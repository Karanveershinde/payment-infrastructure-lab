package com.payflow.payment.provider;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.exception.ProviderUnavailableException;
import com.payflow.payment.config.Resilience4jConfig;
import com.payflow.payment.observability.MetricsService;
import com.payflow.payment.repository.PaymentAttemptRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderRouterTest {

    @Mock
    private PaymentProviderClient providerClient;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private MetricsService metricsService;

    private ProviderRouter providerRouter;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = new Resilience4jConfig().circuitBreakerRegistry();
        providerRouter = new ProviderRouter(providerClient, paymentAttemptRepository, circuitBreakerRegistry, metricsService);
    }

    @Test
    @DisplayName("Should successfully route to primary ProviderA on healthy execution")
    void shouldRouteToPrimary() {
        ProviderModels.AuthorizeRequest request = ProviderModels.AuthorizeRequest.builder()
                .paymentId("pay_123")
                .amount(BigDecimal.valueOf(100.00))
                .build();

        ProviderModels.ProviderResponse responseA = ProviderModels.ProviderResponse.builder()
                .success(true)
                .provider("PROVIDER_A")
                .providerTransactionId("txn_provider_a_123")
                .status(PaymentStatus.AUTHORIZED)
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenReturn(responseA);

        ProviderRouter.RoutingResult result = providerRouter.routeAuthorize("pay_123", null, request);

        assertThat(result.getProvider()).isEqualTo(ProviderType.PROVIDER_A);
        assertThat(result.getResponse().isSuccess()).isTrue();
        assertThat(result.getAttempts()).hasSize(1);
        verify(providerClient, never()).authorize(eq(ProviderType.PROVIDER_B), any());
    }

    @Test
    @DisplayName("Should fail over to ProviderB when ProviderA fails with transient error")
    void shouldFailoverToProviderB() {
        ProviderModels.AuthorizeRequest request = ProviderModels.AuthorizeRequest.builder()
                .paymentId("pay_failover")
                .amount(BigDecimal.valueOf(50.00))
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any()))
                .thenThrow(new ProviderUnavailableException("PROVIDER_A", "503 Service Unavailable"));

        ProviderModels.ProviderResponse responseB = ProviderModels.ProviderResponse.builder()
                .success(true)
                .provider("PROVIDER_B")
                .providerTransactionId("txn_provider_b_456")
                .status(PaymentStatus.AUTHORIZED)
                .build();
        when(providerClient.authorize(eq(ProviderType.PROVIDER_B), any())).thenReturn(responseB);

        ProviderRouter.RoutingResult result = providerRouter.routeAuthorize("pay_failover", null, request);

        assertThat(result.getProvider()).isEqualTo(ProviderType.PROVIDER_B);
        assertThat(result.getResponse().isSuccess()).isTrue();
        assertThat(result.getAttempts()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should abort immediately without failover on permanent business decline")
    void shouldNotFailoverOnPermanentDecline() {
        ProviderModels.AuthorizeRequest request = ProviderModels.AuthorizeRequest.builder()
                .paymentId("pay_decline")
                .amount(BigDecimal.valueOf(50.00))
                .build();

        ProviderModels.ProviderResponse declineResponse = ProviderModels.ProviderResponse.builder()
                .success(false)
                .provider("PROVIDER_A")
                .providerTransactionId("txn_provider_a_999")
                .status(PaymentStatus.FAILED)
                .errorCode("CARD_DECLINED")
                .errorMessage("Card was declined by issuing bank")
                .build();

        when(providerClient.authorize(eq(ProviderType.PROVIDER_A), any())).thenReturn(declineResponse);

        ProviderRouter.RoutingResult result = providerRouter.routeAuthorize("pay_decline", null, request);

        assertThat(result.getProvider()).isEqualTo(ProviderType.PROVIDER_A);
        assertThat(result.getResponse().isSuccess()).isFalse();
        assertThat(result.getResponse().getErrorCode()).isEqualTo("CARD_DECLINED");
        assertThat(result.getAttempts()).hasSize(1);
        verify(providerClient, never()).authorize(eq(ProviderType.PROVIDER_B), any());
    }

    @Test
    @DisplayName("Should throw ProviderUnavailableException when all providers fail")
    void shouldThrowWhenAllProvidersFail() {
        ProviderModels.AuthorizeRequest request = ProviderModels.AuthorizeRequest.builder()
                .paymentId("pay_all_fail")
                .amount(BigDecimal.valueOf(50.00))
                .build();

        when(providerClient.authorize(any(), any()))
                .thenThrow(new ProviderUnavailableException("PROVIDER", "Outage"));

        assertThatThrownBy(() -> providerRouter.routeAuthorize("pay_all_fail", null, request))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("All payment providers failed");
    }
}
