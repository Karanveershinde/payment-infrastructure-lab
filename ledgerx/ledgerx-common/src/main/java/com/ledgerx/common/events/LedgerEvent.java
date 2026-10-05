package com.ledgerx.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard immutable event envelope for all LedgerX Kafka events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEvent<T> {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private String eventType;
    private String aggregateId;

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp = Instant.now();

    @Builder.Default
    private String schemaVersion = "1.0";

    @Builder.Default
    private String producer = "ledgerx-system";

    private T payload;
}
