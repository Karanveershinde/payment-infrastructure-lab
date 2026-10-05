package com.ledgerx.ledger.repository;

import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.ledger.domain.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, String> {

    @Modifying
    @Query("UPDATE AccountEntity a SET a.balance = a.balance + :delta WHERE a.id = :id AND (a.balance + :delta >= 0)")
    int updateBalanceDelta(@Param("id") String id, @Param("delta") BigDecimal delta);

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    Optional<AccountEntity> findByEntityIdAndAccountTypeAndCurrency(String entityId, AccountType accountType, Currency currency);

    List<AccountEntity> findByEntityId(String entityId);
}
