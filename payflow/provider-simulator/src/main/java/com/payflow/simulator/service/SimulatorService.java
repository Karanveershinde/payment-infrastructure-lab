package com.payflow.simulator.service;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.dto.request.WebhookPayload;
import com.payflow.common.util.HmacSignatureUtil;
import com.payflow.common.util.JsonUtil;
import com.payflow.simulator.config.SimulatorProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorService {

    private final SimulatorProperties properties;
    private final Map<String, SimulatorProperties.ProviderConfig> activeConfig = new ConcurrentHashMap<>();
    private final Map<String, ProviderModels.ProviderResponse> transactionLedger = new ConcurrentHashMap<>();
    private final ExecutorService webhookExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private RestClient restClient;

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder().build();

        // Default SLAs
        SimulatorProperties.ProviderConfig cfgA = new SimulatorProperties.ProviderConfig();
        cfgA.setSuccessRate(0.95);
        cfgA.setMinLatencyMs(50);
        cfgA.setMaxLatencyMs(90);
        activeConfig.put("PROVIDER_A", cfgA);
        activeConfig.put("providera", cfgA);

        SimulatorProperties.ProviderConfig cfgB = new SimulatorProperties.ProviderConfig();
        cfgB.setSuccessRate(0.80);
        cfgB.setMinLatencyMs(150);
        cfgB.setMaxLatencyMs(250);
        activeConfig.put("PROVIDER_B", cfgB);
        activeConfig.put("providerb", cfgB);

        SimulatorProperties.ProviderConfig cfgC = new SimulatorProperties.ProviderConfig();
        cfgC.setSuccessRate(0.60);
        cfgC.setMinLatencyMs(300);
        cfgC.setMaxLatencyMs(500);
        activeConfig.put("PROVIDER_C", cfgC);
        activeConfig.put("providerc", cfgC);
    }

    public ProviderModels.ProviderResponse authorize(String providerName, ProviderModels.AuthorizeRequest request) {
        String normalizedProvider = normalizeProvider(providerName);
        SimulatorProperties.ProviderConfig config = activeConfig.getOrDefault(normalizedProvider, new SimulatorProperties.ProviderConfig());

        long startTime = System.currentTimeMillis();
        simulateLatency(config);

        if (config.isForceTimeout()) {
            log.warn("Simulator: Provider [{}] simulating TIMEOUT", normalizedProvider);
            sleep(5000);
            throw new RuntimeException("HTTP 504 Gateway Timeout: Provider " + normalizedProvider + " read timeout");
        }

        if (config.isForceOutage() || config.isForce500Error()) {
            log.warn("Simulator: Provider [{}] simulating 503 SERVICE UNAVAILABLE", normalizedProvider);
            throw new RuntimeException("HTTP 503 Service Unavailable: Provider " + normalizedProvider + " temporary outage");
        }

        String token = request.getPaymentMethodToken();
        if ("tok_declined".equalsIgnoreCase(token) || "tok_insufficient_funds".equalsIgnoreCase(token)) {
            return recordAndRespond(normalizedProvider, false, PaymentStatus.FAILED, "CARD_DECLINED", "Card was declined by issuing bank", startTime);
        }
        if ("tok_expired".equalsIgnoreCase(token)) {
            return recordAndRespond(normalizedProvider, false, PaymentStatus.FAILED, "CARD_EXPIRED", "Card has expired", startTime);
        }
        if ("tok_force_500".equalsIgnoreCase(token)) {
            throw new RuntimeException("HTTP 500 Internal Provider Server Error");
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        boolean isSuccess = roll < config.getSuccessRate();

        if (!isSuccess) {
            String errorCode = config.getForcedErrorCode() != null ? config.getForcedErrorCode() : "TRANSACTION_DECLINED";
            return recordAndRespond(normalizedProvider, false, PaymentStatus.FAILED, errorCode, "Transaction declined by simulated provider", startTime);
        }

        ProviderModels.ProviderResponse response = recordAndRespond(
                normalizedProvider,
                true,
                PaymentStatus.AUTHORIZED,
                null,
                null,
                startTime
        );

        if (properties.isAsyncWebhooksEnabled()) {
            dispatchAsyncWebhook(
                    normalizedProvider,
                    response.getProviderTransactionId(),
                    request.getPaymentId(),
                    "payment.auth.success",
                    PaymentStatus.AUTHORIZED,
                    request.getAmount(),
                    request.getCurrency(),
                    null,
                    null
            );
        }

        return response;
    }

    public ProviderModels.ProviderResponse capture(String providerName, ProviderModels.CaptureRequest request) {
        String normalizedProvider = normalizeProvider(providerName);
        SimulatorProperties.ProviderConfig config = activeConfig.getOrDefault(normalizedProvider, new SimulatorProperties.ProviderConfig());

        long startTime = System.currentTimeMillis();
        simulateLatency(config);

        if (config.isForceOutage() || config.isForce500Error()) {
            throw new RuntimeException("HTTP 503 Service Unavailable: Provider " + normalizedProvider + " capture failed");
        }

        ProviderModels.ProviderResponse response = recordAndRespond(
                normalizedProvider,
                true,
                PaymentStatus.CAPTURED,
                null,
                null,
                startTime
        );

        if (properties.isAsyncWebhooksEnabled()) {
            dispatchAsyncWebhook(
                    normalizedProvider,
                    request.getProviderTransactionId(),
                    request.getPaymentId(),
                    "payment.capture.success",
                    PaymentStatus.CAPTURED,
                    request.getAmount(),
                    request.getCurrency(),
                    null,
                    null
            );
        }

        return response;
    }

    public ProviderModels.ProviderResponse refund(String providerName, ProviderModels.RefundRequest request) {
        String normalizedProvider = normalizeProvider(providerName);
        SimulatorProperties.ProviderConfig config = activeConfig.getOrDefault(normalizedProvider, new SimulatorProperties.ProviderConfig());

        long startTime = System.currentTimeMillis();
        simulateLatency(config);

        return recordAndRespond(
                normalizedProvider,
                true,
                PaymentStatus.REFUNDED,
                null,
                null,
                startTime
        );
    }

    public ProviderModels.ProviderResponse getStatus(String providerName, String providerTxnId) {
        return transactionLedger.get(providerTxnId);
    }

    public void updateConfig(ProviderModels.FailureInjectionRequest request) {
        String targetProvider = request.getProvider();
        if (targetProvider == null || targetProvider.isBlank() || "ALL".equalsIgnoreCase(targetProvider)) {
            activeConfig.keySet().forEach(k -> applyConfig(activeConfig.get(k), request));
        } else {
            String norm = normalizeProvider(targetProvider);
            SimulatorProperties.ProviderConfig cfg = activeConfig.computeIfAbsent(norm, k -> new SimulatorProperties.ProviderConfig());
            applyConfig(cfg, request);
        }
    }

    public Map<String, SimulatorProperties.ProviderConfig> getConfig() {
        return activeConfig;
    }

    private void applyConfig(SimulatorProperties.ProviderConfig config, ProviderModels.FailureInjectionRequest request) {
        if (request.getSuccessRate() != null) config.setSuccessRate(request.getSuccessRate());
        if (request.getMinLatencyMs() != null) config.setMinLatencyMs(request.getMinLatencyMs());
        if (request.getMaxLatencyMs() != null) config.setMaxLatencyMs(request.getMaxLatencyMs());
        if (request.getForceOutage() != null) config.setForceOutage(request.getForceOutage());
        if (request.getForceTimeout() != null) config.setForceTimeout(request.getForceTimeout());
        if (request.getForce500Error() != null) config.setForce500Error(request.getForce500Error());
        if (request.getForcedErrorCode() != null) config.setForcedErrorCode(request.getForcedErrorCode());
    }

    private ProviderModels.ProviderResponse recordAndRespond(
            String provider,
            boolean success,
            PaymentStatus status,
            String errorCode,
            String errorMessage,
            long startTime
    ) {
        String providerTxnId = "txn_" + provider.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        long latency = System.currentTimeMillis() - startTime;

        ProviderModels.ProviderResponse response = ProviderModels.ProviderResponse.builder()
                .success(success)
                .provider(provider)
                .providerTransactionId(providerTxnId)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .processingLatencyMs(latency)
                .timestamp(Instant.now())
                .build();

        transactionLedger.put(providerTxnId, response);
        return response;
    }

    public void dispatchAsyncWebhook(
            String provider,
            String providerTxnId,
            String paymentId,
            String eventType,
            PaymentStatus status,
            BigDecimal amount,
            Currency currency,
            String errorCode,
            String errorMessage
    ) {
        webhookExecutor.submit(() -> {
            try {
                Thread.sleep(properties.getWebhookDelayMs());
                ProviderType providerType;
                try {
                    providerType = ProviderType.valueOf(provider.toUpperCase());
                } catch (Exception e) {
                    providerType = ProviderType.PROVIDER_A;
                }

                WebhookPayload payload = WebhookPayload.builder()
                        .provider(providerType)
                        .providerEventId("evt_" + UUID.randomUUID().toString().substring(0, 8))
                        .eventType(eventType)
                        .providerTransactionId(providerTxnId)
                        .paymentId(paymentId)
                        .status(status)
                        .amount(amount)
                        .currency(currency)
                        .errorCode(errorCode)
                        .errorMessage(errorMessage)
                        .timestamp(Instant.now())
                        .build();

                String jsonPayload = JsonUtil.toJson(payload);
                String signature = HmacSignatureUtil.calculateSignature(properties.getWebhookSecret(), jsonPayload);

                String endpoint = properties.getWebhookServiceUrl() + "/" + provider.toLowerCase();
                log.info("Simulator: Dispatching HMAC webhook to [{}] for payment [{}]", endpoint, paymentId);

                restClient.post()
                        .uri(endpoint)
                        .header("X-PayFlow-Signature", signature)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonPayload)
                        .retrieve()
                        .toBodilessEntity();

            } catch (Exception e) {
                log.debug("Simulator: Webhook delivery attempt completed (receiver may be offline during unit test): {}", e.getMessage());
            }
        });
    }

    private void simulateLatency(SimulatorProperties.ProviderConfig config) {
        int min = Math.max(1, config.getMinLatencyMs());
        int max = Math.max(min, config.getMaxLatencyMs());
        int delay = ThreadLocalRandom.current().nextInt(min, max + 1);
        sleep(delay);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalizeProvider(String provider) {
        if (provider == null) return "PROVIDER_A";
        return provider.toUpperCase().replace("-", "_");
    }
}
