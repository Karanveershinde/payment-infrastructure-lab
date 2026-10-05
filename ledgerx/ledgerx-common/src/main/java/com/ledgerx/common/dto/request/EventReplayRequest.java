package com.ledgerx.common.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventReplayRequest {
    private String eventId;
    private String transactionId;
    private String eventType;
    private Instant fromTimestamp;
    private Instant toTimestamp;
    private String requestedBy;
}
