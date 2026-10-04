package com.payflow.payment.controller;

import com.payflow.payment.security.MerchantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchant API", description = "Merchant account details and configuration")
@SecurityRequirement(name = "ApiKeyAuth")
public class MerchantController {

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated merchant info")
    public ResponseEntity<Map<String, Object>> getMerchantProfile(@AuthenticationPrincipal MerchantPrincipal merchant) {
        return ResponseEntity.ok(Map.of(
                "merchantId", merchant.getMerchantId(),
                "name", merchant.getName(),
                "rateLimitPerMinute", merchant.getRateLimitPerMinute(),
                "status", merchant.getStatus()
        ));
    }
}
