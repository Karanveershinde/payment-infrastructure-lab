package com.payflow.payment.security;

import com.payflow.common.exception.RateLimitExceededException;
import com.payflow.payment.observability.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MetricsService metricsService;

    private RateLimiterService rateLimiterService;

    private static final String MERCHANT_ID = "mer_acme_001";
    private static final int LIMIT = 10;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiterService = new RateLimiterService(redisTemplate, metricsService);
    }

    @Test
    @DisplayName("Should allow requests within rate limit quota")
    void shouldAllowWithinLimit() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        RateLimiterService.RateLimitResult result = rateLimiterService.checkRateLimit(MERCHANT_ID, LIMIT);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getLimit()).isEqualTo(LIMIT);
        assertThat(result.getRemaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should reject and throw RateLimitExceededException when quota exceeded")
    void shouldRejectWhenLimitExceeded() {
        when(valueOperations.increment(anyString())).thenReturn(11L);

        assertThatThrownBy(() -> rateLimiterService.checkRateLimit(MERCHANT_ID, LIMIT))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded for merchant");

        verify(metricsService).incrementRateLimitRejected(MERCHANT_ID);
    }

    @Test
    @DisplayName("Should fall back gracefully to in-memory counter if Redis fails")
    void shouldFallbackToLocalWhenRedisFails() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        RateLimiterService.RateLimitResult result = rateLimiterService.checkRateLimit(MERCHANT_ID, LIMIT);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(9);
    }
}
