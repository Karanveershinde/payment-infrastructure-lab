import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
export const paymentSuccessRate = new Rate('payment_success_rate');
export const paymentDuration = new Trend('payment_duration_ms');
export const idempotencyReplays = new Counter('idempotency_replays');

export const options = {
    stages: [
        { duration: '10s', target: 20 },  // Ramp up to 20 VUs
        { duration: '30s', target: 50 },  // Hold at 50 VUs
        { duration: '10s', target: 0 },   // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<1000', 'p(99)<2000'],
        'payment_success_rate': ['rate>0.90'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = 'pk_test_acme_123456';

export default function () {
    const randomId = Math.random().toString(36).substring(2, 12);
    const idempotencyKey = `k6_bench_${__VU}_${__ITER}_${randomId}`;

    const payload = JSON.stringify({
        amount: (Math.random() * 100 + 10).toFixed(2),
        currency: 'USD',
        paymentMethodType: 'CARD_TOKEN',
        paymentMethodToken: 'tok_visa_success',
        customerEmail: `loadtest_${__VU}@example.com`,
        captureMethod: 'AUTOMATIC',
        description: 'k6 load test transaction',
        metadata: {
            vu: __VU,
            iteration: __ITER,
        }
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
            'Idempotency-Key': idempotencyKey,
        },
    };

    const startTime = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/payments`, payload, params);
    const latency = Date.now() - startTime;

    paymentDuration.add(latency);

    const isSuccess = check(res, {
        'status is 201 Created or 200 OK': (r) => r.status === 201 || r.status === 200,
        'response contains paymentId': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.paymentId !== undefined && body.paymentId.startsWith('pay_');
            } catch (e) {
                return false;
            }
        },
        'payment status is CAPTURED': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'CAPTURED';
            } catch (e) {
                return false;
            }
        }
    });

    paymentSuccessRate.add(isSuccess);

    sleep(0.1); // 100ms think time between transactions
}
