package com.payflow.payment.security;

import com.payflow.common.util.HashUtil;
import com.payflow.payment.domain.entity.MerchantEntity;
import com.payflow.payment.repository.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    private final MerchantRepository merchantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // Public endpoints bypass
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/api/simulator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String keyHash = HashUtil.sha256Hex(apiKey);
        Optional<MerchantEntity> merchantOpt = merchantRepository.findByApiKeyHash(keyHash);

        if (merchantOpt.isPresent()) {
            MerchantEntity merchant = merchantOpt.get();
            MerchantPrincipal principal = MerchantPrincipal.builder()
                    .merchantId(merchant.getId())
                    .name(merchant.getName())
                    .rateLimitPerMinute(merchant.getRateLimitPerMinute())
                    .status(merchant.getStatus())
                    .build();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated merchant [{}] for request [{}]", merchant.getId(), path);
        } else {
            log.warn("Invalid API key provided for request [{}]", path);
        }

        filterChain.doFilter(request, response);
    }
}
