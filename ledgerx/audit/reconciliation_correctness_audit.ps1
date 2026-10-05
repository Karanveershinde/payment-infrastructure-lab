# Phase 2E: Reconciliation Correctness Audit
param(
    [string]$BaseUrl = "http://localhost:8090"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       PHASE 2E: RECONCILIATION CORRECTNESS AUDIT         " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$testId = [System.Guid]::NewGuid().ToString('N').Substring(0, 6)
$merchantId = "merch_recon_${testId}"

Write-Host "`n1. Ingesting baseline internal transactions..." -ForegroundColor Yellow

# Ingest Baseline Tx 1: Will Match
$txMatch = "tx_match_${testId}"
$req1 = @{
    eventId = "evt_m_${testId}"
    transactionId = $txMatch
    merchantId = $merchantId
    customerId = "cust_rec_1"
    provider = "STRIPE"
    amount = 100.00
    currency = "USD"
    transactionType = "PAYMENT"
    providerTransactionId = "ch_match_${testId}"
    paymentMethodToken = "pm_recon"
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $req1 -ContentType "application/json" | Out-Null

# Ingest Baseline Tx 2: Amount Mismatch (Internal $200.00 vs Prov $175.00)
$txAmt = "tx_amt_${testId}"
$req2 = @{
    eventId = "evt_a_${testId}"
    transactionId = $txAmt
    merchantId = $merchantId
    customerId = "cust_rec_2"
    provider = "STRIPE"
    amount = 200.00
    currency = "USD"
    transactionType = "PAYMENT"
    providerTransactionId = "ch_amt_${testId}"
    paymentMethodToken = "pm_recon"
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $req2 -ContentType "application/json" | Out-Null

# Ingest Baseline Tx 3: Currency Mismatch (Internal USD vs Prov EUR)
$txCurr = "tx_curr_${testId}"
$req3 = @{
    eventId = "evt_c_${testId}"
    transactionId = $txCurr
    merchantId = $merchantId
    customerId = "cust_rec_3"
    provider = "STRIPE"
    amount = 300.00
    currency = "USD"
    transactionType = "PAYMENT"
    providerTransactionId = "ch_curr_${testId}"
    paymentMethodToken = "pm_recon"
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $req3 -ContentType "application/json" | Out-Null

# Ingest Baseline Tx 4: Status Mismatch (Internal POSTED vs Prov FAILED)
$txStat = "tx_stat_${testId}"
$req4 = @{
    eventId = "evt_s_${testId}"
    transactionId = $txStat
    merchantId = $merchantId
    customerId = "cust_rec_4"
    provider = "STRIPE"
    amount = 400.00
    currency = "USD"
    transactionType = "PAYMENT"
    providerTransactionId = "ch_stat_${testId}"
    paymentMethodToken = "pm_recon"
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $req4 -ContentType "application/json" | Out-Null

# Ingest Baseline Tx 5: Missing Provider (Internal exists, but provider never includes it in settlement report)
$txMissingProv = "tx_missprov_${testId}"
$req5 = @{
    eventId = "evt_mp_${testId}"
    transactionId = $txMissingProv
    merchantId = $merchantId
    customerId = "cust_rec_5"
    provider = "STRIPE"
    amount = 550.00
    currency = "USD"
    transactionType = "PAYMENT"
    providerTransactionId = "ch_missprov_${testId}"
    paymentMethodToken = "pm_recon"
    timestamp = [System.DateTime]::UtcNow.ToString("o")
} | ConvertTo-Json -Compress
Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions" -Method Post -Body $req5 -ContentType "application/json" | Out-Null

Write-Host "   Ingested 5 internal transactions."

Write-Host "`n2. Executing provider settlement batch reconciliation..." -ForegroundColor Yellow

$reconPayload = @{
    provider = "STRIPE"
    batchDate = [System.DateTime]::UtcNow.ToString("yyyy-MM-dd")
    settlementRecords = @(
        # 1. Matched Record
        @{
            providerTransactionId = "ch_match_${testId}"
            internalTransactionId = $txMatch
            amount = 100.00
            currency = "USD"
            status = "SUCCESS"
            settlementTimestamp = [System.DateTime]::UtcNow.ToString("o")
        },
        # 2. Amount Mismatch
        @{
            providerTransactionId = "ch_amt_${testId}"
            internalTransactionId = $txAmt
            amount = 175.00 # Mismatch
            currency = "USD"
            status = "SUCCESS"
            settlementTimestamp = [System.DateTime]::UtcNow.ToString("o")
        },
        # 3. Currency Mismatch
        @{
            providerTransactionId = "ch_curr_${testId}"
            internalTransactionId = $txCurr
            amount = 300.00
            currency = "EUR" # Mismatch
            status = "SUCCESS"
            settlementTimestamp = [System.DateTime]::UtcNow.ToString("o")
        },
        # 4. Status Mismatch
        @{
            providerTransactionId = "ch_stat_${testId}"
            internalTransactionId = $txStat
            amount = 400.00
            currency = "USD"
            status = "FAILED" # Mismatch
            settlementTimestamp = [System.DateTime]::UtcNow.ToString("o")
        },
        # 5. Missing Internal (Settled at provider but no internal order)
        @{
            providerTransactionId = "ch_phantom_${testId}"
            internalTransactionId = $null
            amount = 600.00
            currency = "USD"
            status = "SUCCESS"
            settlementTimestamp = [System.DateTime]::UtcNow.ToString("o")
        },
        # 6. Duplicate Provider (Duplicate line item for ch_match)
        @{
            providerTransactionId = "ch_match_${testId}"
            internalTransactionId = $txMatch
            amount = 100.00
            currency = "USD"
            status = "SUCCESS"
            settlementTimestamp = [System.DateTime]::UtcNow.ToString("o")
        }
    )
} | ConvertTo-Json -Depth 5

$reconBatch = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/run" -Method Post -Body $reconPayload -ContentType "application/json"

Write-Host "   Reconciliation Batch ID : $($reconBatch.id)"
Write-Host "   Total Records Processed : $($reconBatch.totalRecords)"
Write-Host "   Matched Count           : $($reconBatch.matchedRecords)"
Write-Host "   Discrepancies Detected  : $($reconBatch.discrepantRecords)"
Write-Host "   Engine Execution Time   : $($reconBatch.executionDurationMs) ms"

Write-Host "`n3. Inspecting detected discrepancy records..." -ForegroundColor Yellow
$discrepancies = (Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/$($reconBatch.id)/discrepancies" -Method Get).content

$detectedTypes = @{}
foreach ($d in $discrepancies) {
    Write-Host "   - Discrepancy Type: $($d.discrepancyType)"
    Write-Host "     Internal Tx: $($d.transactionId), Provider Tx: $($d.providerTransactionId)"
    Write-Host "     Internal Amt: $($d.internalAmount), Provider Amt: $($d.providerAmount)"
    Write-Host "     Notes: $($d.resolutionNotes)"
    $detectedTypes[$d.discrepancyType] = $true
}

# Check if Missing Provider tx was detected
$txMissingStatus = (Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/$txMissingProv" -Method Get).reconciliationStatus
Write-Host "`n4. Checking if un-settled internal transaction (MISSING_PROVIDER) was flagged..." -ForegroundColor Yellow
Write-Host "   Internal Tx $txMissingProv reconciliationStatus: $txMissingStatus"

Write-Host "`n================ RECONCILIATION AUDIT SUMMARY ================" -ForegroundColor Green
Write-Host "MATCHED Record              : $($reconBatch.matchedRecords -eq 1)"
Write-Host "AMOUNT_MISMATCH Detected    : $($detectedTypes.ContainsKey('AMOUNT_MISMATCH'))"
Write-Host "CURRENCY_MISMATCH Detected  : $($detectedTypes.ContainsKey('CURRENCY_MISMATCH'))"
Write-Host "STATUS_MISMATCH Detected    : $($detectedTypes.ContainsKey('STATUS_MISMATCH'))"
Write-Host "MISSING_INTERNAL Detected   : $($detectedTypes.ContainsKey('MISSING_INTERNAL'))"
Write-Host "DUPLICATE_PROVIDER Detected : $($detectedTypes.ContainsKey('DUPLICATE_PROVIDER'))"
Write-Host "MISSING_PROVIDER Detected   : $($detectedTypes.ContainsKey('MISSING_PROVIDER') -or $txMissingStatus -eq 'MISSING_PROVIDER')"
Write-Host "LATE_SETTLEMENT Detected    : $($detectedTypes.ContainsKey('LATE_SETTLEMENT'))"
Write-Host "==============================================================" -ForegroundColor Green
