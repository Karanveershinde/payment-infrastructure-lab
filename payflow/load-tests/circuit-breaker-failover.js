import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const fallbackSuccessRate = new Rate('fallback_success_rate');

export const options = {
    vus: 10,
    duration: '20s',
};

const PAYMENT_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SIMULATOR_URL = __ENV.SIMULATOR_URL || 'http://localhost:8081';
const API_KEY = 'pk_test_acme_123456';

export function setup() {
    // Inject 100% outage into ProviderA to test automatic failover to ProviderB
    const failurePayload = JSON.stringify({
        provider: 'PROVIDER_A',
        forceOutage: true,
    });

    const res = http.post(`${SIMULATOR_URL}/api/simulator/config`, failurePayload, {
        headers: { 'Content-Type': 'application/json' },
    });
    console.log(`Simulator ProviderA Outage Injected: ${res.status}`);
}

export default function () {
    const randomKey = `failover_${__VU}_${__ITER}_${Math.random()}`;

    const payload = JSON.stringify({
        amount: '89.99',
        currency: 'USD',
        paymentMethodType: 'CARD_TOKEN',
        paymentMethodToken: 'tok_visa_success',
        customerEmail: 'failover_test@example.com',
        captureMethod: 'AUTOMATIC',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
            'Idempotency-Key': randomKey,
        },
    };

    const res = http.post(`${PAYMENT_URL}/api/v1/payments`, payload, params);

    const isSuccess = check(res, {
        'status is 201 Created': (r) => r.status === 201,
        'routed to fallback provider B or C': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.provider === 'PROVIDER_B' || body.provider === 'PROVIDER_C';
            } catch (e) {
                return false;
            }
        },
    });

    fallbackSuccessRate.add(isSuccess);
    sleep(0.2);
}

export function teardown() {
    // Restore ProviderA
    const restorePayload = JSON.stringify({
        provider: 'PROVIDER_A',
        forceOutage: false,
        successRate: 0.95,
    });
    http.post(`${SIMULATOR_URL}/api/simulator/config`, restorePayload, {
        headers: { 'Content-Type': 'application/json' },
    });
    console.log('Restored ProviderA healthy status');
}
