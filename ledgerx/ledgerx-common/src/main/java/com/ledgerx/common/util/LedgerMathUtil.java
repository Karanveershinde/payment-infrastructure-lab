package com.ledgerx.common.util;

import com.ledgerx.common.domain.enums.EntryType;
import com.ledgerx.common.exception.LedgerInvariantException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Financial precision math utilities for Double-Entry Ledger accounting.
 * Standardizes 4 decimal places with Banker's Rounding (HALF_EVEN).
 */
public final class LedgerMathUtil {

    public static final int LEDGER_SCALE = 4;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(LEDGER_SCALE, ROUNDING_MODE);

    private LedgerMathUtil() {}

    public static BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(LEDGER_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal round(BigDecimal amount) {
        return scale(amount);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        BigDecimal valA = a == null ? ZERO : a;
        BigDecimal valB = b == null ? ZERO : b;
        return scale(valA.add(valB));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        BigDecimal valA = a == null ? ZERO : a;
        BigDecimal valB = b == null ? ZERO : b;
        return scale(valA.subtract(valB));
    }

    public static boolean isZero(BigDecimal amount) {
        return amount == null || scale(amount).compareTo(ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && scale(amount).compareTo(ZERO) > 0;
    }

    /**
     * Verifies double-entry balancing invariant: Total Debits must equal Total Credits.
     */
    public static boolean isBalanced(List<BigDecimal> debits, List<BigDecimal> credits) {
        BigDecimal totalDebits = debits.stream()
                .filter(Objects::nonNull)
                .reduce(ZERO, LedgerMathUtil::add);

        BigDecimal totalCredits = credits.stream()
                .filter(Objects::nonNull)
                .reduce(ZERO, LedgerMathUtil::add);

        return totalDebits.compareTo(totalCredits) == 0 && isPositive(totalDebits);
    }

    public static void validateDoubleEntry(List<DoubleEntryItem> items) {
        if (items == null || items.size() < 2) {
            throw new LedgerInvariantException("Double-entry transaction must contain at least 2 entries.");
        }

        List<BigDecimal> debits = new ArrayList<>();
        List<BigDecimal> credits = new ArrayList<>();

        for (DoubleEntryItem item : items) {
            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new LedgerInvariantException("Ledger entry amount must be strictly positive: " + item.getAmount());
            }
            if (item.getType() == EntryType.DEBIT) {
                debits.add(scale(item.getAmount()));
            } else if (item.getType() == EntryType.CREDIT) {
                credits.add(scale(item.getAmount()));
            } else {
                throw new LedgerInvariantException("Invalid entry type: " + item.getType());
            }
        }

        BigDecimal sumDebits = debits.stream().reduce(ZERO, LedgerMathUtil::add);
        BigDecimal sumCredits = credits.stream().reduce(ZERO, LedgerMathUtil::add);

        if (sumDebits.compareTo(sumCredits) != 0) {
            throw new LedgerInvariantException(String.format(
                    "Double-entry ledger invariant violation! Total Debits (%s) != Total Credits (%s)",
                    sumDebits, sumCredits));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class DoubleEntryItem {
        private final EntryType type;
        private final BigDecimal amount;
    }
}
