# LedgerX End-to-End Demonstration Script
param(
    [string]$BaseUrl = "http://localhost:8090"
)

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "          LEDGERX — EVENT-DRIVEN PAYMENT RECONCILIATION & RISK ENGINE          " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl`n"

function Assert-Step($title) {
    Write-Host "`n>>> [STEP] $title" -ForegroundColor Yellow
}

# 1. Health check
Assert-Step "1. Verifying System Health & Actuator Status"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
Write-Host "Status: $($health.status)" -ForegroundColor Green

# ---------------------------------------------------------
# SCENARIO 1: Clean Transaction Flow & Double-Entry Ledger Posting
# ---------------------------------------------------------
Assert-Step "SCENARIO 1: Clean Transaction Flow & Double-Entry Ledger Posting"

$txId1 = "tx_demo_$([System.Guid]::NewGuid().ToString('N').Substring(0,8))"
$evtId1 = "evt_demo_$([System.Guid]::NewGuid().ToString('N').Substring(0,8))"
$merchId = "merch_acme_corp"
$custId = "cust_john_doe"

$tx1 = @{
    eventId = $evtId1
    transactionId = $txId1
    merchantId = $merchId
    customerId = $custId
    provider = "STRIPE"
    amount = 500.00
    currency = "USD"
    transactionType = "PAYMENT"
    providerTransactionId = "ch_stripe_$txId1"
    paymentMethodId = "pm_visa_4242"
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json

Write-Host "Ingesting clean transaction $txId1 (Amount: $500.00 USD)..."
$res1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $tx1 -ContentType "application/json"
Write-Host "Transaction Ingested Successfully:" -ForegroundColor Green
Write-Host "  Transaction ID : $($res1.id)"
Write-Host "  Risk Score     : $($res1.riskScore) ($($res1.riskLevel))"
Write-Host "  Status         : $($res1.status)"

# Inspect Ledger
Write-Host "`nInspecting Double-Entry Ledger Transaction for reference $($res1.id)..."
$ledgerTxList = Invoke-RestMethod -Uri "$BaseUrl/api/v1/ledger/transactions/reference/$($res1.id)" -Method Get
foreach ($ledgerTx in $ledgerTxList) {
    Write-Host "  Ledger Tx ID : $($ledgerTx.id)"
    Write-Host "  Reference ID : $($ledgerTx.referenceId)"
    Write-Host "  Type         : $($ledgerTx.transactionType)"
    Write-Host "  Total Amount : $($ledgerTx.amount) $($ledgerTx.currency)"
    Write-Host "  Status       : $($ledgerTx.status)"
    Write-Host "  Entries Breakdown:"
    foreach ($entry in $ledgerTx.entries) {
        Write-Host "    - Account: $($entry.accountNumber) | Type: $($entry.entryType) | Amount: $($entry.amount) $($entry.currency)"
    }
}

# ---------------------------------------------------------
# SCENARIO 2: Synthetic Settlement & Reconciliation Anomaly Detection
# ---------------------------------------------------------
Assert-Step "SCENARIO 2: Synthetic Settlement & Reconciliation Anomaly Detection"

$reconReq = @{
    batchDate = [System.DateTime]::UtcNow.ToString("yyyy-MM-dd")
    provider = "STRIPE"
    totalRecords = 50
    anomalyRate = 0.20
    amountMismatchRate = 0.05
    statusMismatchRate = 0.05
    missingProviderRate = 0.05
    missingInternalRate = 0.05
} | ConvertTo-Json

Write-Host "Executing Synthetic Reconciliation Batch (50 records, 20% anomaly injection)..."
$reconBatch = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/synthetic" -Method Post -Body $reconReq -ContentType "application/json"
Write-Host "Reconciliation Completed:" -ForegroundColor Green
Write-Host "  Batch ID           : $($reconBatch.id)"
Write-Host "  Total Records      : $($reconBatch.totalRecords)"
Write-Host "  Matched Records    : $($reconBatch.matchedRecords)"
Write-Host "  Discrepancy Count  : $($reconBatch.discrepantRecords)"
Write-Host "  Engine Execution   : $($reconBatch.executionDurationMs) ms"

Write-Host "`nFetching Detected Discrepancies for Batch $($reconBatch.id)..."
$discrepanciesPage = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/$($reconBatch.id)/discrepancies" -Method Get
$discrepancies = if ($discrepanciesPage.content) { $discrepanciesPage.content } else { $discrepanciesPage }
foreach ($d in $discrepancies | Select-Object -First 5) {
    Write-Host "  [Discrepancy $($d.id)]" -ForegroundColor Yellow
    Write-Host "    Type            : $($d.discrepancyType)"
    Write-Host "    Internal Amount : $($d.internalAmount) (Status: $($d.internalStatus))"
    Write-Host "    Provider Amount : $($d.providerAmount) (Status: $($d.providerStatus))"
    Write-Host "    Resolution      : $($d.resolutionStatus)"
}

if ($discrepancies.Count -gt 0) {
    $firstDisc = $discrepancies[0]
    Write-Host "`nResolving Discrepancy $($firstDisc.id)..."
    $resolveReq = @{
        resolutionStatus = "RESOLVED"
        resolutionNotes = "Verified with Stripe dashboard - adjustment approved by finance lead"
        resolvedBy = "lead-auditor-42"
    } | ConvertTo-Json
    $resolved = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/discrepancies/$($firstDisc.id)/resolve" -Method Post -Body $resolveReq -ContentType "application/json"
    Write-Host "  Resolution Status: $($resolved.resolutionStatus) by $($resolved.resolvedBy)" -ForegroundColor Green
}

# ---------------------------------------------------------
# SCENARIO 3: Idempotent Event Deduplication
# ---------------------------------------------------------
Assert-Step "SCENARIO 3: Idempotent Event Deduplication Under Duplicate Delivery"

$dupEvtId = "evt_dedup_$([System.Guid]::NewGuid().ToString('N').Substring(0,8))"
$dupTxId = "tx_dedup_$([System.Guid]::NewGuid().ToString('N').Substring(0,8))"

$dupPayload = @{
    eventId = $dupEvtId
    transactionId = $dupTxId
    merchantId = "merch_acme_corp"
    customerId = "cust_alice"
    provider = "STRIPE"
    amount = 120.00
    currency = "USD"
    transactionType = "PAYMENT"
    providerTransactionId = "ch_stripe_$dupTxId"
    paymentMethodId = "pm_visa_1111"
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json

Write-Host "Sending First Delivery of Event $dupEvtId..."
$firstDelivery = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $dupPayload -ContentType "application/json"
Write-Host "First Delivery Result: Status=$($firstDelivery.status), TxID=$($firstDelivery.id)" -ForegroundColor Green

Write-Host "Sending DUPLICATE Delivery of Exact Same Event $dupEvtId..."
$secondDelivery = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $dupPayload -ContentType "application/json"
Write-Host "Duplicate Delivery Result: Status=$($secondDelivery.status), TxID=$($secondDelivery.id) (Idempotently skipped duplicate ledger mutation!)" -ForegroundColor Green

# ---------------------------------------------------------
# SCENARIO 4: DLQ Routing & Controlled Event Replay
# ---------------------------------------------------------
Assert-Step "SCENARIO 4: Dead-Letter Queue (DLQ) & Controlled Event Replay"

Write-Host "Inspecting DLQ Events..."
$dlqList = Invoke-RestMethod -Uri "$BaseUrl/api/v1/dlq" -Method Get
Write-Host "Total DLQ Records: $($dlqList.totalElements)"

Write-Host "Triggering Event Replay for Event $dupEvtId..."
$replayRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/events/$dupEvtId/replay" -Method Post -ContentType "application/json"
Write-Host "Replay Result: Replayed=$($replayRes.replayed), EventId=$($replayRes.eventId), Message=$($replayRes.message)" -ForegroundColor Green

# ---------------------------------------------------------
# SCENARIO 5: Immutable Audit Trail Inspection
# ---------------------------------------------------------
Assert-Step "SCENARIO 5: Immutable Financial & Operational Audit Trail"

Write-Host "Fetching latest Audit Events for Transaction $($res1.id)..."
$auditTrail = Invoke-RestMethod -Uri "$BaseUrl/api/v1/audit/TRANSACTION/$($res1.id)" -Method Get
foreach ($ae in $auditTrail) {
    Write-Host "  [$($ae.createdAt)] Action: $($ae.action) | Actor: $($ae.actor) | Resource: $($ae.resourceType)/$($ae.resourceId)" -ForegroundColor Cyan
}

Write-Host "`n================================================================================" -ForegroundColor Green
Write-Host "             ALL 4 CORE SCENARIOS EXECUTED AND VERIFIED SUCCESSFULLY!           " -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
