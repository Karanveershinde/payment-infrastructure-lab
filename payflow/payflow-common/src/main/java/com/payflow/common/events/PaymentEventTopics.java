package com.payflow.common.events;

public final class PaymentEventTopics {

    public static final String PAYMENT_CREATED = "payment.created";
    public static final String PAYMENT_PROCESSING = "payment.processing";
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String PAYMENT_CAPTURED = "payment.captured";
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";
    public static final String PAYMENT_REFUND_REQUESTED = "payment.refund_requested";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String PAYMENT_WEBHOOK_RECEIVED = "payment.webhook.received";

    public static final String NOTIFICATIONS_TOPIC = "payment.notifications";
    public static final String DEAD_LETTER_QUEUE = "payment.events.dlq";

    private PaymentEventTopics() {}
}
