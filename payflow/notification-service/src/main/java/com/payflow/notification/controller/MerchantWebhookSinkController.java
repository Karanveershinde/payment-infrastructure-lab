package com.payflow.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Merchant Notification Sink", description = "Mock Webhook Sink endpoint for merchants receiving asynchronous payment status updates")
public class MerchantWebhookSinkController {

    @PostMapping("/webhook-sink")
    @Operation(summary = "Demo webhook sink receiving merchant notification callbacks")
    public ResponseEntity<Map<String, Object>> receiveMerchantNotification(
            @RequestHeader(value = "X-PayFlow-Signature", required = false) String signature,
            @RequestBody String payload
    ) {
        log.info("Merchant Webhook Sink received notification! Payload: {}", payload);
        return ResponseEntity.ok(Map.of("status", "RECEIVED", "ack", true));
    }
}
