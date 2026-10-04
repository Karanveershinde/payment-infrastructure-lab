package com.payflow.simulator.controller;

import com.payflow.common.dto.provider.ProviderModels;
import com.payflow.simulator.service.SimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
@Tag(name = "Provider Simulator API", description = "Mock Payment Providers (ProviderA, ProviderB, ProviderC) & Chaos Failure Injection")
public class SimulatorController {

    private final SimulatorService simulatorService;

    @PostMapping("/{provider}/authorize")
    @Operation(summary = "Simulate payment authorization")
    public ResponseEntity<ProviderModels.ProviderResponse> authorize(
            @PathVariable("provider") String provider,
            @RequestBody ProviderModels.AuthorizeRequest request
    ) {
        log.info("Simulator received authorize request on [{}]: paymentId={}", provider, request.getPaymentId());
        ProviderModels.ProviderResponse response = simulatorService.authorize(provider, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{provider}/capture")
    @Operation(summary = "Simulate payment capture")
    public ResponseEntity<ProviderModels.ProviderResponse> capture(
            @PathVariable("provider") String provider,
            @RequestBody ProviderModels.CaptureRequest request
    ) {
        log.info("Simulator received capture request on [{}]: txnId={}", provider, request.getProviderTransactionId());
        ProviderModels.ProviderResponse response = simulatorService.capture(provider, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{provider}/refund")
    @Operation(summary = "Simulate payment refund")
    public ResponseEntity<ProviderModels.ProviderResponse> refund(
            @PathVariable("provider") String provider,
            @RequestBody ProviderModels.RefundRequest request
    ) {
        log.info("Simulator received refund request on [{}]: txnId={}", provider, request.getProviderTransactionId());
        ProviderModels.ProviderResponse response = simulatorService.refund(provider, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{provider}/status/{providerTxnId}")
    @Operation(summary = "Get transaction status from provider ledger")
    public ResponseEntity<ProviderModels.ProviderResponse> getStatus(
            @PathVariable("provider") String provider,
            @PathVariable("providerTxnId") String providerTxnId
    ) {
        ProviderModels.ProviderResponse response = simulatorService.getStatus(provider, providerTxnId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/config")
    @Operation(summary = "Inject failure, latency, or outage settings into simulated providers")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody ProviderModels.FailureInjectionRequest request) {
        log.warn("Simulator failure injection triggered: {}", request);
        simulatorService.updateConfig(request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Simulation configuration updated successfully"));
    }

    @GetMapping("/config")
    @Operation(summary = "Get active simulation and failure injection configuration")
    public ResponseEntity<Object> getConfig() {
        return ResponseEntity.ok(simulatorService.getConfig());
    }
}
