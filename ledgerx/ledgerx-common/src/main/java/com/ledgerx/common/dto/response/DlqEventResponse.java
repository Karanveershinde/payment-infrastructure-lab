package com.ledgerx.common.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqEventResponse {
    private String id;
    private String eventId;
    private String topic;
    private Integer partitionNum;
    private Long offsetNum;
    private String failureReason;
    private int retryCount;
    private String status;
    private String payload;
    private Instant firstFailedAt;
    private Instant lastFailedAt;
    private Instant replayedAt;
}
