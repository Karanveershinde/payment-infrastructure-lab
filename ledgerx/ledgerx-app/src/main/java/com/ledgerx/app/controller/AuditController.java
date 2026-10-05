package com.ledgerx.app.controller;

import com.ledgerx.common.dto.response.AuditEventResponse;
import com.ledgerx.ledger.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "Endpoints for retrieving immutable financial audit records and operational history")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "List recent audit events", description = "Retrieves paginated stream of immutable audit records.")
    public ResponseEntity<Page<AuditEventResponse>> getAuditEvents(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(auditService.getRecentAuditEvents(pageable));
    }

    @GetMapping("/{resourceType}/{resourceId}")
    @Operation(summary = "Get audit trail for a resource", description = "Retrieves full chronological audit trail for a specific entity or resource ID.")
    public ResponseEntity<List<AuditEventResponse>> getResourceAuditTrail(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("resourceId") String resourceId
    ) {
        return ResponseEntity.ok(auditService.getAuditTrail(resourceType, resourceId));
    }
}
