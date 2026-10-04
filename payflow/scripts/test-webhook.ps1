$secret = "whsec_test_secret_key_1234567890"
$body = '{"eventId":"evt_wh_live_001","eventType":"CHARGE_SUCCEEDED","paymentId":"pay_e32aee248b77436c","provider":"PROVIDER_A","providerTransactionId":"txn_wh_001","timestamp":1712234000,"amount":250.00,"currency":"USD","payload":{"status":"CAPTURED"}}'

$hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($secret))
$hashBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($body))
$sig = [System.BitConverter]::ToString($hashBytes).Replace("-","").ToLower()

Write-Host "Sending HMAC-SHA256 Webhook with Sig: $sig"
$headers = @{ "X-PayFlow-Signature" = $sig }
$res = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/webhooks/providera" -Method Post -Body $body -ContentType "application/json" -Headers $headers
$res | ConvertTo-Json

Write-Host "`nRe-sending Duplicate Webhook (Expect DUPLICATE_IGNORED):"
$dupRes = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/webhooks/providera" -Method Post -Body $body -ContentType "application/json" -Headers $headers
$dupRes | ConvertTo-Json
