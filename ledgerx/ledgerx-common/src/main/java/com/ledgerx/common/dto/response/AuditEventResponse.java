package com.ledgerx.common.dto.response;

import com.ledgerx.common.domain.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventResponse {
    private String id;
    private String actor;
    private AuditAction action;
    private String resourceId;
    private String resourceType;
    private Map<String, Object> details;
    private String requestId;
    private Instant createdAt;
}
