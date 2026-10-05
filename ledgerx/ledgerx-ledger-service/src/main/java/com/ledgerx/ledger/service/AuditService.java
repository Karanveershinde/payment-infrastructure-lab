package com.ledgerx.ledger.service;

import com.ledgerx.common.domain.enums.AuditAction;
import com.ledgerx.common.dto.response.AuditEventResponse;
import com.ledgerx.common.util.JsonUtil;
import com.ledgerx.ledger.domain.entity.AuditEventEntity;
import com.ledgerx.ledger.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEventEntity record(
            String actor,
            AuditAction action,
            String resourceId,
            String resourceType,
            Object details,
            String requestId
    ) {
        String detailsJson = (details instanceof String) ? (String) details : JsonUtil.toJson(details);

        AuditEventEntity event = AuditEventEntity.builder()
                .id(UUID.randomUUID().toString())
                .actor(actor != null ? actor : "SYSTEM")
                .action(action)
                .resourceId(resourceId)
                .resourceType(resourceType)
                .detailsJson(detailsJson)
                .requestId(requestId)
                .build();

        log.info("Recording immutable audit event: action={} resourceType={} resourceId={} actor={}",
                action, resourceType, resourceId, actor);
        return auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getAuditTrail(String resourceType, String resourceId) {
        return auditEventRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(resourceType, resourceId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> getRecentAuditEvents(Pageable pageable) {
        return auditEventRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @SuppressWarnings("unchecked")
    private AuditEventResponse toResponse(AuditEventEntity entity) {
        Map<String, Object> details = null;
        if (entity.getDetailsJson() != null && !entity.getDetailsJson().isBlank()) {
            try {
                details = JsonUtil.fromJson(entity.getDetailsJson(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse audit details json: {}", entity.getDetailsJson());
            }
        }

        return AuditEventResponse.builder()
                .id(entity.getId())
                .actor(entity.getActor())
                .action(entity.getAction())
                .resourceId(entity.getResourceId())
                .resourceType(entity.getResourceType())
                .details(details)
                .requestId(entity.getRequestId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
