import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    ingestion_and_reconciliation: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';

export default function () {
  const randomSuffix = Math.floor(Math.random() * 1000000);
  const txPayload = JSON.stringify({
    eventId: `evt_k6_${randomSuffix}_${Date.now()}`,
    transactionId: `tx_k6_${randomSuffix}`,
    merchantId: `merch_k6_${randomSuffix % 20}`,
    customerId: `cust_k6_${randomSuffix % 50}`,
    provider: 'STRIPE',
    amount: (Math.random() * 500 + 10).toFixed(2),
    currency: 'USD',
    transactionType: 'PAYMENT',
    providerTransactionId: `ch_k6_${randomSuffix}`,
    paymentMethodId: `pm_k6_${randomSuffix % 10}`,
    timestamp: new Date().toISOString(),
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/transactions`, txPayload, params);

  check(res, {
    'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    'risk score present': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.riskDecision !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.05);
}
