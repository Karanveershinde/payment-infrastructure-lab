package com.ledgerx.ledger.repository;

import com.ledgerx.ledger.domain.entity.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, String> {

    List<LedgerEntryEntity> findByAccountId(String accountId);

    List<LedgerEntryEntity> findByLedgerTransactionId(String ledgerTransactionId);
}
