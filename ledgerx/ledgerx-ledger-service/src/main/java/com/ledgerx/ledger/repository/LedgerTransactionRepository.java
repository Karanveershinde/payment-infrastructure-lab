package com.ledgerx.ledger.repository;

import com.ledgerx.ledger.domain.entity.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, String> {

    List<LedgerTransactionEntity> findByReferenceId(String referenceId);
}
