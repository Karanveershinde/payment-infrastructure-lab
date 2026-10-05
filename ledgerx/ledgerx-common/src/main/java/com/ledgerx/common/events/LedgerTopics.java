package com.ledgerx.common.events;

public final class LedgerTopics {

    private LedgerTopics() {}

    public static final String TRANSACTIONS_RAW = "transactions.raw";
    public static final String TRANSACTIONS_VALIDATED = "transactions.validated";
    public static final String LEDGER_POSTED = "ledger.posted";
    public static final String RISK_EVALUATED = "risk.evaluated";
    public static final String RECONCILIATION_REQUESTED = "reconciliation.requested";
    public static final String RECONCILIATION_COMPLETED = "reconciliation.completed";
    public static final String RECONCILIATION_DISCREPANCY = "reconciliation.discrepancy";
    public static final String DEAD_LETTER_EVENTS = "dead-letter.events";
}
