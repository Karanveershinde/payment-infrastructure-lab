import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

export const originalCreated = new Counter('original_created_count');
export const conflictOrReplay = new Counter('conflict_or_replay_count');
export const idempotencySuccessRate = new Rate('idempotency_success_rate');

export const options = {
    scenarios: {
        concurrent_duplicates: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 20,
            maxDuration: '10s',
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = 'pk_test_acme_123456';
// Shared identical idempotency key across all 20 concurrent threads
const SHARED_IDEMPOTENCY_KEY = 'idem_hammer_' + Date.now();

export default function () {
    const payload = JSON.stringify({
        amount: '250.00',
        currency: 'USD',
        paymentMethodType: 'CARD_TOKEN',
        paymentMethodToken: 'tok_visa_success',
        customerEmail: 'concurrency_test@example.com',
        captureMethod: 'AUTOMATIC',
        description: 'Hammering same idempotency key simultaneously',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
            'Idempotency-Key': SHARED_IDEMPOTENCY_KEY,
        },
    };

    const res = http.post(`${BASE_URL}/api/v1/payments`, payload, params);

    const validStatus = check(res, {
        'status is 201 Created, 200 OK, or 409 Conflict': (r) =>
            r.status === 201 || r.status === 200 || r.status === 409,
    });

    idempotencySuccessRate.add(validStatus);

    if (res.status === 201) {
        originalCreated.add(1);
    } else if (res.status === 200 || res.status === 409) {
        conflictOrReplay.add(1);
    }
}
