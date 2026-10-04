package com.payflow.payment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.dto.response.ApiErrorResponse;
import com.payflow.common.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/api/simulator")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MerchantPrincipal merchant) {
            try {
                RateLimiterService.RateLimitResult result = rateLimiterService.checkRateLimit(
                        merchant.getMerchantId(),
                        merchant.getRateLimitPerMinute()
                );

                // Add standard rate limit headers
                response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
                response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetEpochSeconds()));

            } catch (RateLimitExceededException e) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));

                ApiErrorResponse error = ApiErrorResponse.builder()
                        .code("RATE_LIMIT_EXCEEDED")
                        .message(e.getMessage())
                        .requestId((String) request.getAttribute("requestId"))
                        .timestamp(Instant.now())
                        .details(Map.of("retryAfterSeconds", e.getRetryAfterSeconds(), "limitPerMinute", merchant.getRateLimitPerMinute()))
                        .build();

                response.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
