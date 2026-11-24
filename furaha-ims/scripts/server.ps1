# ==============================================================================
# FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)
# Built-in Standalone Live Server Engine (.NET / PowerShell)
# Furaha Mini Mart · STU 3101 · KCA University
# ==============================================================================

param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$rootDir = (Get-Item $PSScriptRoot).Parent.FullName
$publicDir = Join-Path $rootDir "src\main\resources\public"
$dbFile = Join-Path $rootDir "furaha_db_state.json"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "  FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)" -ForegroundColor Green
Write-Host "  Furaha Mini Mart · KCA University · STU 3101" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Root Directory: $rootDir" -ForegroundColor Gray
Write-Host "Public Web UI:  $publicDir" -ForegroundColor Gray

# ------------------------------------------------------------------------------
# 1. DATABASE STATE INITIALIZATION
# ------------------------------------------------------------------------------
function Init-Database {
    if (Test-Path $dbFile) {
        try {
            $raw = Get-Content $dbFile -Raw -Encoding UTF8
            return ($raw | ConvertFrom-Json)
        } catch {
            Write-Warning "Corrupt state file. Initializing fresh seed database..."
        }
    }

    $now = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $db = [ordered]@{
        roles = @(
            [ordered]@{ role_id = 1; role_name = "ADMINISTRATOR"; description = "System Administrator / Shop Owner" },
            [ordered]@{ role_id = 2; role_name = "ATTENDANT"; description = "Cashier / Shop Attendant" }
        )
        users = @(
            [ordered]@{ user_id = 1; role_id = 1; role_name = "ADMINISTRATOR"; username = "admin"; password_hash = "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9"; display_name = "Mitsanze Kazungu (Admin)"; is_active = $true; created_at = $now; last_login_at = $null },
            [ordered]@{ user_id = 2; role_id = 2; role_name = "ATTENDANT"; username = "amina"; password_hash = "9a7852c035c91b5c3e031e0303fe20f2db9d251d2fbe4f6050b11e2f790c6fa7"; display_name = "Amina Mwangi (Attendant)"; is_active = $true; created_at = $now; last_login_at = $null },
            [ordered]@{ user_id = 3; role_id = 2; role_name = "ATTENDANT"; username = "john"; password_hash = "129b09f448c5b0c4cf1b9cf3832ca87e3831ecad3474a2dfbfab043c85fdf294"; display_name = "John Ochieng (Attendant)"; is_active = $true; created_at = $now; last_login_at = $null }
        )
        units = @(
            [ordered]@{ unit_id = 1; unit_name = "Piece / Unit"; abbreviation = "pcs" },
            [ordered]@{ unit_id = 2; unit_name = "Kilogram"; abbreviation = "kg" },
            [ordered]@{ unit_id = 3; unit_name = "Litre"; abbreviation = "ltr" },
            [ordered]@{ unit_id = 4; unit_name = "Packet"; abbreviation = "pkt" },
            [ordered]@{ unit_id = 5; unit_name = "Box"; abbreviation = "box" }
        )
        suppliers = @(
            [ordered]@{ supplier_id = 1; supplier_name = "Highland Dairies Kenya"; phone = "+254 712 000 001"; email = "orders@highland.co.ke"; is_active = $true },
            [ordered]@{ supplier_id = 2; supplier_name = "Harvest Foods & Millers Ltd"; phone = "+254 722 000 002"; email = "sales@harvest.co.ke"; is_active = $true },
            [ordered]@{ supplier_id = 3; supplier_name = "Premier FMCG Distributors"; phone = "+254 733 000 003"; email = "supplies@premier.co.ke"; is_active = $true },
            [ordered]@{ supplier_id = 4; supplier_name = "Brookside Dairy Ltd"; phone = "+254 700 000 004"; email = "info@brookside.co.ke"; is_active = $true }
        )
        products = @(
            [ordered]@{ product_id = 1; sku = "FMM-1001"; product_name = "Brookside Fresh Milk 500ml"; category = "Dairy & Eggs"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 4; supplier_name = "Brookside Dairy Ltd"; cost_price = 52.00; selling_price = 65.00; quantity_on_hand = 4.0; reorder_level = 15.0; lead_time_days = 2; safety_stock = 5.0; is_active = $true; is_low_stock = $true },
            [ordered]@{ product_id = 2; sku = "FMM-1002"; product_name = "Jogoo Maize Meal 2kg"; category = "Grains & Flour"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 2; supplier_name = "Harvest Foods & Millers Ltd"; cost_price = 180.00; selling_price = 210.00; quantity_on_hand = 28.0; reorder_level = 12.0; lead_time_days = 4; safety_stock = 6.0; is_active = $true; is_low_stock = $false },
            [ordered]@{ product_id = 3; sku = "FMM-1003"; product_name = "Kabras Pure White Sugar 1kg"; category = "Groceries"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 2; supplier_name = "Harvest Foods & Millers Ltd"; cost_price = 142.00; selling_price = 165.00; quantity_on_hand = 18.0; reorder_level = 15.0; lead_time_days = 3; safety_stock = 5.0; is_active = $true; is_low_stock = $false },
            [ordered]@{ product_id = 4; sku = "FMM-1004"; product_name = "Rina Vegetable Cooking Oil 1L"; category = "Oils & Fats"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 3; supplier_name = "Premier FMCG Distributors"; cost_price = 260.00; selling_price = 310.00; quantity_on_hand = 9.0; reorder_level = 10.0; lead_time_days = 3; safety_stock = 4.0; is_active = $true; is_low_stock = $true },
            [ordered]@{ product_id = 5; sku = "FMM-1005"; product_name = "Broadways White Bread 400g"; category = "Bakery"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 2; supplier_name = "Harvest Foods & Millers Ltd"; cost_price = 55.00; selling_price = 65.00; quantity_on_hand = 3.0; reorder_level = 12.0; lead_time_days = 1; safety_stock = 4.0; is_active = $true; is_low_stock = $true },
            [ordered]@{ product_id = 6; sku = "FMM-1006"; product_name = "Daawat Super Basmati Rice 2kg"; category = "Grains & Flour"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 2; supplier_name = "Harvest Foods & Millers Ltd"; cost_price = 380.00; selling_price = 450.00; quantity_on_hand = 22.0; reorder_level = 8.0; lead_time_days = 5; safety_stock = 3.0; is_active = $true; is_low_stock = $false },
            [ordered]@{ product_id = 7; sku = "FMM-1007"; product_name = "Ketepa Pride Tea Bags 50s"; category = "Beverages"; unit_id = 4; unit_abbr = "pkt"; preferred_supplier_id = 3; supplier_name = "Premier FMCG Distributors"; cost_price = 115.00; selling_price = 140.00; quantity_on_hand = 14.0; reorder_level = 8.0; lead_time_days = 4; safety_stock = 2.0; is_active = $true; is_low_stock = $false },
            [ordered]@{ product_id = 8; sku = "FMM-1008"; product_name = "Geisha Beauty Soap Aloe Vera 225g"; category = "Toiletries"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 3; supplier_name = "Premier FMCG Distributors"; cost_price = 90.00; selling_price = 115.00; quantity_on_hand = 2.0; reorder_level = 10.0; lead_time_days = 4; safety_stock = 3.0; is_active = $true; is_low_stock = $true },
            [ordered]@{ product_id = 9; sku = "FMM-1009"; product_name = "Omo Hand Washing Powder 500g"; category = "Household"; unit_id = 4; unit_abbr = "pkt"; preferred_supplier_id = 3; supplier_name = "Premier FMCG Distributors"; cost_price = 155.00; selling_price = 185.00; quantity_on_hand = 16.0; reorder_level = 8.0; lead_time_days = 4; safety_stock = 3.0; is_active = $true; is_low_stock = $false },
            [ordered]@{ product_id = 10; sku = "FMM-1010"; product_name = "Highland Natural Yoghurt 500ml"; category = "Dairy & Eggs"; unit_id = 1; unit_abbr = "pcs"; preferred_supplier_id = 1; supplier_name = "Highland Dairies Kenya"; cost_price = 85.00; selling_price = 110.00; quantity_on_hand = 7.0; reorder_level = 10.0; lead_time_days = 2; safety_stock = 4.0; is_active = $true; is_low_stock = $true }
        )
        customers = @(
            [ordered]@{ customer_id = 1; customer_name = "Walk-in Customer (General)"; phone = $null; credit_limit = 0.00; balance = 0.00; is_active = $true },
            [ordered]@{ customer_id = 2; customer_name = "Mama Wanjiru (Local Resident)"; phone = "+254 722 123 456"; credit_limit = 5000.00; balance = 1850.00; is_active = $true },
            [ordered]@{ customer_id = 3; customer_name = "Bwana Suleiman Said"; phone = "+254 733 654 321"; credit_limit = 10000.00; balance = 3200.00; is_active = $true },
            [ordered]@{ customer_id = 4; customer_name = "Celestine Achieng"; phone = "+254 711 987 654"; credit_limit = 3000.00; balance = 450.00; is_active = $true }
        )
        sales = @(
            [ordered]@{ sale_id = 1; receipt_no = "RCP-20261122-1042"; sold_at = $now; status = "COMPLETED"; subtotal = 420.00; total = 420.00; cashier_id = 2; cashier_name = "Amina Mwangi (Attendant)"; customer_id = 1; customer_name = "Walk-in Customer"; payment_method = "CASH"; amount_tendered = 500.00; change_due = 80.00; reference = $null }
        )
        sale_items = @(
            [ordered]@{ sale_item_id = 1; sale_id = 1; product_id = 2; product_name = "Jogoo Maize Meal 2kg"; quantity = 2.0; unit_selling_price = 210.00; unit_cost_at_sale = 180.00; line_total = 420.00 }
        )
        stock_counts = @(
            [ordered]@{ count_id = 1; created_by = 2; created_by_name = "Amina Mwangi (Attendant)"; counted_at = $now; status = "SUBMITTED"; notes = "Morning routine stock take"; total_items = 2; total_variance = -2.0 }
        )
        stock_adjustments = @(
            [ordered]@{ adjustment_id = 1; count_item_id = 1; product_id = 1; sku = "FMM-1001"; product_name = "Brookside Fresh Milk 500ml"; quantity_delta = -2.0; reason = "Carton seal leak discovered during shelf restocking"; status = "PENDING"; proposed_by = 2; proposed_by_name = "Amina Mwangi (Attendant)"; proposed_at = $now; approved_by = $null; approved_by_name = $null; decided_at = $null }
        )
        stock_movements = @(
            [ordered]@{ movement_id = 1; product_id = 1; sku = "FMM-1001"; product_name = "Brookside Fresh Milk 500ml"; movement_type = "OPENING"; quantity_delta = 6.0; reference = "INIT-STOCK"; actor_name = "Mitsanze Kazungu (Admin)"; occurred_at = $now },
            [ordered]@{ movement_id = 2; product_id = 2; sku = "FMM-1002"; product_name = "Jogoo Maize Meal 2kg"; movement_type = "OPENING"; quantity_delta = 30.0; reference = "INIT-STOCK"; actor_name = "Mitsanze Kazungu (Admin)"; occurred_at = $now },
            [ordered]@{ movement_id = 3; product_id = 2; sku = "FMM-1002"; product_name = "Jogoo Maize Meal 2kg"; movement_type = "SALE"; quantity_delta = -2.0; reference = "RCP-20261122-1042"; actor_name = "Amina Mwangi (Attendant)"; occurred_at = $now }
        )
        customer_ledger_entries = @(
            [ordered]@{ entry_id = 1; customer_id = 2; sale_id = $null; entry_type = "CREDIT_SALE"; signed_amount = 2500.00; payment_method = "CREDIT"; reference = "RCP-PREV-01"; created_by = 1; created_by_name = "Admin"; occurred_at = $now },
            [ordered]@{ entry_id = 2; customer_id = 2; sale_id = $null; entry_type = "REPAYMENT"; signed_amount = -650.00; payment_method = "MPESA"; reference = "QK781290AA"; created_by = 2; created_by_name = "Amina"; occurred_at = $now }
        )
        audit_logs = @(
            [ordered]@{ audit_id = 1; user_id = 1; user_name = "Mitsanze Kazungu (Admin)"; action = "SYSTEM_BOOT"; entity_type = "SYSTEM"; entity_id = "ALL"; reason = "Furaha Mini Mart ISMS live server online"; occurred_at = $now }
        )
    }

    $db | ConvertTo-Json -Depth 10 | Set-Content $dbFile -Encoding UTF8
    return $db
}

