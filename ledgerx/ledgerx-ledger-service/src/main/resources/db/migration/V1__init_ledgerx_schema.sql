-- V1__init_ledgerx_schema.sql
-- LedgerX Core Double-Entry Accounting & Financial Invariant Schema

CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR(36) PRIMARY KEY,
    account_number VARCHAR(64) NOT NULL UNIQUE,
    entity_id VARCHAR(36) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_accounts_entity ON accounts(entity_id, account_type);
CREATE INDEX IF NOT EXISTS idx_accounts_number ON accounts(account_number);

CREATE TABLE IF NOT EXISTS ledger_transactions (
    id VARCHAR(36) PRIMARY KEY,
    reference_id VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ledger_tx_ref ON ledger_transactions(reference_id);
CREATE INDEX IF NOT EXISTS idx_ledger_tx_created ON ledger_transactions(created_at DESC);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id VARCHAR(36) PRIMARY KEY,
    ledger_transaction_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entry_transaction FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions(id) ON DELETE CASCADE,
    CONSTRAINT fk_entry_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_entry_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX IF NOT EXISTS idx_entries_tx ON ledger_entries(ledger_transaction_id);
CREATE INDEX IF NOT EXISTS idx_entries_account ON ledger_entries(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL,
    merchant_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_transaction_id VARCHAR(100),
    transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    risk_score INT,
    risk_level VARCHAR(20),
    reconciliation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    raw_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transactions_event UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_transactions_merchant ON transactions(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_customer ON transactions(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_provider_txn ON transactions(provider_transaction_id);
CREATE INDEX IF NOT EXISTS idx_transactions_reconcile ON transactions(reconciliation_status, provider);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(128) NOT NULL,
    consumer_name VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_time ON processed_events(processed_at DESC);

CREATE TABLE IF NOT EXISTS audit_events (
    id VARCHAR(36) PRIMARY KEY,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    details_json TEXT,
    request_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_resource ON audit_events(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_events(created_at DESC);

CREATE TABLE IF NOT EXISTS dead_letter_events (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    partition_num INT,
    offset_num BIGINT,
    failure_reason VARCHAR(1000),
    stacktrace TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
    first_failed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_failed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    replayed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_dlq_status ON dead_letter_events(status, last_failed_at DESC);
