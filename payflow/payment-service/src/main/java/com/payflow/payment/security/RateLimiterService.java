package com.payflow.payment.security;

import com.payflow.common.exception.RateLimitExceededException;
import com.payflow.payment.observability.MetricsService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Merchant-level API rate limiter using Redis atomic sliding counters
 * with in-memory resilient fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final MetricsService metricsService;
    private final Map<String, LocalWindow> localFallbackCache = new ConcurrentHashMap<>();

    @Data
    @Builder
    public static class RateLimitResult {
        private boolean allowed;
        private int limit;
        private long remaining;
        private long resetEpochSeconds;
    }

    private static class LocalWindow {
        private final long windowMinute;
        private final AtomicInteger counter = new AtomicInteger(0);

        public LocalWindow(long windowMinute) {
            this.windowMinute = windowMinute;
        }
    }

    /**
     * Checks if the merchant is within their rate limit. Throws RateLimitExceededException if exceeded.
     */
    public RateLimitResult checkRateLimit(String merchantId, int maxRequestsPerMinute) {
        long currentEpochSecond = Instant.now().getEpochSecond();
        long currentWindowMinute = currentEpochSecond / 60;
        long resetEpochSeconds = (currentWindowMinute + 1) * 60;
        long retryAfterSeconds = Math.max(1, resetEpochSeconds - currentEpochSecond);

        String redisKey = String.format("ratelimit:%s:%d", merchantId, currentWindowMinute);

        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(120));
            }

            long currentCount = (count != null) ? count : 1;
            long remaining = Math.max(0, maxRequestsPerMinute - currentCount);

            if (currentCount > maxRequestsPerMinute) {
                log.warn("Rate limit exceeded for merchant [{}]: {}/{} reqs/min", merchantId, currentCount, maxRequestsPerMinute);
                metricsService.incrementRateLimitRejected(merchantId);
                throw new RateLimitExceededException(merchantId, retryAfterSeconds);
            }

            return RateLimitResult.builder()
                    .allowed(true)
                    .limit(maxRequestsPerMinute)
                    .remaining(remaining)
                    .resetEpochSeconds(resetEpochSeconds)
                    .build();

        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis rate limiter unavailable. Falling back to local in-memory counter: {}", e.getMessage());
            return checkLocalFallback(merchantId, maxRequestsPerMinute, currentWindowMinute, resetEpochSeconds, retryAfterSeconds);
        }
    }

    private RateLimitResult checkLocalFallback(String merchantId, int maxRequestsPerMinute, long windowMinute, long resetEpochSeconds, long retryAfterSeconds) {
        LocalWindow window = localFallbackCache.compute(merchantId, (k, v) -> {
            if (v == null || v.windowMinute != windowMinute) {
                return new LocalWindow(windowMinute);
            }
            return v;
        });

        int currentCount = window.counter.incrementAndGet();
        long remaining = Math.max(0, maxRequestsPerMinute - currentCount);

        if (currentCount > maxRequestsPerMinute) {
            metricsService.incrementRateLimitRejected(merchantId);
            throw new RateLimitExceededException(merchantId, retryAfterSeconds);
        }

        return RateLimitResult.builder()
                .allowed(true)
                .limit(maxRequestsPerMinute)
                .remaining(remaining)
                .resetEpochSeconds(resetEpochSeconds)
                .build();
    }
}
