package com.payflow.payment.provider;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.exception.ProviderUnavailableException;
import com.payflow.payment.domain.entity.PaymentAttemptEntity;
import com.payflow.payment.observability.MetricsService;
import com.payflow.payment.repository.PaymentAttemptRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Intelligent Payment Provider Router & Resilience Orchestrator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderRouter {

    private final PaymentProviderClient providerClient;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MetricsService metricsService;

    private static final int MAX_RETRIES_PER_PROVIDER = 2;
    private static final long INITIAL_BACKOFF_MS = 100;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    @Data
    @Builder
    public static class RoutingResult {
        private ProviderType provider;
        private ProviderModels.ProviderResponse response;
        private List<PaymentAttemptEntity> attempts;
    }

    public RoutingResult routeAuthorize(String paymentId, ProviderType preferredProvider, ProviderModels.AuthorizeRequest request) {
        List<ProviderType> providerSequence = buildRoutingSequence(preferredProvider);
        List<PaymentAttemptEntity> attemptLog = new ArrayList<>();
        int globalAttemptNumber = 1;

        for (ProviderType provider : providerSequence) {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(provider.name().toLowerCase());

            // Check if circuit breaker allows call
            if (!circuitBreaker.tryAcquirePermission()) {
                log.warn("Circuit Breaker is OPEN for provider [{}]. Failing over to next provider.", provider);
                metricsService.incrementCircuitBreakerOpen(provider.name());
                continue;
            }

            // Attempt execution on this provider with exponential backoff & jitter
            for (int retry = 0; retry <= MAX_RETRIES_PER_PROVIDER; retry++) {
                if (retry > 0) {
                    metricsService.incrementRetry(provider.name(), retry);
                    applyExponentialBackoffWithJitter(retry);
                }

                long attemptStartTime = System.currentTimeMillis();
                try {
                    log.info("Dispatching authorization attempt #{} on provider [{}] for payment [{}]",
                            globalAttemptNumber, provider, paymentId);

                    ProviderModels.ProviderResponse response = circuitBreaker.executeSupplier(
                            () -> providerClient.authorize(provider, request)
                    );

                    long latency = System.currentTimeMillis() - attemptStartTime;

                    PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .paymentId(paymentId)
                            .provider(provider)
                            .attemptNumber(globalAttemptNumber++)
                            .status(response.getStatus())
                            .errorCode(response.getErrorCode())
                            .errorMessage(response.getErrorMessage())
                            .latencyMs(latency)
                            .build();

                    attemptLog.add(attempt);
                    paymentAttemptRepository.save(attempt);

                    // Permanent business declines are final
                    if (!response.isSuccess() && isPermanentFailure(response.getErrorCode())) {
                        log.info("Payment [{}] rejected with permanent failure [{}] on [{}]. No failover.",
                                paymentId, response.getErrorCode(), provider);
                        return RoutingResult.builder()
                                .provider(provider)
                                .response(response)
                                .attempts(attemptLog)
                                .build();
                    }

                    if (response.isSuccess()) {
                        log.info("Payment [{}] authorized successfully via [{}] in {}ms", paymentId, provider, latency);
                        return RoutingResult.builder()
                                .provider(provider)
                                .response(response)
                                .attempts(attemptLog)
                                .build();
                    }

                } catch (CallNotPermittedException e) {
                    log.warn("Resilience4j Circuit Breaker call not permitted for [{}]: {}", provider, e.getMessage());
                    metricsService.incrementCircuitBreakerOpen(provider.name());
                    break;
                } catch (ProviderUnavailableException e) {
                    long latency = System.currentTimeMillis() - attemptStartTime;
                    log.warn("Provider [{}] transient failure on attempt #{}: {}", provider, retry + 1, e.getMessage());

                    PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .paymentId(paymentId)
                            .provider(provider)
                            .attemptNumber(globalAttemptNumber++)
                            .status(PaymentStatus.FAILED)
                            .errorCode("PROVIDER_UNAVAILABLE")
                            .errorMessage(e.getMessage())
                            .latencyMs(latency)
                            .build();
                    attemptLog.add(attempt);
                    paymentAttemptRepository.save(attempt);

                    if (retry == MAX_RETRIES_PER_PROVIDER) {
                        log.warn("Exhausted retries for provider [{}]. Failing over.", provider);
                    }
                } catch (Exception e) {
                    long latency = System.currentTimeMillis() - attemptStartTime;
                    log.error("Unexpected error on provider [{}] attempt #{}: {}", provider, retry + 1, e.getMessage());
                    break;
                }
            }
        }

        // All providers exhausted
        log.error("All payment providers exhausted for payment [{}]", paymentId);
        throw new ProviderUnavailableException("ALL_PROVIDERS", "All payment providers failed or circuit breakers are open");
    }

    public ProviderModels.ProviderResponse routeCapture(ProviderType provider, ProviderModels.CaptureRequest request) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(provider.name().toLowerCase());
        return circuitBreaker.executeSupplier(() -> providerClient.capture(provider, request));
    }

    public ProviderModels.ProviderResponse routeRefund(ProviderType provider, ProviderModels.RefundRequest request) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(provider.name().toLowerCase());
        return circuitBreaker.executeSupplier(() -> providerClient.refund(provider, request));
    }

    private List<ProviderType> buildRoutingSequence(ProviderType preferred) {
        if (preferred != null) {
            List<ProviderType> seq = new ArrayList<>();
            seq.add(preferred);
            for (ProviderType p : ProviderType.values()) {
                if (p != preferred) seq.add(p);
            }
            return seq;
        }
        return Arrays.asList(ProviderType.PROVIDER_A, ProviderType.PROVIDER_B, ProviderType.PROVIDER_C);
    }

    private boolean isPermanentFailure(String errorCode) {
        if (errorCode == null) return false;
        return switch (errorCode.toUpperCase()) {
            case "CARD_DECLINED", "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "DO_NOT_HONOR", "INVALID_CARD_NUMBER" -> true;
            default -> false;
        };
    }

    private void applyExponentialBackoffWithJitter(int attempt) {
        long baseBackoff = (long) (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
        long sleepTime = ThreadLocalRandom.current().nextLong(0, baseBackoff + 1);
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
