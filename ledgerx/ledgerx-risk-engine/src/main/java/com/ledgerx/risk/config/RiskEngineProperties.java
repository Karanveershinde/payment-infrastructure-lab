package com.ledgerx.risk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "ledgerx.risk")
public class RiskEngineProperties {

    private boolean enabled = true;

    // Rule Score Weights
    private int highAmountScore = 30;
    private int velocityBurstScore = 25;
    private int repeatFailureScore = 20;
    private int multiPaymentMethodScore = 20;
    private int highRiskMerchantScore = 15;
    private int unusualRefundRatioScore = 20;

    // Thresholds
    private BigDecimal highAmountThreshold = new BigDecimal("5000.00");
    private int velocityBurstLimit = 5; // > 5 transactions in 60 seconds
    private int repeatFailureLimit = 3; // >= 3 declines in 10 minutes
    private int multiPaymentMethodLimit = 2; // > 2 cards in 10 minutes
    private BigDecimal merchantHourlyVolumeLimit = new BigDecimal("20000.00"); // > $20k in 1h
    private double refundRatioLimit = 0.30; // > 30% refund ratio

    // Decision Thresholds
    private int mediumThreshold = 31;
    private int highThreshold = 61;
    private int blockThreshold = 81;
}
