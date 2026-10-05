package com.ledgerx.ledger.repository;

import com.ledgerx.ledger.domain.entity.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

    List<AuditEventEntity> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, String resourceId);

    Page<AuditEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
