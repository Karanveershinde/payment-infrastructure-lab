package com.ledgerx.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerMathUtilTest {

    @Test
    @DisplayName("Verify double-entry balancing invariant calculation")
    void testIsBalanced() {
        // Balanced 100.00 debit = 100.00 credit
        List<BigDecimal> debits = List.of(new BigDecimal("100.00"));
        List<BigDecimal> credits = List.of(new BigDecimal("97.50"), new BigDecimal("2.50"));

        assertThat(LedgerMathUtil.isBalanced(debits, credits)).isTrue();
    }

    @Test
    @DisplayName("Verify unbalanced entries are rejected")
    void testUnbalancedEntries() {
        List<BigDecimal> debits = List.of(new BigDecimal("100.00"));
        List<BigDecimal> credits = List.of(new BigDecimal("99.00"));

        assertThat(LedgerMathUtil.isBalanced(debits, credits)).isFalse();
    }

    @Test
    @DisplayName("Verify zero amounts are rejected as invalid entries")
    void testZeroEntries() {
        List<BigDecimal> debits = List.of(BigDecimal.ZERO);
        List<BigDecimal> credits = List.of(BigDecimal.ZERO);

        assertThat(LedgerMathUtil.isBalanced(debits, credits)).isFalse();
    }

    @Test
    @DisplayName("Verify precision scaling with Banker's Rounding")
    void testPrecisionScaling() {
        BigDecimal scaled = LedgerMathUtil.scale(new BigDecimal("123.456789"));
        assertThat(scaled).isEqualTo(new BigDecimal("123.4568"));
    }
}
