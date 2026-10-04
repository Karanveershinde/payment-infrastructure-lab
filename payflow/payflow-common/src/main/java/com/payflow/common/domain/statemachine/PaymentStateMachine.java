package com.payflow.common.domain.statemachine;

import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.exception.InvalidStateTransitionException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Explicit Payment State Machine Engine.
 *
 * Enforces strictly defined, deterministic lifecycle transitions for payment transactions.
 * Prevents invalid state mutations such as double-capture, post-failure processing,
 * or out-of-order terminal state corruptions.
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITION_MATRIX;

    static {
        Map<PaymentStatus, Set<PaymentStatus>> matrix = new EnumMap<>(PaymentStatus.class);

        // CREATED -> Can advance to PROCESSING when dispatched, or CANCELLED if aborted early
        matrix.put(PaymentStatus.CREATED, EnumSet.of(
                PaymentStatus.PROCESSING,
                PaymentStatus.CANCELLED
        ));

        // PROCESSING -> Can advance to AUTHORIZED (two-step flow), CAPTURED (direct charge), or FAILED
        matrix.put(PaymentStatus.PROCESSING, EnumSet.of(
                PaymentStatus.AUTHORIZED,
                PaymentStatus.CAPTURED,
                PaymentStatus.FAILED
        ));

        // AUTHORIZED -> Can advance to CAPTURED (settlement), CANCELLED (void auth), or FAILED (auth expiration/error)
        matrix.put(PaymentStatus.AUTHORIZED, EnumSet.of(
                PaymentStatus.CAPTURED,
                PaymentStatus.CANCELLED,
                PaymentStatus.FAILED
        ));

        // CAPTURED -> Can only transition to REFUND_PENDING when merchant initiates refund
        matrix.put(PaymentStatus.CAPTURED, EnumSet.of(
                PaymentStatus.REFUND_PENDING
        ));

        // REFUND_PENDING -> Can advance to REFUNDED (success) or revert to CAPTURED (refund rejected/failed)
        matrix.put(PaymentStatus.REFUND_PENDING, EnumSet.of(
                PaymentStatus.REFUNDED,
                PaymentStatus.CAPTURED
        ));

        // Terminal States - No outgoing transitions permitted
        matrix.put(PaymentStatus.FAILED, Collections.emptySet());
        matrix.put(PaymentStatus.CANCELLED, Collections.emptySet());
        matrix.put(PaymentStatus.REFUNDED, Collections.emptySet());

        TRANSITION_MATRIX = Collections.unmodifiableMap(matrix);
    }

    private PaymentStateMachine() {
        // Prevent instantiation
    }

    /**
     * Checks whether a state transition from fromStatus to toStatus is legally permitted.
     *
     * @param fromStatus The current status of the payment
     * @param toStatus   The target status being attempted
     * @return true if allowed, false otherwise
     */
    public static boolean isTransitionAllowed(PaymentStatus fromStatus, PaymentStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        if (fromStatus == toStatus) {
            // Idempotent self-transition is considered no-op
            return true;
        }
        Set<PaymentStatus> allowedNext = TRANSITION_MATRIX.getOrDefault(fromStatus, Collections.emptySet());
        return allowedNext.contains(toStatus);
    }

    /**
     * Validates that the attempted state transition is legal.
     *
     * @param fromStatus The current status of the payment
     * @param toStatus   The target status
     * @throws InvalidStateTransitionException if the transition violates state machine rules
     */
    public static void validateTransition(PaymentStatus fromStatus, PaymentStatus toStatus) {
        if (fromStatus == toStatus) {
            return; // Idempotent no-op
        }
        if (!isTransitionAllowed(fromStatus, toStatus)) {
            throw new InvalidStateTransitionException(fromStatus, toStatus);
        }
    }

    /**
     * Returns an unmodifiable set of allowed next states for the given state.
     *
     * @param currentStatus Current payment status
     * @return Set of reachable states
     */
    public static Set<PaymentStatus> getAllowedNextStates(PaymentStatus currentStatus) {
        if (currentStatus == null) {
            return Collections.emptySet();
        }
        return TRANSITION_MATRIX.getOrDefault(currentStatus, Collections.emptySet());
    }

    /**
     * Checks if the given status is a terminal state (no further transitions permitted).
     *
     * @param status Payment status
     * @return true if terminal, false otherwise
     */
    public static boolean isTerminal(PaymentStatus status) {
        return status != null && status.isTerminal();
    }
}
