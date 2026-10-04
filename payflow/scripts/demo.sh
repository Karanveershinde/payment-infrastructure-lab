#!/bin/bash
set -e

BASE_URL="http://localhost:8080"
SIMULATOR_URL="http://localhost:8081"
WEBHOOK_URL="http://localhost:8082"
API_KEY="pk_test_acme_123456"

echo "================================================================"
echo " PayFlow — Live Demo & Resilience Showcase"
echo "================================================================"

echo ""
echo "[Step 1] Merchant checks profile & rate limit quota..."
curl -s -X GET "$BASE_URL/api/v1/merchants/me" \
  -H "X-API-Key: $API_KEY" | jq .

echo ""
echo "[Step 2] Creating standard payment order (Idempotency Key: idem_demo_001)..."
PAYMENT_RES=$(curl -s -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -H "Idempotency-Key: idem_demo_001" \
  -d '{
    "amount": 250.00,
    "currency": "USD",
    "paymentMethodType": "CARD_TOKEN",
    "paymentMethodToken": "tok_visa_success",
    "customerEmail": "customer@example.com",
    "captureMethod": "AUTOMATIC"
  }')

echo "$PAYMENT_RES" | jq .
PAYMENT_ID=$(echo "$PAYMENT_RES" | jq -r '.paymentId')

echo ""
echo "[Step 3] Demonstrating IDEMPOTENCY: Re-sending exact same request with idem_demo_001..."
curl -s -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -H "Idempotency-Key: idem_demo_001" \
  -d '{
    "amount": 250.00,
    "currency": "USD",
    "paymentMethodType": "CARD_TOKEN",
    "paymentMethodToken": "tok_visa_success",
    "customerEmail": "customer@example.com",
    "captureMethod": "AUTOMATIC"
  }' | jq .

echo ""
echo "[Step 4] Querying immutable state transition audit history for $PAYMENT_ID..."
curl -s -X GET "$BASE_URL/api/v1/payments/$PAYMENT_ID/events" \
  -H "X-API-Key: $API_KEY" | jq .

echo ""
echo "[Step 5] Initiating Partial Refund of $50.00 on payment $PAYMENT_ID..."
curl -s -X POST "$BASE_URL/api/v1/payments/$PAYMENT_ID/refund" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -H "Idempotency-Key: ref_demo_001" \
  -d '{
    "amount": 50.00,
    "reason": "Customer returned partial order items"
  }' | jq .

echo ""
echo "[Step 6] CHAOS TEST: Injecting 100% Outage into ProviderA..."
curl -s -X POST "$SIMULATOR_URL/api/simulator/config" \
  -H "Content-Type: application/json" \
  -d '{"provider": "PROVIDER_A", "forceOutage": true}' | jq .

echo ""
echo "[Step 7] Routing payment during ProviderA outage (verifying automatic failover to ProviderB)..."
FAILOVER_RES=$(curl -s -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -H "Idempotency-Key: idem_failover_001" \
  -d '{
    "amount": 99.00,
    "currency": "USD",
    "paymentMethodType": "CARD_TOKEN",
    "paymentMethodToken": "tok_visa_success",
    "customerEmail": "failover@example.com"
  }')
echo "$FAILOVER_RES" | jq .

echo ""
echo "[Step 8] Restoring ProviderA health status..."
curl -s -X POST "$SIMULATOR_URL/api/simulator/config" \
  -H "Content-Type: application/json" \
  -d '{"provider": "PROVIDER_A", "forceOutage": false, "successRate": 0.95}' | jq .

echo ""
echo "================================================================"
echo " Demo completed successfully!"
echo " Observability dashboard available at: http://localhost:3000"
echo " Prometheus metrics available at: http://localhost:9090"
echo " Swagger API docs available at: http://localhost:8080/swagger-ui.html"
echo "================================================================"
