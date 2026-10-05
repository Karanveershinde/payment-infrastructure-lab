-- V3__reconciliation_schema.sql
-- LedgerX Payment Reconciliation & Discrepancy Schema

CREATE TABLE IF NOT EXISTS reconciliation_batches (
    id VARCHAR(36) PRIMARY KEY,
    batch_date DATE NOT NULL,
    provider VARCHAR(30) NOT NULL,
    total_records INT NOT NULL DEFAULT 0,
    matched_count INT NOT NULL DEFAULT 0,
    discrepancy_count INT NOT NULL DEFAULT 0,
    total_internal_amount NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    total_provider_amount NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reconcile_batch_date ON reconciliation_batches(batch_date DESC, provider);

CREATE TABLE IF NOT EXISTS provider_settlement_records (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_transaction_id VARCHAR(100) NOT NULL,
    internal_transaction_id VARCHAR(36),
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    settlement_date TIMESTAMP WITH TIME ZONE NOT NULL,
    raw_record TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_batch FOREIGN KEY (batch_id) REFERENCES reconciliation_batches(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_settlement_batch ON provider_settlement_records(batch_id);
CREATE INDEX IF NOT EXISTS idx_settlement_provider_tx ON provider_settlement_records(provider, provider_transaction_id);

CREATE TABLE IF NOT EXISTS reconciliation_discrepancies (
    id VARCHAR(36) PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    internal_transaction_id VARCHAR(36),
    provider_transaction_id VARCHAR(100),
    discrepancy_type VARCHAR(40) NOT NULL,
    internal_amount NUMERIC(18, 4),
    provider_amount NUMERIC(18, 4),
    internal_status VARCHAR(30),
    provider_status VARCHAR(30),
    resolution_status VARCHAR(30) NOT NULL DEFAULT 'UNRESOLVED',
    resolution_notes TEXT,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_discrepancy_batch FOREIGN KEY (batch_id) REFERENCES reconciliation_batches(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_discrepancies_batch ON reconciliation_discrepancies(batch_id);
CREATE INDEX IF NOT EXISTS idx_discrepancies_type ON reconciliation_discrepancies(discrepancy_type, resolution_status);
