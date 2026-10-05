-- V4__create_held_events_table.sql
-- LedgerX Out-of-Order Deferred Event Storage

CREATE TABLE IF NOT EXISTS held_events (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    transaction_id VARCHAR(36) NOT NULL,
    merchant_id VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',
    payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_held_events_merchant ON held_events(merchant_id, status);
