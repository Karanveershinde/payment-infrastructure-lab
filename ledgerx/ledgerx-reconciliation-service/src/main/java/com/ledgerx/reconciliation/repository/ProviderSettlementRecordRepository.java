package com.ledgerx.reconciliation.repository;

import com.ledgerx.common.domain.enums.ProviderType;
import com.ledgerx.reconciliation.domain.entity.ProviderSettlementRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderSettlementRecordRepository extends JpaRepository<ProviderSettlementRecordEntity, String> {

    List<ProviderSettlementRecordEntity> findByBatchId(String batchId);

    List<ProviderSettlementRecordEntity> findByProviderAndProviderTransactionId(ProviderType provider, String providerTransactionId);

    Optional<ProviderSettlementRecordEntity> findByInternalTransactionId(String internalTransactionId);
}
