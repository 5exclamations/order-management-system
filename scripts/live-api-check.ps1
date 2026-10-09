$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$script:pass = 0; $script:fail = 0

function Call($method, $path, $token, $body, $idem) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    if ($idem)  { $h['Idempotency-Key'] = $idem }
    $p = @{ Uri = "$base$path"; Method = $method; Headers = $h; UseBasicParsing = $true; ContentType = 'application/json' }
    if ($null -ne $body) { $p['Body'] = ($body | ConvertTo-Json -Depth 6) }
    try { $r = Invoke-WebRequest @p; $code = [int]$r.StatusCode; $txt = $r.Content }
    catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        $code = [int]$resp.StatusCode
        $txt = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
    }
    $json = $null; if ($txt) { try { $json = $txt | ConvertFrom-Json } catch {} }
    [pscustomobject]@{ Code = $code; Json = $json; Raw = $txt }
}
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; "PASS  $name" } else { $script:fail++; "FAIL  $name  -> $detail" }
}
function Login($u, $p) { (Call POST '/api/v1/auth/login' $null @{ username = $u; password = $p }) }
function Stock($tok, $prodId) { (Call GET "/api/v1/inventory/$prodId" $tok).Json }

"== AUTH & SEED =="
$bad = Login 'admin@oms.local' 'wrong-password'
Check 'login with wrong password rejected' ($bad.Code -in 400,401) $bad.Code
$a = Login 'admin@oms.local' 'DevPassw0rd!'
Check 'seeded admin login 200, role ADMIN' ($a.Code -eq 200 -and $a.Json.role -eq 'ADMIN') $a.Raw
$s = Login 'staff@oms.local' 'DevPassw0rd!'
Check 'seeded staff login 200, role STAFF' ($s.Code -eq 200 -and $s.Json.role -eq 'STAFF') $s.Raw
$admin = $a.Json.accessToken; $staff = $s.Json.accessToken

$email = "cust$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
$reg = Call POST '/api/v1/auth/register' $null @{ email = $email; password = 'Passw0rd!123'; name = 'Live Test'; phone = '555-0100' }
Check 'customer register 201' ($reg.Code -eq 201 -and $reg.Json.role -eq 'CUSTOMER') $reg.Raw
$cust = $reg.Json.accessToken

"== RBAC =="
Check 'no token -> 401' ((Call GET '/api/v1/orders' $null $null).Code -eq 401) ''
Check 'customer on staff-only inventory -> 403' ((Call GET '/api/v1/inventory' $cust $null).Code -eq 403) ''
Check 'customer on admin-only audit logs -> 403' ((Call GET '/api/v1/audit-logs' $cust $null).Code -eq 403) ''
Check 'staff on admin-only audit logs -> 403' ((Call GET '/api/v1/audit-logs' $staff $null).Code -eq 403) ''
Check 'admin on audit logs -> 200' ((Call GET '/api/v1/audit-logs' $admin $null).Code -eq 200) ''
Check 'garbage JWT -> 401' ((Call GET '/api/v1/orders' 'not.a.jwt' $null).Code -eq 401) ''

"== CATALOG =="
$prods = (Call GET '/api/v1/products?size=50' $staff $null).Json
$list = if ($prods.content) { $prods.content } else { $prods.items }
$kb = $list | Where-Object sku -eq 'KEYBOARD-01'
$mon = $list | Where-Object sku -eq 'MONITOR-01'
Check 'seeded products present' ($kb -and $mon -and ($list.Count -ge 3)) ($prods | ConvertTo-Json -Depth 3)
"inventory(KEYBOARD) before: " + ((Stock $staff $kb.id) | ConvertTo-Json -Compress)

