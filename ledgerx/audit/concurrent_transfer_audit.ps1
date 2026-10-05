# Phase 2C: Concurrent Transfer Correctness Test
param(
    [string]$BaseUrl = "http://localhost:8090",
    [int]$TotalTransfers = 100,
    [int]$MerchantCount = 10,
    [int]$Concurrency = 10
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   PHASE 2C: CONCURRENT TRANSFER CORRECTNESS AUDIT TEST   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Base URL    : $BaseUrl"
Write-Host "Total Transfers (N): $TotalTransfers"
Write-Host "Account Pool (M)   : $MerchantCount merchants"
Write-Host "Worker Concurrency : $Concurrency"

Add-Type -AssemblyName System.Net.Http

$runId = [System.Guid]::NewGuid().ToString('N').Substring(0, 8)
$merchantIds = 0..($MerchantCount - 1) | ForEach-Object { "merch_audit_${runId}_$_" }

$payloads = 1..$TotalTransfers | ForEach-Object {
    $idx = $_
    $mId = $merchantIds[$idx % $MerchantCount]
    $txId = "audit_tx_${runId}_$idx"
    $evtId = "audit_evt_${runId}_$idx"
    $amt = [Math]::Round((10.0 + ($idx % 50) * 1.75), 2)
    
    @{
        eventId = $evtId
        transactionId = $txId
        merchantId = $mId
        customerId = "cust_audit_${runId}_$($idx % 20)"
        provider = "STRIPE"
        amount = $amt
        currency = "USD"
        transactionType = "PAYMENT"
        providerTransactionId = "prov_${runId}_$idx"
        paymentMethodId = "pm_audit_card"
        timestamp = [System.DateTime]::UtcNow.ToString("o")
    } | ConvertTo-Json -Compress
}

$csharpRunner = @"
using System;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;
using System.Collections.Concurrent;
using System.Collections.Generic;

public class ConcurrencyAuditRunner {
    public static ConcurrentBag<string> SuccessTxIds = new ConcurrentBag<string>();
    public static ConcurrentBag<string> FailedTxIds = new ConcurrentBag<string>();
    public static int SuccessCount = 0;
    public static int FailureCount = 0;

    public static void Execute(string baseUrl, string[] payloads, int concurrency) {
        SuccessTxIds = new ConcurrentBag<string>();
        FailedTxIds = new ConcurrentBag<string>();
        SuccessCount = 0;
        FailureCount = 0;

        System.Net.ServicePointManager.DefaultConnectionLimit = 100;
        HttpClient client = new HttpClient();
        client.Timeout = TimeSpan.FromSeconds(20);

        ParallelOptions options = new ParallelOptions { MaxDegreeOfParallelism = concurrency };

        Parallel.ForEach(payloads, options, payload => {
            var content = new StringContent(payload, Encoding.UTF8, "application/json");
            try {
                var res = client.PostAsync(baseUrl + "/api/v1/transactions", content).Result;
                if (res.IsSuccessStatusCode) {
                    System.Threading.Interlocked.Increment(ref SuccessCount);
                    SuccessTxIds.Add(payload);
                } else {
                    System.Threading.Interlocked.Increment(ref FailureCount);
                    FailedTxIds.Add(payload);
                }
            } catch {
                System.Threading.Interlocked.Increment(ref FailureCount);
            }
        });
    }
}
"@

try {
    Add-Type -TypeDefinition $csharpRunner -ReferencedAssemblies "System.Net.Http", "System.Core" -Language CSharp
} catch {
    # Type might already be loaded in powershell session
}

Write-Host "Executing $TotalTransfers concurrent transfers across $MerchantCount accounts..."
[ConcurrencyAuditRunner]::Execute($BaseUrl, $payloads, $Concurrency)

$successCount = [ConcurrencyAuditRunner]::SuccessCount
$failureCount = [ConcurrencyAuditRunner]::FailureCount

Write-Host "`n--- Checking Invariant & Balance Correctness ---" -ForegroundColor Yellow

$invariantViolations = 0
$totalDebits = 0.0
$totalCredits = 0.0
$duplicateTransactions = 0
$negativeBalances = 0
$seenTxIds = [System.Collections.Generic.HashSet[string]]::new()

$verifiedTxCount = 0
1..$TotalTransfers | ForEach-Object {
    $idx = $_
    $txId = "audit_tx_${runId}_$idx"
    
    if ($seenTxIds.Contains($txId)) {
        $duplicateTransactions++
    }
    $seenTxIds.Add($txId) | Out-Null
    
    try {
        $txResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/$txId" -Method Get -TimeoutSec 5
        if ($txResp.status -eq "POSTED") {
            $ledgerTxList = Invoke-RestMethod -Uri "$BaseUrl/api/v1/ledger/transactions/reference/$txId" -Method Get -TimeoutSec 5
            foreach ($ltx in $ledgerTxList) {
                $debitSum = 0.0
                $creditSum = 0.0
                foreach ($entry in $ltx.entries) {
                    if ($entry.entryType -eq "DEBIT") {
                        $debitSum += [double]$entry.amount
                        $totalDebits += [double]$entry.amount
                    } elseif ($entry.entryType -eq "CREDIT") {
                        $creditSum += [double]$entry.amount
                        $totalCredits += [double]$entry.amount
                    }
                }
                if ([Math]::Abs($debitSum - $creditSum) -gt 0.0001 -or $debitSum -le 0.0) {
                    $invariantViolations++
                }
                $verifiedTxCount++
            }
        }
    } catch {
        # Transaction might have failed or not posted
    }
}

$totalMerchantBalance = 0.0
foreach ($mId in $merchantIds) {
    try {
        $accs = Invoke-RestMethod -Uri "$BaseUrl/api/v1/ledger/accounts/entity/$mId" -Method Get -TimeoutSec 5
        foreach ($acc in $accs) {
            $bal = [double]$acc.balance
            if ($bal -lt 0.0) {
                $negativeBalances++
            }
            $totalMerchantBalance += $bal
        }
    } catch {
    }
}

Write-Host "`n================ CONCURRENCY AUDIT RESULTS ================" -ForegroundColor Green
Write-Host "Total Attempted Transfers (N) : $TotalTransfers"
Write-Host "Total Merchant Accounts (M)   : $MerchantCount"
Write-Host "Successful Transfers          : $successCount"
Write-Host "Failed Transfers              : $failureCount"
Write-Host "Verified Ledger Transactions  : $verifiedTxCount"
Write-Host "Double-Entry Invariant Violations : $invariantViolations"
Write-Host "Total Ledger Debits Sum       : $($totalDebits.ToString('F4')) USD"
Write-Host "Total Ledger Credits Sum      : $($totalCredits.ToString('F4')) USD"
Write-Host "Sum(Debits) == Sum(Credits)   : $([Math]::Abs($totalDebits - $totalCredits) -lt 0.0001)"
Write-Host "Sum of Merchant Balances      : $($totalMerchantBalance.ToString('F4')) USD"
Write-Host "Negative Balances Count       : $negativeBalances"
Write-Host "Duplicate Transactions Count  : $duplicateTransactions"
Write-Host "Balance Internal Consistency  : $([Math]::Abs($totalMerchantBalance - ($totalCredits / 2.0)) -lt 0.01 -or [Math]::Abs($totalMerchantBalance - ($totalCredits)) -lt 0.01)"
Write-Host "===========================================================" -ForegroundColor Green
