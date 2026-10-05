package com.ledgerx.app.controller;

import com.ledgerx.common.dto.request.IngestTransactionRequest;
import com.ledgerx.common.dto.response.RiskEvaluationResponse;
import com.ledgerx.risk.service.RiskEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "Risk Engine", description = "Endpoints for deterministic rule-based fraud scoring, velocity analysis, and risk decisioning")
public class RiskController {

    private final RiskEngineService riskEngineService;

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate transaction risk", description = "Executes deterministic risk rules and velocity aggregations to produce a risk score, level, and decision.")
    public ResponseEntity<RiskEvaluationResponse> evaluateRisk(@Valid @RequestBody IngestTransactionRequest request) {
        return ResponseEntity.ok(riskEngineService.evaluate(request));
    }
}