"== ORDER A: create -> replay -> pay -> ship -> deliver =="
$idemA = [guid]::NewGuid().ToString()
$bodyA = @{ items = @(@{ productId = $kb.id; quantity = 2 }) }
$oa = Call POST '/api/v1/orders' $cust $bodyA $idemA
Check 'order created 201, PENDING' ($oa.Code -eq 201 -and $oa.Json.status -eq 'PENDING') $oa.Raw
$idA = $oa.Json.id
"total: $($oa.Json.total)"
Check 'order total = 2 x 89.90' ([decimal]$oa.Json.total -eq 179.80) $oa.Json.total
$ra = Call POST '/api/v1/orders' $cust $bodyA $idemA
Check 'idempotent replay 200, same order id' ($ra.Code -eq 200 -and $ra.Json.id -eq $idA) $ra.Raw
$diff = Call POST '/api/v1/orders' $cust @{ items = @(@{ productId = $kb.id; quantity = 3 }) } $idemA
Check 'same key + different body rejected (4xx)' ($diff.Code -ge 400 -and $diff.Code -lt 500) $diff.Raw
$st = Stock $staff $kb.id
"inventory(KEYBOARD) after reserve: " + ($st | ConvertTo-Json -Compress)
Check 'no OTHER customer can read the order (404/403)' ((Call GET "/api/v1/orders/$idA" ((Call POST '/api/v1/auth/register' $null @{ email = "other$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"; password = 'Passw0rd!123'; name = 'Other' }).Json.accessToken) $null).Code -in 403,404) ''
Check 'ship before pay rejected (409)' ((Call POST "/api/v1/orders/$idA/ship" $staff $null).Code -eq 409) ''
$pay = Call POST "/api/v1/orders/$idA/payments" $cust @{ paymentMethodToken = 'tok_visa' } ([guid]::NewGuid().ToString())
Check 'payment 201 approved' ($pay.Code -eq 201 -and $pay.Json.status -in 'SUCCEEDED','APPROVED','CAPTURED','PAID') $pay.Raw
"payment status: $($pay.Json.status)"
Check 'order now PAID' ((Call GET "/api/v1/orders/$idA" $cust $null).Json.status -eq 'PAID') ''
Check 'customer cannot ship (403)' ((Call POST "/api/v1/orders/$idA/ship" $cust $null).Code -eq 403) ''
$sh = Call POST "/api/v1/orders/$idA/ship" $staff $null
Check 'staff ship -> SHIPPED' ($sh.Code -eq 200 -and $sh.Json.status -eq 'SHIPPED') $sh.Raw
"inventory(KEYBOARD) after ship: " + ((Stock $staff $kb.id) | ConvertTo-Json -Compress)
$dl = Call POST "/api/v1/orders/$idA/deliver" $staff $null
Check 'staff deliver -> DELIVERED' ($dl.Code -eq 200 -and $dl.Json.status -eq 'DELIVERED') $dl.Raw
Check 'cancel DELIVERED order rejected (409)' ((Call POST "/api/v1/orders/$idA/cancel" $cust @{ reason = 'too late' }).Code -eq 409) ''

"== ORDER A: staff partial refund (async via Kafka) =="
$idemR = [guid]::NewGuid().ToString()
$rf = Call POST "/api/v1/orders/$idA/refunds" $staff @{ amount = 50.00; reason = 'partial goodwill' } $idemR
Check 'refund request 202' ($rf.Code -eq 202) $rf.Raw
Check 'customer cannot request refund (403)' ((Call POST "/api/v1/orders/$idA/refunds" $cust @{ amount = 1.00 } ([guid]::NewGuid().ToString())).Code -eq 403) ''
Check 'refund replay 200' ((Call POST "/api/v1/orders/$idA/refunds" $staff @{ amount = 50.00; reason = 'partial goodwill' } $idemR).Code -eq 200) ''
$ok = $false
for ($i = 0; $i -lt 20; $i++) {
    $rl = (Call GET "/api/v1/orders/$idA/refunds" $staff $null).Json
    if ($rl -and ($rl | Select-Object -First 1).status -notin 'PENDING') { $ok = $true; break }
    Start-Sleep 1
}
"refunds: " + ($rl | ConvertTo-Json -Compress -Depth 3)
Check 'refund settled asynchronously (not PENDING)' $ok ($rl | ConvertTo-Json -Compress)
Check 'refund SUCCEEDED/COMPLETED' (($rl | Select-Object -First 1).status -in 'SUCCEEDED','COMPLETED','REFUNDED','PROCESSED') (($rl | Select-Object -First 1).status)
$over = Call POST "/api/v1/orders/$idA/refunds" $staff @{ amount = 500.00 } ([guid]::NewGuid().ToString())
Check 'over-refund rejected (4xx)' ($over.Code -ge 400 -and $over.Code -lt 500) $over.Raw

