import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

export const rateLimit429Counter = new Counter('rate_limit_429_count');
export const allowed201Counter = new Counter('allowed_201_count');

export const options = {
    scenarios: {
        blast_rate_limit: {
            executor: 'constant-arrival-rate',
            rate: 50, // 50 reqs/sec -> 3000 reqs/min (merchant limit is 100 reqs/min)
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 20,
            maxVUs: 50,
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// Merchant 002 limit is 100 req/min
const API_KEY = 'pk_test_global_789012';

export default function () {
    const randomKey = `ratelimit_${__VU}_${__ITER}_${Math.random()}`;

    const payload = JSON.stringify({
        amount: '25.00',
        currency: 'USD',
        paymentMethodType: 'CARD_TOKEN',
        paymentMethodToken: 'tok_visa_success',
        customerEmail: 'ratelimit@example.com',
        captureMethod: 'AUTOMATIC',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
            'Idempotency-Key': randomKey,
        },
    };

    const res = http.post(`${BASE_URL}/api/v1/payments`, payload, params);

    check(res, {
        'status is 201 or 429': (r) => r.status === 201 || r.status === 429,
        'has rate limit headers': (r) => r.headers['X-Ratelimit-Limit'] !== undefined || r.headers['Retry-After'] !== undefined,
    });

    if (res.status === 429) {
        rateLimit429Counter.add(1);
    } else if (res.status === 201) {
        allowed201Counter.add(1);
    }
}
