import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import { Counter, Rate } from 'k6/metrics';

export const duplicateProcessed = new Counter('webhook_duplicates_counter');
export const newProcessed = new Counter('webhook_new_counter');
export const webhookSuccessRate = new Rate('webhook_success_rate');

export const options = {
    vus: 10,
    iterations: 100,
};

const WEBHOOK_URL = __ENV.WEBHOOK_URL || 'http://localhost:8082';
const SECRET = 'whsec_test_secret_key_1234567890';

export default function () {
    // 50% chance of reusing fixed duplicate event ID to stress deduplication engine
    const isDuplicate = (__ITER % 2 === 0);
    const eventId = isDuplicate ? 'evt_duplicate_stress_test' : `evt_k6_${__VU}_${__ITER}_${Date.now()}`;

    const payloadObj = {
        provider: 'PROVIDER_A',
        providerEventId: eventId,
        eventType: 'payment.capture.success',
        paymentId: `pay_test_${__ITER}`,
        status: 'CAPTURED',
        amount: 100.00,
        currency: 'USD',
        timestamp: new Date().toISOString(),
    };

    const payload = JSON.stringify(payloadObj);
    const timestampStr = Math.floor(Date.now() / 1000).toString();
    const signature = crypto.hmac('sha256', SECRET, `${timestampStr}.${payload}`, 'hex');

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-PayFlow-Signature': signature,
            'X-PayFlow-Timestamp': timestampStr,
        },
    };

    const res = http.post(`${WEBHOOK_URL}/api/v1/webhooks/provider_a`, payload, params);

    const isSuccess = check(res, {
        'status is 200 OK': (r) => r.status === 200,
        'valid webhook result status': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'PROCESSED' || body.status === 'DUPLICATE';
            } catch (e) {
                return false;
            }
        }
    });

    webhookSuccessRate.add(isSuccess);

    try {
        const body = JSON.parse(res.body);
        if (body.status === 'DUPLICATE') {
            duplicateProcessed.add(1);
        } else if (body.status === 'PROCESSED') {
            newProcessed.add(1);
        }
    } catch (e) {}
}
