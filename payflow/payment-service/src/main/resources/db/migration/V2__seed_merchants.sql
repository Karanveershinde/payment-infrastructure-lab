-- V2__seed_merchants.sql
-- Seed Initial Demo Merchants

-- Raw Key: pk_test_acme_123456
-- SHA-256 Hash of "pk_test_acme_123456": 9c084563f3a56ed9e00a1ded9adf3af67191c9cec732a5d462a34f87806dd37f
INSERT INTO merchants (id, api_key_hash, name, webhook_url, rate_limit_per_minute, status, created_at)
VALUES (
    'mer_acme_001',
    '9c084563f3a56ed9e00a1ded9adf3af67191c9cec732a5d462a34f87806dd37f',
    'Acme Corp Demo Merchant',
    'http://localhost:8083/api/v1/notifications/webhook-sink',
    200,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET api_key_hash = EXCLUDED.api_key_hash;

-- Raw Key: pk_test_global_789012
INSERT INTO merchants (id, api_key_hash, name, webhook_url, rate_limit_per_minute, status, created_at)
VALUES (
    'mer_global_002',
    'e684250b16b29ce66a4b89aba4583125b5356635c27410aedf1e08912057df96',
    'Global Retailers Inc',
    'http://localhost:8083/api/v1/notifications/webhook-sink',
    100,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET api_key_hash = EXCLUDED.api_key_hash;

-- Raw Key: pk_test_admin_supersecret
INSERT INTO merchants (id, api_key_hash, name, webhook_url, rate_limit_per_minute, status, created_at)
VALUES (
    'mer_admin_000',
    '7445247232493acab38ebc4137e07c596c4581900801026989a29a985185db3f',
    'PayFlow Admin Root',
    'http://localhost:8083/api/v1/notifications/webhook-sink',
    1000,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET api_key_hash = EXCLUDED.api_key_hash;
