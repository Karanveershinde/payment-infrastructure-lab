package com.payflow.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Standardized CloudEvents-compliant Payment Event Envelope.
 *
 * Guaranteed consistent message structure across Kafka event streams.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent<T> {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private String eventType;

    private String aggregateId;

    private String merchantId;

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp = Instant.now();

    @Builder.Default
    private int version = 1;

    private String correlationId;

    private T payload;

    public static <T> PaymentEvent<T> of(String eventType, String aggregateId, String merchantId, String correlationId, T payload) {
        return PaymentEvent.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .aggregateId(aggregateId)
                .merchantId(merchantId)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .version(1)
                .payload(payload)
                .build();
    }
}
