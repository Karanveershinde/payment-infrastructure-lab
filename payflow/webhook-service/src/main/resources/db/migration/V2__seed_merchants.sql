-- V2__seed_merchants.sql
-- Seed Initial Demo Merchants

INSERT INTO merchants (id, api_key_hash, name, webhook_url, rate_limit_per_minute, status, created_at)
VALUES (
    'mer_acme_001',
    '4dfc15e219fb09623e1ce4998797b1bb64bcf6825c0e7681604a37a1c7c98028',
    'Acme Corp Demo Merchant',
    'http://localhost:8083/api/v1/notifications/webhook-sink',
    200,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

INSERT INTO merchants (id, api_key_hash, name, webhook_url, rate_limit_per_minute, status, created_at)
VALUES (
    'mer_global_002',
    '8c9c0f997cb658c29302507bfec06175ec437c357591e12795ec8e2ecaa21568',
    'Global Retailers Inc',
    'http://localhost:8083/api/v1/notifications/webhook-sink',
    100,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

INSERT INTO merchants (id, api_key_hash, name, webhook_url, rate_limit_per_minute, status, created_at)
VALUES (
    'mer_admin_000',
    '348df8fbe29a28c5e62bfb992f80eb3c490ff73aa7b494632a588b39417f7b32',
    'PayFlow Admin Root',
    'http://localhost:8083/api/v1/notifications/webhook-sink',
    1000,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
