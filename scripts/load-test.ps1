$k6Dir = "C:\Program Files\k6"
if ((Test-Path "$k6Dir\k6.exe") -and ($env:Path -notlike "*$k6Dir*")) {
    $env:Path += ";$k6Dir"
}

$script = Join-Path $PSScriptRoot "..\k6\get-products.js"
$script = (Resolve-Path $script).Path

if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    Write-Error "k6 is not on PATH. Install it first (winget install k6), then re-run this script."
    exit 1
}

k6 run $script