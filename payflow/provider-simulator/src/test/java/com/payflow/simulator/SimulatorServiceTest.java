package com.payflow.simulator;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.simulator.config.SimulatorProperties;
import com.payflow.simulator.service.SimulatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatorServiceTest {

    private SimulatorService simulatorService;
    private SimulatorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SimulatorProperties();
        properties.setAsyncWebhooksEnabled(false);
        simulatorService = new SimulatorService(properties);
        simulatorService.init();
    }

    @Test
    @DisplayName("Should successfully authorize payment on ProviderA")
    void shouldAuthorizePayment() {
        ProviderModels.FailureInjectionRequest cfg = ProviderModels.FailureInjectionRequest.builder()
                .provider("PROVIDER_A")
                .successRate(1.0)
                .minLatencyMs(1)
                .maxLatencyMs(5)
                .build();
        simulatorService.updateConfig(cfg);

        ProviderModels.AuthorizeRequest request = ProviderModels.AuthorizeRequest.builder()
                .paymentId("pay_12345")
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .paymentMethodToken("tok_visa_success")
                .customerEmail("alice@example.com")
                .build();

        ProviderModels.ProviderResponse response = simulatorService.authorize("PROVIDER_A", request);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.getProviderTransactionId()).startsWith("txn_provider_a_");
    }

    @Test
    @DisplayName("Should simulate decline when card token is tok_declined")
    void shouldDeclineCardToken() {
        ProviderModels.AuthorizeRequest request = ProviderModels.AuthorizeRequest.builder()
                .paymentId("pay_declined")
                .amount(BigDecimal.valueOf(50.00))
                .currency(Currency.USD)
                .paymentMethodToken("tok_declined")
                .build();

        ProviderModels.ProviderResponse response = simulatorService.authorize("PROVIDER_A", request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("CARD_DECLINED");
    }

    @Test
    @DisplayName("Should simulate 503 outage when failure injection enabled")
    void shouldSimulateOutage() {
        ProviderModels.FailureInjectionRequest cfg = ProviderModels.FailureInjectionRequest.builder()
                .provider("PROVIDER_A")
                .forceOutage(true)
                .build();
        simulatorService.updateConfig(cfg);

        ProviderModels.AuthorizeRequest request = ProviderModels.AuthorizeRequest.builder()
                .paymentId("pay_outage")
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .paymentMethodToken("tok_visa")
                .build();

        assertThatThrownBy(() -> simulatorService.authorize("PROVIDER_A", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("503 Service Unavailable");
    }
}
