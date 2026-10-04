package com.payflow.payment.service;

import com.payflow.common.domain.enums.Currency;
import com.payflow.common.domain.enums.PaymentMethodType;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.domain.enums.StateTransitionTrigger;
import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.common.dto.request.CapturePaymentRequest;
import com.payflow.common.dto.request.CreatePaymentRequest;
import com.payflow.common.dto.request.RefundPaymentRequest;
import com.payflow.common.dto.response.CaptureResponse;
import com.payflow.common.dto.response.PaymentResponse;
import com.payflow.common.dto.response.RefundResponse;
import com.payflow.payment.domain.entity.PaymentEntity;
import com.payflow.payment.domain.entity.RefundEntity;
import com.payflow.payment.observability.MetricsService;
import com.payflow.payment.provider.ProviderRouter;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.PaymentStateTransitionRepository;
import com.payflow.payment.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStateTransitionRepository transitionRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private StateMachineService stateMachineService;

    @Mock
    private ProviderRouter providerRouter;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private MetricsService metricsService;

    private PaymentService paymentService;

    private static final String MERCHANT_ID = "mer_acme_001";
    private static final String IDEMP_KEY = "idem_key_payment_1";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                transitionRepository,
                refundRepository,
                stateMachineService,
                providerRouter,
                idempotencyService,
                metricsService
        );
    }

    @Test
    @DisplayName("Should execute end-to-end payment creation and auto-capture")
    void shouldCreateAndAutoCapturePayment() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .amount(BigDecimal.valueOf(150.00))
                .currency(Currency.USD)
                .paymentMethodType(PaymentMethodType.CARD_TOKEN)
                .paymentMethodToken("tok_visa_success")
                .captureMethod("AUTOMATIC")
                .customerEmail("customer@example.com")
                .build();

        when(idempotencyService.checkAndReserve(eq(MERCHANT_ID), eq(IDEMP_KEY), anyString()))
                .thenReturn(IdempotencyService.IdempotencyRecord.builder().replay(false).build());

        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(stateMachineService.transitionState(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    PaymentEntity p = inv.getArgument(0);
                    p.setStatus(inv.getArgument(1));
                    return p;
                });

        ProviderModels.ProviderResponse providerResp = ProviderModels.ProviderResponse.builder()
                .success(true)
                .provider("PROVIDER_A")
                .providerTransactionId("txn_provider_a_12345")
                .status(PaymentStatus.AUTHORIZED)
                .build();

        ProviderRouter.RoutingResult routingResult = ProviderRouter.RoutingResult.builder()
                .provider(ProviderType.PROVIDER_A)
                .response(providerResp)
                .attempts(List.of())
                .build();

        when(providerRouter.routeAuthorize(anyString(), any(), any())).thenReturn(routingResult);

        PaymentResponse response = paymentService.createPayment(MERCHANT_ID, IDEMP_KEY, request, "{}", "corr_123");

        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(response.getProvider()).isEqualTo(ProviderType.PROVIDER_A);
        assertThat(response.getProviderTransactionId()).isEqualTo("txn_provider_a_12345");

        verify(idempotencyService).complete(eq(MERCHANT_ID), eq(IDEMP_KEY), anyString(), eq(201));
    }

    @Test
    @DisplayName("Should successfully capture an authorized payment")
    void shouldCaptureAuthorizedPayment() {
        PaymentEntity payment = PaymentEntity.builder()
                .id("pay_12345")
                .merchantId(MERCHANT_ID)
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .status(PaymentStatus.AUTHORIZED)
                .provider(ProviderType.PROVIDER_A)
                .providerTransactionId("txn_123")
                .build();

        when(paymentRepository.findByIdAndMerchantId("pay_12345", MERCHANT_ID)).thenReturn(Optional.of(payment));

        ProviderModels.ProviderResponse captureResp = ProviderModels.ProviderResponse.builder()
                .success(true)
                .status(PaymentStatus.CAPTURED)
                .build();
        when(providerRouter.routeCapture(eq(ProviderType.PROVIDER_A), any())).thenReturn(captureResp);

        when(stateMachineService.transitionState(any(), eq(PaymentStatus.CAPTURED), eq(StateTransitionTrigger.PROVIDER_CAPTURE_SUCCESS), any(), any()))
                .thenAnswer(inv -> {
                    payment.setStatus(PaymentStatus.CAPTURED);
                    return payment;
                });

        CaptureResponse response = paymentService.capturePayment(MERCHANT_ID, "pay_12345", new CapturePaymentRequest(BigDecimal.valueOf(100.00), "capture"), "idemp_cap", "corr_1");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(response.getCapturedAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }

    @Test
    @DisplayName("Should successfully refund a captured payment")
    void shouldRefundCapturedPayment() {
        PaymentEntity payment = PaymentEntity.builder()
                .id("pay_refund_123")
                .merchantId(MERCHANT_ID)
                .amount(BigDecimal.valueOf(100.00))
                .refundedAmount(BigDecimal.ZERO)
                .currency(Currency.USD)
                .status(PaymentStatus.CAPTURED)
                .provider(ProviderType.PROVIDER_A)
                .providerTransactionId("txn_123")
                .build();

        when(paymentRepository.findByIdAndMerchantId("pay_refund_123", MERCHANT_ID)).thenReturn(Optional.of(payment));

        ProviderModels.ProviderResponse refundResp = ProviderModels.ProviderResponse.builder()
                .success(true)
                .providerTransactionId("ref_txn_999")
                .status(PaymentStatus.REFUNDED)
                .build();
        when(providerRouter.routeRefund(eq(ProviderType.PROVIDER_A), any())).thenReturn(refundResp);

        when(stateMachineService.transitionState(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    payment.setStatus(inv.getArgument(1));
                    return payment;
                });

        RefundPaymentRequest refundReq = new RefundPaymentRequest(BigDecimal.valueOf(50.00), "Customer return");
        RefundResponse response = paymentService.refundPayment(MERCHANT_ID, "pay_refund_123", refundReq, "idemp_ref", "corr_ref");

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(refundRepository).save(any(RefundEntity.class));
    }
}
