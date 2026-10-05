package com.ledgerx.risk.tracker;

import java.math.BigDecimal;

public interface VelocityTracker {

    void recordTransaction(
            String customerId,
            String merchantId,
            String paymentMethodToken,
            BigDecimal amount,
            boolean isRefund,
            boolean isSuccess
    );

    int getCustomerTxCountLast60s(String customerId);

    int getCustomerTxCountLast10m(String customerId);

    int getCustomerFailuresLast10m(String customerId);

    int getCustomerDistinctPaymentMethodsLast10m(String customerId);

    BigDecimal getMerchantVolumeLast1h(String merchantId);

    double getMerchantRefundRatio(String merchantId);

    void reset();
}