$Global:DB = Init-Database

function Save-Database {
    $Global:DB | ConvertTo-Json -Depth 10 | Set-Content $dbFile -Encoding UTF8
}

function Get-Sha256([string]$text) {
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $hash = $hasher.ComputeHash($bytes)
    return [System.BitConverter]::ToString($hash).Replace("-", "").ToLower()
}

# Active In-Memory Sessions (Token -> User)
$Global:Sessions = @{}

# ------------------------------------------------------------------------------
# 2. HTTP LISTENER SETUP
# ------------------------------------------------------------------------------
$listener = New-Object System.Net.HttpListener
$prefix = "http://localhost:$Port/"
$listener.Prefixes.Add($prefix)
$listener.Start()

Write-Host ">>> Furaha ISMS Server is RUNNING at $prefix" -ForegroundColor Green
Write-Host ">>> Log in as Admin:     admin / admin123" -ForegroundColor Yellow
Write-Host ">>> Log in as Attendant: amina / attendant123" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop the server." -ForegroundColor Gray
Write-Host "----------------------------------------------------------------------"

# ------------------------------------------------------------------------------
# 3. REQUEST DISPATCH LOOP
# ------------------------------------------------------------------------------
while ($listener.IsListening) {
    try {
        $context = $listener.GetContext()
        $req = $context.Request
        $res = $context.Response

        $res.Headers.Add("Access-Control-Allow-Origin", "*")
        $res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        $res.Headers.Add("Access-Control-Allow-Headers", "Content-Type, Authorization")

        if ($req.HttpMethod -eq "OPTIONS") {
            $res.StatusCode = 204
            $res.Close()
            continue
        }

        $path = $req.Url.AbsolutePath
        $method = $req.HttpMethod

        # Read JSON body if present
        $bodyJson = $null
        if ($req.HasEntityBody) {
            $reader = New-Object System.IO.StreamReader($req.InputStream, [System.Text.Encoding]::UTF8)
            $rawBody = $reader.ReadToEnd()
            if ($rawBody) {
                try { $bodyJson = $rawBody | ConvertFrom-Json } catch {}
            }
        }

        # Auth Token Verification
        $user = $null
        $authHeader = $req.Headers["Authorization"]
        if ($authHeader -and $authHeader.StartsWith("Bearer ")) {
            $token = $authHeader.Substring(7).Trim()
            if ($Global:Sessions.ContainsKey($token)) {
                $user = $Global:Sessions[$token]
            }
        }

        # Route Handler
        $statusCode = 200
        $contentType = "application/json; charset=utf-8"
        $responseBytes = $null

        # --- A. STATIC WEB ASSETS ---
        if (-not $path.StartsWith("/api")) {
            $rel = $path.TrimStart('/')
            if (-not $rel -or $rel -eq "" -or $rel.Contains("..")) { $rel = "index.html" }
            $filePath = Join-Path $publicDir $rel

            if (Test-Path $filePath -PathType Leaf) {
                if ($rel.EndsWith(".css")) { $contentType = "text/css; charset=utf-8" }
                elseif ($rel.EndsWith(".js")) { $contentType = "application/javascript; charset=utf-8" }
                elseif ($rel.EndsWith(".html")) { $contentType = "text/html; charset=utf-8" }
                elseif ($rel.EndsWith(".svg")) { $contentType = "image/svg+xml" }
                elseif ($rel.EndsWith(".png")) { $contentType = "image/png" }

                $responseBytes = [System.IO.File]::ReadAllBytes($filePath)
            } else {
                $statusCode = 404
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes("File Not Found: $rel")
                $contentType = "text/plain"
            }
        }

        # --- B. REST API ENDPOINTS ---
        elseif ($path -eq "/api/login" -and $method -eq "POST") {
            $username = $bodyJson.username
            $password = $bodyJson.password
            $pHash = Get-Sha256 $password

            $matched = $Global:DB.users | Where-Object { $_.username -eq $username -and $_.password_hash -eq $pHash -and $_.is_active }

            if ($matched) {
                $token = [System.Guid]::NewGuid().ToString()
                $Global:Sessions[$token] = $matched
                $matched.last_login_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                Save-Database

                $out = [ordered]@{
                    token = $token
                    userId = $matched.user_id
                    role = $matched.role_name
                    username = $matched.username
                    displayName = $matched.display_name
                } | ConvertTo-Json -Compress
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
            } else {
                $statusCode = 401
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"error":"Sign-in failed. Check your details or ask the Administrator."}')
            }
        }
        elseif ($path -eq "/api/logout" -and $method -eq "POST") {
            if ($token -and $Global:Sessions.ContainsKey($token)) {
                $Global:Sessions.Remove($token)
            }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
        }
        elseif ($path -eq "/api/products") {
            if ($method -eq "GET") {
                # Update low_stock flags
                foreach ($p in $Global:DB.products) {
                    $p.is_low_stock = ($p.quantity_on_hand -le $p.reorder_level)
                }
                $out = $Global:DB.products | ConvertTo-Json -Depth 5 -Compress
                if (-not $out) { $out = "[]" }
                if ($out -notmatch '^\[') { $out = "[$out]" }
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
            }
            elseif ($method -eq "POST") {
                if ($user.role_name -ne "ADMINISTRATOR") {
                    $statusCode = 403
                    $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"error":"Access denied. Administrator privileges required."}')
                } else {
                    $newId = ($Global:DB.products | Measure-Object -Property product_id -Maximum).Maximum + 1
                    $unit = $Global:DB.units | Where-Object { $_.unit_id -eq ($bodyJson.unitId -as [int]) }
                    $supp = $Global:DB.suppliers | Where-Object { $_.supplier_id -eq ($bodyJson.preferredSupplierId -as [int]) }

                    $newProd = [ordered]@{
                        product_id = $newId
                        sku = $bodyJson.sku
                        product_name = $bodyJson.productName
                        category = $bodyJson.category
                        unit_id = [int]$bodyJson.unitId
                        unit_abbr = if ($unit) { $unit.abbreviation } else { "pcs" }
                        preferred_supplier_id = [int]$bodyJson.preferredSupplierId
                        supplier_name = if ($supp) { $supp.supplier_name } else { "None" }
                        cost_price = [double]$bodyJson.costPrice
                        selling_price = [double]$bodyJson.sellingPrice
                        quantity_on_hand = 0.0
                        reorder_level = [double]$bodyJson.reorderLevel
                        lead_time_days = [int]$bodyJson.leadTimeDays
                        safety_stock = 0.0
                        is_active = $true
                        is_low_stock = $true
                    }
                    $Global:DB.products += $newProd
                    Save-Database
                    $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
                }
            }
            elseif ($method -eq "PUT") {
                $pid = [int]$bodyJson.productId
                $prod = $Global:DB.products | Where-Object { $_.product_id -eq $pid }
                if ($prod) {
                    $prod.product_name = $bodyJson.productName
                    $prod.category = $bodyJson.category
                    $prod.cost_price = [double]$bodyJson.costPrice
                    $prod.selling_price = [double]$bodyJson.sellingPrice
                    $prod.reorder_level = [double]$bodyJson.reorderLevel
                    $prod.lead_time_days = [int]$bodyJson.leadTimeDays
                    Save-Database
                    $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
                }
            }
        }
        elseif ($path -eq "/api/suppliers") {
            $out = $Global:DB.suppliers | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/units") {
            $out = $Global:DB.units | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/customers") {
            if ($method -eq "GET") {
                $out = $Global:DB.customers | ConvertTo-Json -Depth 5 -Compress
                if (-not $out) { $out = "[]" }
                if ($out -notmatch '^\[') { $out = "[$out]" }
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
            } elseif ($method -eq "POST") {
                $newId = ($Global:DB.customers | Measure-Object -Property customer_id -Maximum).Maximum + 1
                $newCust = [ordered]@{
                    customer_id = $newId
                    customer_name = $bodyJson.customerName
                    phone = $bodyJson.phone
                    email = $bodyJson.email
                    credit_limit = [double]$bodyJson.creditLimit
                    balance = 0.00
                    is_active = $true
                }
                $Global:DB.customers += $newCust
                Save-Database
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
            }
        }
        elseif ($path -eq "/api/customers/repayment" -and $method -eq "POST") {
            $cid = [int]$bodyJson.customerId
            $amt = [double]$bodyJson.amount
            $cust = $Global:DB.customers | Where-Object { $_.customer_id -eq $cid }
            if ($cust) {
                $cust.balance = [Math]::Max(0, ($cust.balance - $amt))
                $newLedId = ($Global:DB.customer_ledger_entries | Measure-Object -Property entry_id -Maximum).Maximum + 1
                $Global:DB.customer_ledger_entries += [ordered]@{
                    entry_id = $newLedId
                    customer_id = $cid
                    sale_id = $null
                    entry_type = "REPAYMENT"
                    signed_amount = (-$amt)
                    payment_method = $bodyJson.method
                    reference = $bodyJson.reference
                    created_by = if ($user) { $user.user_id } else { 1 }
                    created_by_name = if ($user) { $user.display_name } else { "Staff" }
                    occurred_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                }
                Save-Database
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
            }
        }
        elseif ($path -eq "/api/customers/statement") {
            $cid = 1
            if ($req.Url.Query -match "customerId=(\d+)") { $cid = [int]$matches[1] }
            $cust = $Global:DB.customers | Where-Object { $_.customer_id -eq $cid }
            $entries = @($Global:DB.customer_ledger_entries | Where-Object { $_.customer_id -eq $cid })

            $stmt = [ordered]@{
                customerName = if ($cust) { $cust.customer_name } else { "Customer" }
                phone = if ($cust) { $cust.phone } else { "" }
                creditLimit = if ($cust) { $cust.credit_limit } else { 0 }
                currentBalance = if ($cust) { $cust.balance } else { 0 }
                entries = ($entries | ConvertTo-Json -Depth 5 -Compress)
            } | ConvertTo-Json -Compress
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($stmt)
        }
        elseif ($path -eq "/api/sales") {
            if ($method -eq "GET") {
                if ($req.QueryString["saleId"]) {
                    $sId = [int]$req.QueryString["saleId"]
                    $sale = $Global:DB.sales | Where-Object { $_.sale_id -eq $sId }
                    if ($sale) {
                        $sItems = @($Global:DB.sale_items | Where-Object { $_.sale_id -eq $sId } | ForEach-Object {
                            [ordered]@{
                                name = $_.product_name
                                quantity = $_.quantity
                                price = $_.unit_selling_price
                                lineTotal = $_.line_total
                            }
                        })
                        $recObj = [ordered]@{
                            saleId = $sale.sale_id
                            receiptNo = $sale.receipt_no
                            date = $sale.sold_at
                            cashier = $sale.cashier_name
                            customerName = $sale.customer_name
                            total = $sale.total
                            subtotal = $sale.subtotal
                            method = $sale.payment_method
                            tendered = $sale.amount_tendered
                            change = $sale.change_due
                            reference = $sale.reference
                            items = $sItems
                        } | ConvertTo-Json -Depth 5 -Compress
                        $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($recObj)
                    } else {
                        $statusCode = 404
                        $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"error":"Sale not found"}')
                    }
                } else {
                    $out = $Global:DB.sales | ConvertTo-Json -Depth 5 -Compress
                    if (-not $out) { $out = "[]" }
                    if ($out -notmatch '^\[') { $out = "[$out]" }
                    $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
                }
            }
            elseif ($method -eq "POST") {
                # ATOMIC POS CHECKOUT
                $receiptNo = "RCP-" + (Get-Date).ToString("yyyyMMdd") + "-" + (Get-Random -Minimum 1000 -Maximum 9999)
                $saleId = ($Global:DB.sales | Measure-Object -Property sale_id -Maximum).Maximum + 1

                $calcTotal = 0.0
                $receiptLines = @()

                foreach ($item in $bodyJson.items) {
                    $pid = [int]$item.productId
                    $qty = [double]$item.quantity
                    $prod = $Global:DB.products | Where-Object { $_.product_id -eq $pid }
                    if (-not $prod) { throw "Product not found: $pid" }
                    if ($prod.quantity_on_hand -lt $qty) {
                        throw "Insufficient stock for $($prod.product_name) (Available: $($prod.quantity_on_hand))"
                    }

                    $lineTotal = [Math]::Round(($prod.selling_price * $qty), 2)
                    $calcTotal += $lineTotal

                    # Deduct inventory
                    $prod.quantity_on_hand = [Math]::Max(0, ($prod.quantity_on_hand - $qty))

                    # Immutable Movement
                    $movId = ($Global:DB.stock_movements | Measure-Object -Property movement_id -Maximum).Maximum + 1
                    $Global:DB.stock_movements += [ordered]@{
                        movement_id = $movId
                        product_id = $pid
                        sku = $prod.sku
                        product_name = $prod.product_name
                        movement_type = "SALE"
                        quantity_delta = (-$qty)
                        reference = $receiptNo
                        actor_name = if ($user) { $user.display_name } else { "Cashier" }
                        occurred_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                    }

                    # Sale item
                    $sItemId = ($Global:DB.sale_items | Measure-Object -Property sale_item_id -Maximum).Maximum + 1
                    $Global:DB.sale_items += [ordered]@{
                        sale_item_id = $sItemId
                        sale_id = $saleId
                        product_id = $pid
                        product_name = $prod.product_name
                        quantity = $qty
                        unit_selling_price = $prod.selling_price
                        unit_cost_at_sale = $prod.cost_price
                        line_total = $lineTotal
                    }

                    $receiptLines += [ordered]@{
                        name = $prod.product_name
                        quantity = $qty
                        price = $prod.selling_price
                        lineTotal = $lineTotal
                    }
                }

                $method = $bodyJson.method
                $tendered = [double]$bodyJson.amountTendered
                if ($method -eq "CASH" -and $tendered -lt $calcTotal) {
                    throw "Cash tendered cannot be less than total due."
                }
                $change = if ($method -eq "CASH") { [Math]::Max(0, ($tendered - $calcTotal)) } else { 0.0 }

                # Customer Credit Handling
                $custId = [int]$bodyJson.customerId
                $cust = $Global:DB.customers | Where-Object { $_.customer_id -eq $custId }
                $custName = if ($cust) { $cust.customer_name } else { "Walk-in Customer" }

                if ($method -eq "CREDIT") {
                    if (-not $cust) { throw "Customer account required for credit sale." }
                    if (($cust.balance + $calcTotal) -gt $cust.credit_limit) {
                        throw "Credit limit exceeded for $($cust.customer_name) (Limit: $($cust.credit_limit), Current: $($cust.balance))"
                    }
                    $cust.balance += $calcTotal
                    $ledId = ($Global:DB.customer_ledger_entries | Measure-Object -Property entry_id -Maximum).Maximum + 1
                    $Global:DB.customer_ledger_entries += [ordered]@{
                        entry_id = $ledId
                        customer_id = $custId
                        sale_id = $saleId
                        entry_type = "CREDIT_SALE"
                        signed_amount = $calcTotal
                        payment_method = "CREDIT"
                        reference = $receiptNo
                        created_by = if ($user) { $user.user_id } else { 1 }
                        created_by_name = if ($user) { $user.display_name } else { "Cashier" }
                        occurred_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                    }
                }

                # Sales Header
                $Global:DB.sales += [ordered]@{
                    sale_id = $saleId
                    receipt_no = $receiptNo
                    sold_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                    status = "COMPLETED"
                    subtotal = $calcTotal
                    total = $calcTotal
                    cashier_id = if ($user) { $user.user_id } else { 2 }
                    cashier_name = if ($user) { $user.display_name } else { "Amina Mwangi (Attendant)" }
                    customer_id = $custId
                    customer_name = $custName
                    payment_method = $method
                    amount_tendered = $tendered
                    change_due = $change
                    reference = $bodyJson.reference
                }

                # Audit Log
                $audId = ($Global:DB.audit_logs | Measure-Object -Property audit_id -Maximum).Maximum + 1
                $Global:DB.audit_logs += [ordered]@{
                    audit_id = $audId
                    user_id = if ($user) { $user.user_id } else { 2 }
                    user_name = if ($user) { $user.display_name } else { "Cashier" }
                    action = "SALE_COMPLETED"
                    entity_type = "SALES"
                    entity_id = "$saleId"
                    reason = "Completed sale $receiptNo for KES $calcTotal"
                    occurred_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                }

                Save-Database

                $receipt = [ordered]@{
                    saleId = $saleId
                    receiptNo = $receiptNo
                    date = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                    cashier = if ($user) { $user.display_name } else { "Amina Mwangi" }
                    customerName = $custName
                    total = $calcTotal
                    subtotal = $calcTotal
                    method = $method
                    tendered = $tendered
                    change = $change
                    reference = $bodyJson.reference
                    items = $receiptLines
                } | ConvertTo-Json -Depth 5 -Compress

                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($receipt)
            }
        }
        elseif ($path -eq "/api/stock/counts") {
            if ($method -eq "GET") {
                $out = $Global:DB.stock_counts | ConvertTo-Json -Depth 5 -Compress
                if (-not $out) { $out = "[]" }
                if ($out -notmatch '^\[') { $out = "[$out]" }
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
            } elseif ($method -eq "POST") {
                $newCId = ($Global:DB.stock_counts | Measure-Object -Property count_id -Maximum).Maximum + 1
                $netVar = 0.0
                foreach ($it in $bodyJson.items) {
                    $pid = [int]$it.productId
                    $counted = [double]$it.countedQty
                    $prod = $Global:DB.products | Where-Object { $_.product_id -eq $pid }
                    $variance = $counted - $prod.quantity_on_hand
                    $netVar += $variance

                    if ($variance -ne 0) {
                        $newAId = ($Global:DB.stock_adjustments | Measure-Object -Property adjustment_id -Maximum).Maximum + 1
                        $Global:DB.stock_adjustments += [ordered]@{
                            adjustment_id = $newAId
                            count_item_id = $newCId
                            product_id = $pid
                            sku = $prod.sku
                            product_name = $prod.product_name
                            quantity_delta = $variance
                            reason = $it.reason
                            status = "PENDING"
                            proposed_by = if ($user) { $user.user_id } else { 2 }
                            proposed_by_name = if ($user) { $user.display_name } else { "Amina Mwangi" }
                            proposed_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                            approved_by = $null
                            approved_by_name = $null
                            decided_at = $null
                        }
                    }
                }
                $Global:DB.stock_counts += [ordered]@{
                    count_id = $newCId
                    created_by = if ($user) { $user.user_id } else { 2 }
                    created_by_name = if ($user) { $user.display_name } else { "Amina Mwangi" }
                    counted_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                    status = "SUBMITTED"
                    notes = $bodyJson.notes
                    total_items = $bodyJson.items.Count
                    total_variance = $netVar
                }
                Save-Database
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
            }
        }
        elseif ($path -eq "/api/stock/adjustments") {
            if ($method -eq "GET") {
                $out = $Global:DB.stock_adjustments | ConvertTo-Json -Depth 5 -Compress
                if (-not $out) { $out = "[]" }
                if ($out -notmatch '^\[') { $out = "[$out]" }
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
            } elseif ($method -eq "PUT") {
                $aid = [int]$bodyJson.adjustmentId
                $decision = $bodyJson.decision
                $adj = $Global:DB.stock_adjustments | Where-Object { $_.adjustment_id -eq $aid }
                if ($adj) {
                    $adj.status = if ($decision -eq "APPROVE") { "APPROVED" } else { "REJECTED" }
                    $adj.approved_by = if ($user) { $user.user_id } else { 1 }
                    $adj.approved_by_name = if ($user) { $user.display_name } else { "Admin" }
                    $adj.decided_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")

                    if ($decision -eq "APPROVE") {
                        $prod = $Global:DB.products | Where-Object { $_.product_id -eq $adj.product_id }
                        if ($prod) {
                            $prod.quantity_on_hand += $adj.quantity_delta
                            $mId = ($Global:DB.stock_movements | Measure-Object -Property movement_id -Maximum).Maximum + 1
                            $Global:DB.stock_movements += [ordered]@{
                                movement_id = $mId
                                product_id = $prod.product_id
                                sku = $prod.sku
                                product_name = $prod.product_name
                                movement_type = "ADJUSTMENT"
                                quantity_delta = $adj.quantity_delta
                                reference = "ADJ-" + $aid + ": " + $adj.reason
                                actor_name = if ($user) { $user.display_name } else { "Admin" }
                                occurred_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                            }
                        }
                    }
                    Save-Database
                    $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
                }
            }
        }
        elseif ($path -eq "/api/stock/receive" -and $method -eq "POST") {
            $pid = [int]$bodyJson.productId
            $qty = [double]$bodyJson.quantity
            $cost = [double]$bodyJson.unitCost
            $prod = $Global:DB.products | Where-Object { $_.product_id -eq $pid }
            if ($prod) {
                $prod.quantity_on_hand += $qty
                $prod.cost_price = $cost
                $mId = ($Global:DB.stock_movements | Measure-Object -Property movement_id -Maximum).Maximum + 1
                $Global:DB.stock_movements += [ordered]@{
                    movement_id = $mId
                    product_id = $prod.product_id
                    sku = $prod.sku
                    product_name = $prod.product_name
                    movement_type = "RECEIPT"
                    quantity_delta = $qty
                    reference = "GRN: $($bodyJson.invoiceNo)"
                    actor_name = if ($user) { $user.display_name } else { "Admin" }
                    occurred_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                }
                Save-Database
                $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"ok":true}')
            }
        }
        elseif ($path -eq "/api/stock/movements") {
            $out = $Global:DB.stock_movements | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/replenishment") {
            $list = @()
            foreach ($p in $Global:DB.products) {
                # Sum 30d units sold
                $sold = ($Global:DB.sale_items | Where-Object { $_.product_id -eq $p.product_id } | Measure-Object -Property quantity -Sum).Sum
                if (-not $sold) { $sold = 0.0 }
                $avgDaily = [Math]::Round(($sold / 30.0), 2)
                $lead = if ($p.lead_time_days) { $p.lead_time_days } else { 3 }
                $safety = if ($p.safety_stock) { $p.safety_stock } else { 0.0 }
                $needed = ($avgDaily * $lead) + $safety - $p.quantity_on_hand
                $sug = [Math]::Max(0, [int][Math]::Ceiling($needed))

                $list += [ordered]@{
                    productId = $p.product_id
                    sku = $p.sku
                    productName = $p.product_name
                    quantityOnHand = $p.quantity_on_hand
                    reorderLevel = $p.reorder_level
                    unitsSold30d = $sold
                    avgDailySales = $avgDaily
                    leadTimeDays = $lead
                    safetyStock = $safety
                    suggestedQuantity = $sug
                    isLowStock = ($p.quantity_on_hand -le $p.reorder_level)
                }
            }
            $out = $list | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/reports/summary") {
            $todaySales = ($Global:DB.sales | Measure-Object -Property total -Sum).Sum
            $todayRec = $Global:DB.sales.Count
            $lowCount = ($Global:DB.products | Where-Object { $_.quantity_on_hand -le $_.reorder_level }).Count
            $pendAdj = ($Global:DB.stock_adjustments | Where-Object { $_.status -eq "PENDING" }).Count
            $creditOut = ($Global:DB.customers | Measure-Object -Property balance -Sum).Sum
            $stockCost = ($Global:DB.products | ForEach-Object { $_.quantity_on_hand * $_.cost_price } | Measure-Object -Sum).Sum
            $stockRet = ($Global:DB.products | ForEach-Object { $_.quantity_on_hand * $_.selling_price } | Measure-Object -Sum).Sum

            $sumObj = @([ordered]@{
                sales_today = [double]$todaySales
                receipts_today = [int]$todayRec
                low_stock_count = [int]$lowCount
                pending_adjustments_count = [int]$pendAdj
                credit_outstanding = [double]$creditOut
                stock_cost_value = [double]$stockCost
                stock_retail_value = [double]$stockRet
            })
            $out = $sumObj | ConvertTo-Json -Depth 5 -Compress
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/reports/profit") {
            $profitList = @()
            foreach ($p in $Global:DB.products) {
                $items = @($Global:DB.sale_items | Where-Object { $_.product_id -eq $p.product_id })
                if ($items.Count -gt 0) {
                    $uSold = ($items | Measure-Object -Property quantity -Sum).Sum
                    $rev = ($items | Measure-Object -Property line_total -Sum).Sum
                    $cogs = ($items | ForEach-Object { $_.quantity * $_.unit_cost_at_sale } | Measure-Object -Sum).Sum
                    $profit = $rev - $cogs
                    $margin = if ($rev -gt 0) { [Math]::Round((($profit / $rev) * 100), 2) } else { 0.0 }

                    $profitList += [ordered]@{
                        sku = $p.sku
                        product_name = $p.product_name
                        category = $p.category
                        total_units_sold = $uSold
                        total_revenue = $rev
                        total_cogs = $cogs
                        gross_profit = $profit
                        profit_margin_pct = $margin
                    }
                }
            }
            $out = $profitList | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/reports/sales") {
            $salesRep = @([ordered]@{
                sale_date = (Get-Date).ToString("yyyy-MM-dd")
                total_sales_count = $Global:DB.sales.Count
                total_revenue = ($Global:DB.sales | Measure-Object -Property total -Sum).Sum
                cash_revenue = ($Global:DB.sales | Where-Object { $_.payment_method -eq "CASH" } | Measure-Object -Property total -Sum).Sum
                mpesa_revenue = ($Global:DB.sales | Where-Object { $_.payment_method -eq "MOBILE_MONEY" } | Measure-Object -Property total -Sum).Sum
                credit_revenue = ($Global:DB.sales | Where-Object { $_.payment_method -eq "CREDIT" } | Measure-Object -Property total -Sum).Sum
            })
            $out = $salesRep | ConvertTo-Json -Depth 5 -Compress
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/reports/valuation") {
            $cats = $Global:DB.products | Group-Object category
            $valList = @()
            foreach ($g in $cats) {
                $pCount = $g.Group.Count
                $totUnits = ($g.Group | Measure-Object -Property quantity_on_hand -Sum).Sum
                $cVal = ($g.Group | ForEach-Object { $_.quantity_on_hand * $_.cost_price } | Measure-Object -Sum).Sum
                $rVal = ($g.Group | ForEach-Object { $_.quantity_on_hand * $_.selling_price } | Measure-Object -Sum).Sum
                $valList += [ordered]@{
                    category = $g.Name
                    product_count = $pCount
                    total_units = $totUnits
                    inventory_cost_value = $cVal
                    inventory_retail_value = $rVal
                    potential_profit = ($rVal - $cVal)
                }
            }
            $out = $valList | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/reports/export") {
            $csv = "Receipt No,Date,Cashier,Customer,Method,Total`n"
            foreach ($s in $Global:DB.sales) {
                $csv += "$($s.receipt_no),$($s.sold_at),`"$($s.cashier_name)`",`"$($s.customer_name)`",$($s.payment_method),$($s.total)`n"
            }
            $contentType = "text/csv; charset=utf-8"
            $res.Headers.Add("Content-Disposition", 'attachment; filename="furaha_sales_export.csv"')
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($csv)
        }
        elseif ($path -eq "/api/users") {
            $out = $Global:DB.users | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/audit") {
            $out = $Global:DB.audit_logs | ConvertTo-Json -Depth 5 -Compress
            if (-not $out) { $out = "[]" }
            if ($out -notmatch '^\[') { $out = "[$out]" }
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($out)
        }
        elseif ($path -eq "/api/backup") {
            $sql = "-- Furaha Mini Mart ISMS Database Backup Export`n-- Date: $(Get-Date)`nUSE furaha_ims;`n"
            $contentType = "application/sql; charset=utf-8"
            $res.Headers.Add("Content-Disposition", 'attachment; filename="furaha_backup.sql"')
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($sql)
        }
        else {
            $statusCode = 404
            $responseBytes = [System.Text.Encoding]::UTF8.GetBytes('{"error":"Not Found"}')
        }

        # Send response
        $res.StatusCode = $statusCode
        $res.ContentType = $contentType
        $res.ContentLength64 = $responseBytes.Length
        $res.OutputStream.Write($responseBytes, 0, $responseBytes.Length)
        $res.OutputStream.Close()
    }
    catch {
        Write-Warning "Request processing error: $_"
    }
}
