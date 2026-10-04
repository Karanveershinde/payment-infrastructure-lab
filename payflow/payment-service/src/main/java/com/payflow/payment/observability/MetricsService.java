package com.payflow.payment.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Centralized Metrics Service providing Prometheus instrumentation.
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementPaymentRequests(String merchantId, String method) {
        Counter.builder("payment_requests_total")
                .description("Total number of incoming payment requests")
                .tag("merchant_id", merchantId != null ? merchantId : "unknown")
                .tag("payment_method", method != null ? method : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementPaymentSuccess(String merchantId, String provider) {
        Counter.builder("payment_success_total")
                .description("Total number of successful payments")
                .tag("merchant_id", merchantId != null ? merchantId : "unknown")
                .tag("provider", provider != null ? provider : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementPaymentFailure(String merchantId, String provider, String errorCode) {
        Counter.builder("payment_failure_total")
                .description("Total number of failed payments")
                .tag("merchant_id", merchantId != null ? merchantId : "unknown")
                .tag("provider", provider != null ? provider : "unknown")
                .tag("error_code", errorCode != null ? errorCode : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordPaymentLatency(String merchantId, String status, long durationMs) {
        Timer.builder("payment_latency")
                .description("End-to-end payment processing latency")
                .tag("merchant_id", merchantId != null ? merchantId : "unknown")
                .tag("status", status != null ? status : "unknown")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    public void recordProviderLatency(String provider, String operation, long durationMs) {
        Timer.builder("provider_latency")
                .description("Outbound latency to external payment provider")
                .tag("provider", provider != null ? provider : "unknown")
                .tag("operation", operation != null ? operation : "unknown")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    public void incrementProviderFailures(String provider, String errorType) {
        Counter.builder("provider_failures_total")
                .description("Total number of provider errors")
                .tag("provider", provider != null ? provider : "unknown")
                .tag("error_type", errorType != null ? errorType : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementRetry(String provider, int attempt) {
        Counter.builder("retry_total")
                .description("Total number of provider retry attempts")
                .tag("provider", provider != null ? provider : "unknown")
                .tag("attempt", String.valueOf(attempt))
                .register(meterRegistry)
                .increment();
    }

    public void incrementCircuitBreakerOpen(String provider) {
        Counter.builder("circuit_breaker_open_total")
                .description("Total number of times a provider circuit breaker opened")
                .tag("provider", provider != null ? provider : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementWebhookDuplicates(String provider) {
        Counter.builder("webhook_duplicate_total")
                .description("Total number of duplicate webhooks received and deduplicated")
                .tag("provider", provider != null ? provider : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementIdempotencyConflict(String merchantId) {
        Counter.builder("idempotency_conflict_total")
                .description("Total number of concurrent idempotency conflicts")
                .tag("merchant_id", merchantId != null ? merchantId : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementRateLimitRejected(String merchantId) {
        Counter.builder("rate_limit_rejected_total")
                .description("Total number of requests rejected due to merchant rate limit")
                .tag("merchant_id", merchantId != null ? merchantId : "unknown")
                .register(meterRegistry)
                .increment();
    }
}
