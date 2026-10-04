package com.payflow.payment.provider;

import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.exception.ProviderUnavailableException;
import com.payflow.payment.observability.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Component
public class HttpPaymentProviderClient implements PaymentProviderClient {

    private final RestClient restClient;
    private final MetricsService metricsService;

    public HttpPaymentProviderClient(
            MetricsService metricsService,
            @Value("${simulator.base-url:http://localhost:8081/api/simulator}") String simulatorBaseUrl
    ) {
        this.metricsService = metricsService;

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(1500))
                .withReadTimeout(Duration.ofMillis(3500));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(simulatorBaseUrl)
                .build();
    }

    @Override
    public ProviderModels.ProviderResponse authorize(ProviderType provider, ProviderModels.AuthorizeRequest request) {
        String path = "/" + provider.name().toLowerCase() + "/authorize";
        long startTime = System.currentTimeMillis();

        try {
            ProviderModels.ProviderResponse response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ProviderUnavailableException(provider.name(), "HTTP " + res.getStatusCode() + " server error from provider");
                    })
                    .body(ProviderModels.ProviderResponse.class);

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordProviderLatency(provider.name(), "AUTHORIZE", duration);
            return response;

        } catch (ProviderUnavailableException e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordProviderLatency(provider.name(), "AUTHORIZE", duration);
            metricsService.incrementProviderFailures(provider.name(), "HTTP_5XX");
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordProviderLatency(provider.name(), "AUTHORIZE", duration);
            metricsService.incrementProviderFailures(provider.name(), "NETWORK_OR_TIMEOUT");
            throw new ProviderUnavailableException(provider.name(), "Communication error with provider: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderModels.ProviderResponse capture(ProviderType provider, ProviderModels.CaptureRequest request) {
        String path = "/" + provider.name().toLowerCase() + "/capture";
        long startTime = System.currentTimeMillis();

        try {
            ProviderModels.ProviderResponse response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ProviderUnavailableException(provider.name(), "HTTP " + res.getStatusCode() + " server error from provider capture");
                    })
                    .body(ProviderModels.ProviderResponse.class);

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordProviderLatency(provider.name(), "CAPTURE", duration);
            return response;

        } catch (ProviderUnavailableException e) {
            metricsService.incrementProviderFailures(provider.name(), "CAPTURE_5XX");
            throw e;
        } catch (Exception e) {
            metricsService.incrementProviderFailures(provider.name(), "CAPTURE_TIMEOUT");
            throw new ProviderUnavailableException(provider.name(), "Capture communication error: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderModels.ProviderResponse refund(ProviderType provider, ProviderModels.RefundRequest request) {
        String path = "/" + provider.name().toLowerCase() + "/refund";
        long startTime = System.currentTimeMillis();

        try {
            ProviderModels.ProviderResponse response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ProviderUnavailableException(provider.name(), "HTTP " + res.getStatusCode() + " refund error");
                    })
                    .body(ProviderModels.ProviderResponse.class);

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordProviderLatency(provider.name(), "REFUND", duration);
            return response;

        } catch (Exception e) {
            metricsService.incrementProviderFailures(provider.name(), "REFUND_ERROR");
            throw new ProviderUnavailableException(provider.name(), "Refund communication error: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderModels.ProviderResponse getStatus(ProviderType provider, String providerTxnId) {
        String path = "/" + provider.name().toLowerCase() + "/status/" + providerTxnId;
        try {
            return restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(ProviderModels.ProviderResponse.class);
        } catch (Exception e) {
            throw new ProviderUnavailableException(provider.name(), "Failed to get status for " + providerTxnId, e);
        }
    }
}
