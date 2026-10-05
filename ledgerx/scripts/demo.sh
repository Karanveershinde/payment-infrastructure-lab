#!/usr/bin/env bash
set -e

BASE_URL="${1:-http://localhost:8090}"

echo "================================================================================"
echo "          LEDGERX — EVENT-DRIVEN PAYMENT RECONCILIATION & RISK ENGINE          "
echo "================================================================================"
echo "Target Base URL: $BASE_URL"

echo -e "\n>>> [STEP 1] Verifying System Health..."
curl -s -f "$BASE_URL/actuator/health" | grep -q "UP" && echo "LedgerX is healthy and UP"

echo -e "\n>>> [STEP 2] Ingesting Transaction & Double-Entry Ledger Posting..."
TX_ID="tx_demo_$(date +%s)"
EVT_ID="evt_demo_$(date +%s)"

curl -s -X POST "$BASE_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d "{
    \"eventId\": \"$EVT_ID\",
    \"transactionId\": \"$TX_ID\",
    \"merchantId\": \"merch_acme_corp\",
    \"customerId\": \"cust_john_doe\",
    \"provider\": \"STRIPE\",
    \"amount\": 500.00,
    \"currency\": \"USD\",
    \"transactionType\": \"PAYMENT\",
    \"providerTransactionId\": \"ch_stripe_$TX_ID\",
    \"paymentMethodId\": \"pm_visa_4242\",
    \"timestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\"
  }" | jq .

echo -e "\n>>> [STEP 3] Running Synthetic Reconciliation Batch with Anomaly Detection..."
curl -s -X POST "$BASE_URL/api/v1/reconciliation/run-synthetic" \
  -H "Content-Type: application/json" \
  -d "{
    \"batchDate\": \"$(date +%Y-%m-%d)\",
    \"provider\": \"STRIPE\",
    \"totalRecords\": 50,
    \"anomalyRate\": 0.20,
    \"amountMismatchRate\": 0.05,
    \"statusMismatchRate\": 0.05,
    \"missingProviderRate\": 0.05,
    \"missingInternalRate\": 0.05
  }" | jq .

echo -e "\n>>> [STEP 4] Deduplication Verification (Sending duplicate event)..."
curl -s -X POST "$BASE_URL/api/v1/transactions" \
  -H "Content-Type: application/json" \
  -d "{
    \"eventId\": \"$EVT_ID\",
    \"transactionId\": \"$TX_ID\",
    \"merchantId\": \"merch_acme_corp\",
    \"customerId\": \"cust_john_doe\",
    \"provider\": \"STRIPE\",
    \"amount\": 500.00,
    \"currency\": \"USD\",
    \"transactionType\": \"PAYMENT\",
    \"providerTransactionId\": \"ch_stripe_$TX_ID\",
    \"paymentMethodId\": \"pm_visa_4242\",
    \"timestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\"
  }" | jq .

echo -e "\n>>> [STEP 5] Replaying Event..."
curl -s -X POST "$BASE_URL/api/v1/events/$EVT_ID/replay" \
  -H "Content-Type: application/json" \
  -d "{
    \"eventId\": \"$EVT_ID\",
    \"replayedBy\": \"ops-bash-admin\",
    \"reason\": \"Verification of replay idempotency\"
  }" | jq .

echo -e "\n================================================================================"
echo "                        ALL DEMO SCENARIOS COMPLETED!                           "
echo "================================================================================"
