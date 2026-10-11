param(
  [string]$BaseUrl = 'http://localhost:8080',
  [string]$DockerExecutable = 'docker'
)

$ErrorActionPreference = 'Stop'
$script:checkCount = 0
$projectDirectory = Split-Path -Parent $PSScriptRoot

if (-not (Get-Command $DockerExecutable -ErrorAction SilentlyContinue)) {
  $perUserDocker = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'
  if (Test-Path -LiteralPath $perUserDocker) {
    $DockerExecutable = $perUserDocker
  } else {
    throw 'Docker CLI was not found. Supply -DockerExecutable with its full path.'
  }
}

function Assert-Check([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
  $script:checkCount++
}

function Invoke-Api([string]$Method, [string]$Path, $Body, [int]$ExpectedStatus = 200) {
  $request = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    UseBasicParsing = $true
  }
  if ($null -ne $Body) {
    $request.ContentType = 'application/json'
    $request.Body = $Body | ConvertTo-Json -Depth 5
  }
  try {
    $response = Invoke-WebRequest @request
    $status = [int]$response.StatusCode
  } catch {
    if ($null -eq $_.Exception.Response) { throw }
    $status = [int]$_.Exception.Response.StatusCode
    Assert-Check ($status -eq $ExpectedStatus) "$Method $Path returned $status; expected $ExpectedStatus"
    return
  }
  Assert-Check ($status -eq $ExpectedStatus) "$Method $Path returned $status; expected $ExpectedStatus"
  if ($response.Content) { return $response.Content | ConvertFrom-Json }
}

function Read-Database([string]$Query) {
  $result = & $DockerExecutable compose --project-directory $projectDirectory exec -T database `
    psql -v ON_ERROR_STOP=1 -U assignment -d assignment -Atc $Query
  if ($LASTEXITCODE -ne 0) { throw 'Database verification query failed' }
  return ($result -join "`n").Trim()
}

$prefix = 'VERIFY-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$store = $null
$product = $null
$original = $null
$replacement = $null
$completed = $false

try {
  $live = Invoke-Api GET '/q/health/live' $null
  $ready = Invoke-Api GET '/q/health/ready' $null
  Assert-Check ($live.status -eq 'UP' -and $ready.status -eq 'UP') 'Health checks are not UP'

  Invoke-Api GET '/' $null 404
  Invoke-Api GET '/index.html' $null 404
  foreach ($path in @('/warehouse', '/store', '/product', '/fulfilment')) {
    Invoke-Api GET $path $null | Out-Null
  }

  $store = Invoke-Api POST '/store' @{ name = "$prefix-store"; quantityProductsInStock = 10 } 201
  $product = Invoke-Api POST '/product' @{ name = "$prefix-product"; price = 12.50; stock = 5 } 201
  $warehouseBody = @{ businessUnitCode = $prefix; location = 'AMSTERDAM-002'; capacity = 20; stock = 5 }
  $original = Invoke-Api POST '/warehouse' $warehouseBody 201
  $read = Invoke-Api GET "/warehouse/$($original.id)" $null
  Assert-Check ($read.stock -eq 5 -and $read.capacity -eq 20) 'Warehouse fields were not saved'

  Invoke-Api POST '/warehouse' $warehouseBody 409
  Invoke-Api POST '/warehouse' @{ businessUnitCode = "$prefix-invalid"; location = 'UNKNOWN'; capacity = 20; stock = 5 } 400
  Invoke-Api GET '/warehouse/invalid-id' $null 400

  $patched = Invoke-Api PATCH "/store/$($store.id)" @{ quantityProductsInStock = 0 }
  Assert-Check ($patched.quantityProductsInStock -eq 0 -and $patched.name -eq "$prefix-store") 'PATCH did not preserve the name or set zero stock'

  $assignmentBody = @{ storeId = $store.id; productId = $product.id; warehouseId = [long]$original.id }
  $assignment = Invoke-Api POST '/fulfilment' $assignmentBody 201
  $duplicate = Invoke-Api POST '/fulfilment' $assignmentBody 200
  Assert-Check ($duplicate.id -eq $assignment.id) 'Repeated association created another row'

  Invoke-Api POST "/warehouse/$prefix/replacement" @{ location = 'AMSTERDAM-002'; capacity = 25; stock = 4 } 400
  $unchanged = Invoke-Api GET "/warehouse/$($original.id)" $null
  Assert-Check ($unchanged.stock -eq 5) 'Invalid replacement changed original stock'

  $replacement = Invoke-Api POST "/warehouse/$prefix/replacement" @{ location = 'AMSTERDAM-002'; capacity = 25; stock = 5 }
  Assert-Check ($replacement.id -ne $original.id -and $replacement.stock -eq 5) 'Replacement identity or stock is incorrect'
  Invoke-Api GET "/warehouse/$($original.id)" $null 404
  $oldId = [long]$original.id
  Assert-Check ((Read-Database "select count(*) from warehouse where id = $oldId and archivedAt is not null and stock = 5") -eq '1') 'Original warehouse history is missing'
  $assignmentId = [long]$assignment.id
  Assert-Check ((Read-Database "select count(*) from FulfilmentAssignment where id = $assignmentId and warehouse_id = $oldId") -eq '1') 'Historical association was lost'

  $active = @(Invoke-Api GET '/fulfilment' $null)
  Assert-Check (@($active | Where-Object { $_.id -eq $assignment.id }).Count -eq 0) 'Historical association is still active'
  $assignmentBody.warehouseId = [long]$replacement.id
  Invoke-Api POST '/fulfilment' $assignmentBody 201 | Out-Null
  Invoke-Api DELETE "/warehouse/$($replacement.id)" $null 204
  Invoke-Api GET "/warehouse/$($replacement.id)" $null 404
  $completed = $true
} finally {
  # Remove only records created by this run; warehouse rows remain archived as history.
  if ($null -ne $product) { Invoke-Api DELETE "/product/$($product.id)" $null 204 }
  if ($null -ne $store) { Invoke-Api DELETE "/store/$($store.id)" $null 204 }
  if (-not $completed) {
    foreach ($warehouse in @($replacement, $original)) {
      if ($null -ne $warehouse) {
        try { Invoke-Api DELETE "/warehouse/$($warehouse.id)" $null 204 } catch { Write-Warning $_ }
      }
    }
  }
}

$summary = [ordered]@{
  result = 'PASS'
  checks = $script:checkCount
  baseUrl = $BaseUrl
  businessUnitCode = $prefix
  originalWarehouseId = $original.id
  replacementWarehouseId = $replacement.id
  verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$reportPath = Join-Path $projectDirectory 'target/docker-verification.json'
$summary | ConvertTo-Json | Set-Content -LiteralPath $reportPath -Encoding UTF8
$summary | ConvertTo-Json
