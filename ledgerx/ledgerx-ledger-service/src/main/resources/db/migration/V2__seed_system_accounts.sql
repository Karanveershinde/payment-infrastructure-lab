-- V2__seed_system_accounts.sql
-- Seed Core Ledger Accounts for Double-Entry Accounting

-- System Clearing Account (Transit Asset Account)
INSERT INTO accounts (id, account_number, entity_id, account_type, currency, balance, version, status, created_at, updated_at)
VALUES (
    'acc_sys_clearing_usd',
    'ACC_SYS_CLEARING_USD',
    'SYSTEM_CLEARING',
    'CLEARING',
    'USD',
    0.0000,
    0,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- System Settlement Account (Bank Settled Asset Account)
INSERT INTO accounts (id, account_number, entity_id, account_type, currency, balance, version, status, created_at, updated_at)
VALUES (
    'acc_sys_settlement_usd',
    'ACC_SYS_SETTLEMENT_USD',
    'SYSTEM_SETTLEMENT',
    'SETTLEMENT',
    'USD',
    0.0000,
    0,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- System Fee Revenue Account (Revenue Account)
INSERT INTO accounts (id, account_number, entity_id, account_type, currency, balance, version, status, created_at, updated_at)
VALUES (
    'acc_sys_fee_usd',
    'ACC_SYS_FEE_USD',
    'SYSTEM_FEE',
    'FEE_REVENUE',
    'USD',
    0.0000,
    0,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- System Suspense Account (Anomalous & Discrepancy Holding)
INSERT INTO accounts (id, account_number, entity_id, account_type, currency, balance, version, status, created_at, updated_at)
VALUES (
    'acc_sys_suspense_usd',
    'ACC_SYS_SUSPENSE_USD',
    'SYSTEM_SUSPENSE',
    'SUSPENSE',
    'USD',
    0.0000,
    0,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Merchant Payable: Acme Corp Demo
INSERT INTO accounts (id, account_number, entity_id, account_type, currency, balance, version, status, created_at, updated_at)
VALUES (
    'acc_mer_acme_001_usd',
    'ACC_MER_ACME_001_USD',
    'mer_acme_001',
    'MERCHANT_PAYABLE',
    'USD',
    0.0000,
    0,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- Merchant Payable: Global Retailers
INSERT INTO accounts (id, account_number, entity_id, account_type, currency, balance, version, status, created_at, updated_at)
VALUES (
    'acc_mer_global_002_usd',
    'ACC_MER_GLOBAL_002_USD',
    'mer_global_002',
    'MERCHANT_PAYABLE',
    'USD',
    0.0000,
    0,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
