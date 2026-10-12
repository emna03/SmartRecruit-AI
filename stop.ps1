# Arrête toute l'application SmartRecruit AI : .\arreter (ou powershell -ExecutionPolicy Bypass -File stop.ps1)
$root = $PSScriptRoot

function EnvValue([string]$key, [string]$default) {
    $line = Get-Content (Join-Path $root ".env") -Encoding UTF8 -ErrorAction SilentlyContinue | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if ($line) { $value = ($line -split "=", 2)[1].Trim(); if ($value) { return $value } }
    return $default
}

$services = [ordered]@{
    "Backend Spring Boot" = [int](EnvValue "SERVER_PORT" "8080")
    "Service IA FastAPI"  = ([Uri](EnvValue "AI_SERVICE_URL" "http://127.0.0.1:8000")).Port
}
foreach ($name in $services.Keys) {
    $owners = Get-NetTCPConnection -State Listen -LocalPort $services[$name] -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($id in $owners) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
    Write-Host ("{0} : {1}" -f $name, $(if ($owners) { "arrêté" } else { "rien à arrêter" }))
}

# Seul le serveur Angular de ce projet est arrêté (une autre application peut utiliser le port 4200).
$front = @(Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
    Where-Object { $_.CommandLine -like "*$root\frontend*" -and $_.CommandLine -like "* serve*" })
foreach ($process in $front) {
    Get-CimInstance Win32_Process -Filter "ParentProcessId=$($process.ProcessId)" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
}
Write-Host ("Frontend Angular : {0}" -f $(if ($front.Count) { "arrêté" } else { "rien à arrêter" }))
Push-Location $root
docker compose stop mysql *> $null
Pop-Location
Write-Host "MySQL : arrêté"
Write-Host "Les fenêtres restées ouvertes peuvent être fermées."
