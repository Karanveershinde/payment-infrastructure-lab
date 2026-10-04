package com.payflow.common.domain.statemachine;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateMachineTest {

    @ParameterizedTest(name = "Allowed transition: {0} -> {1}")
    @CsvSource({
            "CREATED, PROCESSING",
            "CREATED, CANCELLED",
            "PROCESSING, AUTHORIZED",
            "PROCESSING, CAPTURED",
            "PROCESSING, FAILED",
            "AUTHORIZED, CAPTURED",
            "AUTHORIZED, CANCELLED",
            "AUTHORIZED, FAILED",
            "CAPTURED, REFUND_PENDING",
            "REFUND_PENDING, REFUNDED",
            "REFUND_PENDING, CAPTURED"
    })
    @DisplayName("Should allow legal payment state transitions")
    void shouldAllowLegalTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(PaymentStateMachine.isTransitionAllowed(from, to)).isTrue();
        assertThatCode(() -> PaymentStateMachine.validateTransition(from, to))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "Forbidden transition: {0} -> {1}")
    @CsvSource({
            "CREATED, CAPTURED",
            "CREATED, REFUNDED",
            "PROCESSING, CREATED",
            "PROCESSING, REFUNDED",
            "AUTHORIZED, CREATED",
            "AUTHORIZED, PROCESSING",
            "CAPTURED, CREATED",
            "CAPTURED, PROCESSING",
            "CAPTURED, AUTHORIZED",
            "CAPTURED, FAILED",
            "CAPTURED, CANCELLED",
            "FAILED, CREATED",
            "FAILED, PROCESSING",
            "FAILED, AUTHORIZED",
            "FAILED, CAPTURED",
            "CANCELLED, CREATED",
            "CANCELLED, PROCESSING",
            "REFUNDED, CREATED",
            "REFUNDED, CAPTURED",
            "REFUNDED, REFUND_PENDING"
    })
    @DisplayName("Should reject illegal payment state transitions with exception")
    void shouldRejectIllegalTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(PaymentStateMachine.isTransitionAllowed(from, to)).isFalse();
        assertThatThrownBy(() -> PaymentStateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Illegal state transition attempted");
    }

    @Test
    @DisplayName("Idempotent self-transitions should be permitted as no-ops")
    void shouldAllowSelfTransitions() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertThat(PaymentStateMachine.isTransitionAllowed(status, status)).isTrue();
            assertThatCode(() -> PaymentStateMachine.validateTransition(status, status))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Terminal states should have no reachable next states")
    void shouldIdentifyTerminalStates() {
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.CANCELLED)).isTrue();
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.REFUNDED)).isTrue();

        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.CREATED)).isFalse();
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.PROCESSING)).isFalse();
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.AUTHORIZED)).isFalse();
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.CAPTURED)).isFalse();

        assertThat(PaymentStateMachine.getAllowedNextStates(PaymentStatus.FAILED)).isEmpty();
        assertThat(PaymentStateMachine.getAllowedNextStates(PaymentStatus.CANCELLED)).isEmpty();
        assertThat(PaymentStateMachine.getAllowedNextStates(PaymentStatus.REFUNDED)).isEmpty();
    }

    @Test
    @DisplayName("Null state checks should fail safely")
    void shouldHandleNullStatesSafely() {
        assertThat(PaymentStateMachine.isTransitionAllowed(null, PaymentStatus.CAPTURED)).isFalse();
        assertThat(PaymentStateMachine.isTransitionAllowed(PaymentStatus.CREATED, null)).isFalse();
        assertThat(PaymentStateMachine.getAllowedNextStates(null)).isEmpty();
    }
}
