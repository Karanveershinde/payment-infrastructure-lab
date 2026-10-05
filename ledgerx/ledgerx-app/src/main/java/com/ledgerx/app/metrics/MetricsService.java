package com.ledgerx.app.metrics;

import com.ledgerx.common.domain.enums.RiskDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MetricsService {

    private final Counter eventsReceivedCounter;
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter eventsReplayedCounter;
    private final Counter ledgerTransactionsCounter;
    private final Counter ledgerInvariantFailuresCounter;
    private final Counter reconciliationRecordsCounter;
    private final Counter reconciliationDiscrepanciesCounter;
    private final Counter highRiskTransactionsCounter;
    private final Counter dlqEventsCounter;
    private final Timer processingLatencyTimer;
    private final MeterRegistry registry;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;

        this.eventsReceivedCounter = Counter.builder("events_received_total")
                .description("Total number of events received by LedgerX")
                .register(registry);

        this.eventsProcessedCounter = Counter.builder("events_processed_total")
                .description("Total number of events successfully processed")
                .register(registry);

        this.eventsFailedCounter = Counter.builder("events_failed_total")
                .description("Total number of events that encountered processing failure")
                .register(registry);

        this.eventsReplayedCounter = Counter.builder("events_replayed_total")
                .description("Total number of events replayed through replay engine")
                .register(registry);

        this.ledgerTransactionsCounter = Counter.builder("ledger_transactions_total")
                .description("Total double-entry ledger transactions posted")
                .register(registry);

        this.ledgerInvariantFailuresCounter = Counter.builder("ledger_invariant_failures_total")
                .description("Total attempts that violated double-entry balancing invariant")
                .register(registry);

        this.reconciliationRecordsCounter = Counter.builder("reconciliation_records_total")
                .description("Total settlement records reconciled")
                .register(registry);

        this.reconciliationDiscrepanciesCounter = Counter.builder("reconciliation_discrepancies_total")
                .description("Total reconciliation discrepancies identified")
                .register(registry);

        this.highRiskTransactionsCounter = Counter.builder("high_risk_transactions_total")
                .description("Total transactions flagged as high-risk or blocked")
                .register(registry);

        this.dlqEventsCounter = Counter.builder("dlq_events_total")
                .description("Total events routed to Dead Letter Queue")
                .register(registry);

        this.processingLatencyTimer = Timer.builder("processing_latency")
                .description("End-to-end event processing latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void incrementEventsReceived() {
        eventsReceivedCounter.increment();
    }

    public void incrementEventsProcessed() {
        eventsProcessedCounter.increment();
    }

    public void incrementEventsFailed() {
        eventsFailedCounter.increment();
    }

    public void incrementEventsReplayed() {
        eventsReplayedCounter.increment();
    }

    public void incrementLedgerTransactions() {
        ledgerTransactionsCounter.increment();
    }

    public void incrementLedgerInvariantFailures() {
        ledgerInvariantFailuresCounter.increment();
    }

    public void incrementReconciliationRecords(int count) {
        reconciliationRecordsCounter.increment(count);
    }

    public void incrementReconciliationDiscrepancies(int count) {
        reconciliationDiscrepanciesCounter.increment(count);
    }

    public void recordRiskDecision(RiskDecision decision) {
        Counter.builder("risk_decisions_total")
                .tag("decision", decision.name())
                .register(registry)
                .increment();

        if (decision == RiskDecision.REJECT || decision == RiskDecision.REVIEW) {
            highRiskTransactionsCounter.increment();
        }
    }

    public void incrementDlqEvents() {
        dlqEventsCounter.increment();
    }

    public void recordProcessingLatency(long durationMs) {
        processingLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
