package com.ledgerx.ledger.service;

import com.ledgerx.common.domain.enums.AccountType;
import com.ledgerx.common.domain.enums.Currency;
import com.ledgerx.common.dto.response.LedgerAccountResponse;
import com.ledgerx.common.exception.AccountInactiveException;
import com.ledgerx.common.exception.AccountNotFoundException;
import com.ledgerx.common.util.LedgerMathUtil;
import com.ledgerx.ledger.domain.entity.AccountEntity;
import com.ledgerx.ledger.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountEntity getAccountById(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    @Transactional(readOnly = true)
    public AccountEntity getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));
    }

    @Transactional(readOnly = true)
    public LedgerAccountResponse getAccountResponse(String accountId) {
        AccountEntity entity = getAccountById(accountId);
        return toResponse(entity);
    }

    @Transactional
    public AccountEntity getOrCreateAccount(String entityId, AccountType accountType, Currency currency) {
        return accountRepository.findByEntityIdAndAccountTypeAndCurrency(entityId, accountType, currency)
                .orElseGet(() -> createAccount(entityId, accountType, currency));
    }

    @Transactional
    public AccountEntity createAccount(String entityId, AccountType accountType, Currency currency) {
        String accountNumber = generateAccountNumber(entityId, accountType, currency);
        AccountEntity account = AccountEntity.builder()
                .id(UUID.randomUUID().toString())
                .accountNumber(accountNumber)
                .entityId(entityId)
                .accountType(accountType)
                .currency(currency)
                .balance(LedgerMathUtil.scale(BigDecimal.ZERO))
                .status("ACTIVE")
                .build();

        log.info("Creating new account {} for entity {} type {} currency {}",
                accountNumber, entityId, accountType, currency);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<LedgerAccountResponse> getAccountsByEntity(String entityId) {
        return accountRepository.findByEntityId(entityId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void validateAccountActive(AccountEntity account) {
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new AccountInactiveException("Account is not active: " + account.getAccountNumber());
        }
    }

    private String generateAccountNumber(String entityId, AccountType type, Currency currency) {
        String prefix = switch (type) {
            case CLEARING -> "CLR";
            case SETTLEMENT -> "SET";
            case MERCHANT_PAYABLE -> "PAY";
            case CUSTOMER_RECEIVABLE -> "REC";
            case FEE_REVENUE -> "REV";
            case SUSPENSE -> "SUS";
            case RESERVE -> "RES";
        };
        String entityClean = entityId.length() > 8 ? entityId.substring(0, 8) : entityId;
        return String.format("%s-%s-%s-%s", prefix, entityClean.toUpperCase(), currency.name(), UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    }

    private LedgerAccountResponse toResponse(AccountEntity entity) {
        return LedgerAccountResponse.builder()
                .id(entity.getId())
                .accountNumber(entity.getAccountNumber())
                .entityId(entity.getEntityId())
                .accountType(entity.getAccountType())
                .currency(entity.getCurrency())
                .balance(entity.getBalance())
                .status(entity.getStatus())
                .version(1L)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
