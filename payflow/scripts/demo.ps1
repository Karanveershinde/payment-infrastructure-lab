$ErrorActionPreference = "Stop"

$BASE_URL = "http://localhost:8080"
$SIMULATOR_URL = "http://localhost:8081"
$WEBHOOK_URL = "http://localhost:8082"
$DEMO_IDEMPOTENCY_KEY = "idem_demo_$((Get-Date).ToString("yyyyMMddHHmmss"))"
$API_KEY = "pk_test_acme_123456"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host " PayFlow — Live Demo & Resilience Showcase" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

Write-Host "`n[Step 1] Merchant checks profile & rate limit quota..." -ForegroundColor Yellow
$merchant = Invoke-RestMethod -Uri "$BASE_URL/api/v1/merchants/me" -Method Get -Headers @{ "X-API-Key" = $API_KEY }
$merchant | ConvertTo-Json

Write-Host "`n[Step 2] Creating standard payment order (Idempotency Key: $DEMO_IDEMPOTENCY_KEY)..." -ForegroundColor Yellow
$body = @{
    amount = 250.00
    currency = "USD"
    paymentMethodType = "CARD_TOKEN"
    paymentMethodToken = "tok_visa_success"
    customerEmail = "customer@example.com"
    captureMethod = "AUTOMATIC"
} | ConvertTo-Json

$payment = Invoke-RestMethod -Uri "$BASE_URL/api/v1/payments" -Method Post -Body $body -ContentType "application/json" -Headers @{
    "X-API-Key" = $API_KEY
    "Idempotency-Key" = "$DEMO_IDEMPOTENCY_KEY"
}
$payment | ConvertTo-Json
$paymentId = $payment.paymentId

Write-Host "`n[Step 3] Demonstrating IDEMPOTENCY: Re-sending exact same request with $DEMO_IDEMPOTENCY_KEY..." -ForegroundColor Yellow
$replay = Invoke-RestMethod -Uri "$BASE_URL/api/v1/payments" -Method Post -Body $body -ContentType "application/json" -Headers @{
    "X-API-Key" = $API_KEY
    "Idempotency-Key" = "$DEMO_IDEMPOTENCY_KEY"
}
$replay | ConvertTo-Json

Write-Host "`n[Step 4] Querying immutable state transition audit history for $paymentId..." -ForegroundColor Yellow
$events = Invoke-RestMethod -Uri "$BASE_URL/api/v1/payments/$paymentId/events" -Method Get -Headers @{ "X-API-Key" = $API_KEY }
$events | ConvertTo-Json

Write-Host "`n[Step 5] Initiating Partial Refund of `$50 on payment $paymentId..." -ForegroundColor Yellow
$refundBody = @{
    amount = 50.00
    reason = "Customer returned partial order items"
} | ConvertTo-Json

$refund = Invoke-RestMethod -Uri "$BASE_URL/api/v1/payments/$paymentId/refund" -Method Post -Body $refundBody -ContentType "application/json" -Headers @{
    "X-API-Key" = $API_KEY
    "Idempotency-Key" = "ref_demo_$((Get-Date).ToString("yyyyMMddHHmmss"))"
}
$refund | ConvertTo-Json

Write-Host "`n[Step 6] CHAOS TEST: Injecting 100% Outage into ProviderA..." -ForegroundColor Yellow
$chaosBody = @{
    provider = "PROVIDER_A"
    forceOutage = $true
} | ConvertTo-Json
Invoke-RestMethod -Uri "$SIMULATOR_URL/api/simulator/config" -Method Post -Body $chaosBody -ContentType "application/json" | Out-Null
Write-Host "ProviderA Outage injected successfully." -ForegroundColor Green

Write-Host "`n[Step 7] Routing payment during ProviderA outage (verifying automatic failover to ProviderB)..." -ForegroundColor Yellow
$failoverPaymentBody = @{
    amount = 99.00
    currency = "USD"
    paymentMethodType = "CARD_TOKEN"
    paymentMethodToken = "tok_visa_success"
    customerEmail = "failover@example.com"
} | ConvertTo-Json

$failoverPayment = Invoke-RestMethod -Uri "$BASE_URL/api/v1/payments" -Method Post -Body $failoverPaymentBody -ContentType "application/json" -Headers @{
    "X-API-Key" = $API_KEY
    "Idempotency-Key" = "idem_failover_$((Get-Date).ToString("yyyyMMddHHmmss"))"
}
$failoverPayment | ConvertTo-Json
Write-Host "Payment routed to fallback provider: $($failoverPayment.provider)" -ForegroundColor Green

Write-Host "`n[Step 8] Restoring ProviderA health status..." -ForegroundColor Yellow
$restoreBody = @{
    provider = "PROVIDER_A"
    forceOutage = $false
    successRate = 0.95
} | ConvertTo-Json
Invoke-RestMethod -Uri "$SIMULATOR_URL/api/simulator/config" -Method Post -Body $restoreBody -ContentType "application/json" | Out-Null
Write-Host "ProviderA restored." -ForegroundColor Green

Write-Host "`n================================================================" -ForegroundColor Cyan
Write-Host " Demo completed successfully!" -ForegroundColor Green
Write-Host " Observability dashboard available at: http://localhost:3000" -ForegroundColor Cyan
Write-Host " Prometheus metrics available at: http://localhost:9090" -ForegroundColor Cyan
Write-Host " Swagger API docs available at: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
