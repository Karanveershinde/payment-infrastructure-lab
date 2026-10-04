package com.payflow.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.payflow.common.domain.enums.PaymentStatus;
import com.payflow.common.domain.enums.StateTransitionTrigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventResponse {

    private String transitionId;
    private String paymentId;
    private PaymentStatus fromState;
    private PaymentStatus toState;
    private StateTransitionTrigger trigger;
    private String reason;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
