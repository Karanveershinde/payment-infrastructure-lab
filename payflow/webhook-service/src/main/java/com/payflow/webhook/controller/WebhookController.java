package com.payflow.webhook.controller;

import com.payflow.common.dto.response.ApiErrorResponse;
import com.payflow.common.exception.InvalidWebhookSignatureException;
import com.payflow.webhook.service.WebhookProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Provider Webhooks API", description = "Asynchronous callback listener for external payment providers")
public class WebhookController {

    private final WebhookProcessorService webhookProcessorService;

    @PostMapping("/{provider}")
    @Operation(summary = "Receive provider asynchronous webhook notification")
    public ResponseEntity<WebhookProcessorService.WebhookResult> handleWebhook(
            @PathVariable("provider") String provider,
            @RequestHeader(value = "X-PayFlow-Signature", required = false) String signature,
            @RequestHeader(value = "X-PayFlow-Timestamp", required = false) String timestamp,
            @RequestBody String rawPayload
    ) {
        log.info("Received inbound webhook from provider [{}]", provider);
        WebhookProcessorService.WebhookResult result = webhookProcessorService.processWebhook(provider, rawPayload, signature, timestamp);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSignature(InvalidWebhookSignatureException ex, HttpServletRequest request) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .requestId((String) request.getAttribute("requestId"))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
}
