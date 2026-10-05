package com.ledgerx.risk.tracker;

import com.ledgerx.common.util.LedgerMathUtil;
import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@ConditionalOnMissingBean(name = "redisVelocityTracker")
public class InMemoryVelocityTracker implements VelocityTracker {

    @Getter
    @Builder
    private static class TxRecord {
        private final String customerId;
        private final String merchantId;
        private final String paymentMethodToken;
        private final BigDecimal amount;
        private final boolean isRefund;
        private final boolean isSuccess;
        private final Instant timestamp;
    }

    private final Map<String, List<TxRecord>> customerHistory = new ConcurrentHashMap<>();
    private final Map<String, List<TxRecord>> merchantHistory = new ConcurrentHashMap<>();

    @Override
    public synchronized void recordTransaction(
            String customerId,
            String merchantId,
            String paymentMethodToken,
            BigDecimal amount,
            boolean isRefund,
            boolean isSuccess
    ) {
        TxRecord record = TxRecord.builder()
                .customerId(customerId)
                .merchantId(merchantId)
                .paymentMethodToken(paymentMethodToken)
                .amount(amount != null ? amount : BigDecimal.ZERO)
                .isRefund(isRefund)
                .isSuccess(isSuccess)
                .timestamp(Instant.now())
                .build();

        if (customerId != null) {
            customerHistory.computeIfAbsent(customerId, k -> new ArrayList<>()).add(record);
        }
        if (merchantId != null) {
            merchantHistory.computeIfAbsent(merchantId, k -> new ArrayList<>()).add(record);
        }
    }

    @Override
    public synchronized int getCustomerTxCountLast60s(String customerId) {
        if (customerId == null || !customerHistory.containsKey(customerId)) {
            return 0;
        }
        Instant cutoff = Instant.now().minusSeconds(60);
        return (int) customerHistory.get(customerId).stream()
                .filter(r -> r.getTimestamp().isAfter(cutoff))
                .count();
    }

    @Override
    public synchronized int getCustomerTxCountLast10m(String customerId) {
        if (customerId == null || !customerHistory.containsKey(customerId)) {
            return 0;
        }
        Instant cutoff = Instant.now().minusSeconds(600);
        return (int) customerHistory.get(customerId).stream()
                .filter(r -> r.getTimestamp().isAfter(cutoff))
                .count();
    }

    @Override
    public synchronized int getCustomerFailuresLast10m(String customerId) {
        if (customerId == null || !customerHistory.containsKey(customerId)) {
            return 0;
        }
        Instant cutoff = Instant.now().minusSeconds(600);
        return (int) customerHistory.get(customerId).stream()
                .filter(r -> !r.isSuccess() && r.getTimestamp().isAfter(cutoff))
                .count();
    }

    @Override
    public synchronized int getCustomerDistinctPaymentMethodsLast10m(String customerId) {
        if (customerId == null || !customerHistory.containsKey(customerId)) {
            return 0;
        }
        Instant cutoff = Instant.now().minusSeconds(600);
        return (int) customerHistory.get(customerId).stream()
                .filter(r -> r.getTimestamp().isAfter(cutoff) && r.getPaymentMethodToken() != null && !r.getPaymentMethodToken().isBlank())
                .map(TxRecord::getPaymentMethodToken)
                .distinct()
                .count();
    }

    @Override
    public synchronized BigDecimal getMerchantVolumeLast1h(String merchantId) {
        if (merchantId == null || !merchantHistory.containsKey(merchantId)) {
            return LedgerMathUtil.ZERO;
        }
        Instant cutoff = Instant.now().minusSeconds(3600);
        return merchantHistory.get(merchantId).stream()
                .filter(r -> !r.isRefund() && r.isSuccess() && r.getTimestamp().isAfter(cutoff))
                .map(TxRecord::getAmount)
                .reduce(LedgerMathUtil.ZERO, LedgerMathUtil::add);
    }

    @Override
    public synchronized double getMerchantRefundRatio(String merchantId) {
        if (merchantId == null || !merchantHistory.containsKey(merchantId)) {
            return 0.0;
        }
        Instant cutoff = Instant.now().minusSeconds(3600 * 24); // 24 hours window
        List<TxRecord> records = merchantHistory.get(merchantId).stream()
                .filter(r -> r.getTimestamp().isAfter(cutoff))
                .collect(Collectors.toList());

        if (records.isEmpty()) {
            return 0.0;
        }

        long refundCount = records.stream().filter(TxRecord::isRefund).count();
        return (double) refundCount / (double) records.size();
    }

    @Override
    public synchronized void reset() {
        customerHistory.clear();
        merchantHistory.clear();
    }
}