"== ORDER B: pay -> cancel -> automatic refund + stock release =="
$before = Stock $staff $kb.id
$ob = Call POST '/api/v1/orders' $cust @{ items = @(@{ productId = $kb.id; quantity = 1 }) } ([guid]::NewGuid().ToString())
$idB = $ob.Json.id
Check 'order B created' ($ob.Code -eq 201) $ob.Raw
Call POST "/api/v1/orders/$idB/payments" $cust @{ paymentMethodToken = 'tok_mastercard' } ([guid]::NewGuid().ToString()) | Out-Null
$cb = Call POST "/api/v1/orders/$idB/cancel" $cust @{ reason = 'changed my mind' }
Check 'cancel PAID order 200' ($cb.Code -eq 200) $cb.Raw
"order B status after cancel: $($cb.Json.status)"
$ok = $false
for ($i = 0; $i -lt 20; $i++) {
    $rl = (Call GET "/api/v1/orders/$idB/refunds" $staff $null).Json
    if ($rl -and ($rl | Select-Object -First 1).status -ne 'PENDING') { $ok = $true; break }
    Start-Sleep 1
}
"order B refunds: " + ($rl | ConvertTo-Json -Compress -Depth 3)
Check 'automatic refund created and settled' $ok ($rl | ConvertTo-Json -Compress)
"final order B: " + ((Call GET "/api/v1/orders/$idB" $cust $null).Json.status)
"inventory(KEYBOARD) after B cancel: " + ((Stock $staff $kb.id) | ConvertTo-Json -Compress)

"== ORDER C: cancel PENDING releases reservation =="
$oc = Call POST '/api/v1/orders' $cust @{ items = @(@{ productId = $kb.id; quantity = 3 }) } ([guid]::NewGuid().ToString())
$mid = Stock $staff $kb.id
Call POST "/api/v1/orders/$($oc.Json.id)/cancel" $cust @{ reason = 'oops' } | Out-Null
$after = Stock $staff $kb.id
"KEYBOARD with C reserved: $($mid | ConvertTo-Json -Compress)"
"KEYBOARD after C cancel : $($after | ConvertTo-Json -Compress)"
Check 'C cancelled' ((Call GET "/api/v1/orders/$($oc.Json.id)" $cust $null).Json.status -eq 'CANCELLED') ''

"== FAILURE PATHS =="
$big = Call POST '/api/v1/orders' $cust @{ items = @(@{ productId = $mon.id; quantity = 6 }) } ([guid]::NewGuid().ToString())
Check 'insufficient stock -> 409' ($big.Code -eq 409) $big.Raw
"error body: $($big.Raw)"
$od = Call POST '/api/v1/orders' $cust @{ items = @(@{ productId = $mon.id; quantity = 1 }) } ([guid]::NewGuid().ToString())
$pd = Call POST "/api/v1/orders/$($od.Json.id)/payments" $cust @{ paymentMethodToken = 'tok_declined' } ([guid]::NewGuid().ToString())
Check 'declined payment 201 FAILED' ($pd.Code -eq 201 -and $pd.Json.status -eq 'FAILED') $pd.Raw
Check 'order stays PENDING after decline' ((Call GET "/api/v1/orders/$($od.Json.id)" $cust $null).Json.status -eq 'PENDING') ''
$noKey = Call POST '/api/v1/orders' $cust @{ items = @(@{ productId = $mon.id; quantity = 1 }) } $null
Check 'missing Idempotency-Key -> 400' ($noKey.Code -eq 400) $noKey.Raw
$inv = Call POST '/api/v1/orders' $cust @{ items = @() } ([guid]::NewGuid().ToString())
Check 'empty items -> 400' ($inv.Code -eq 400) $inv.Raw
Call POST "/api/v1/orders/$($od.Json.id)/cancel" $cust @{ reason = 'cleanup' } | Out-Null

"== ACTUATOR =="
$act = Call GET '/actuator/env' $null $null
Check 'actuator/env not publicly exposed' ($act.Code -in 401,403,404) $act.Code

"`nRESULT: $script:pass passed, $script:fail failed"
