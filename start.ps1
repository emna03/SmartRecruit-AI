# Démarre toute l'application SmartRecruit AI : .\demarrer (ou powershell -ExecutionPolicy Bypass -File start.ps1)
# MySQL dans Docker, backend Spring Boot, service IA FastAPI (avec sa page Swagger), frontend Angular, puis ouvre
# le navigateur. Un service déjà démarré n'est pas relancé. Arrêt : .\arreter
$root = $PSScriptRoot

function EnvValue([string]$key, [string]$default) {
    $line = Get-Content (Join-Path $root ".env") -Encoding UTF8 | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if ($line) { $value = ($line -split "=", 2)[1].Trim(); if ($value) { return $value } }
    return $default
}

function Listening([int]$port) {
    [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
}

function FrontendPort {
    $ours = Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
        Where-Object { $_.CommandLine -like "*$root\frontend*" -and $_.CommandLine -like "* serve*" }
    foreach ($process in $ours) {
        $connection = Get-NetTCPConnection -State Listen -OwningProcess $process.ProcessId -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($connection) { return [int]$connection.LocalPort }
    }
    return 0
}

function WaitFor([string]$name, [scriptblock]$ready, [int]$seconds) {
    Write-Host "  attente $name..." -NoNewline
    for ($i = 0; $i -lt $seconds; $i += 2) {
        if (& $ready) { Write-Host " prêt"; return }
        Start-Sleep 2
    }
    Write-Host " toujours pas prêt (voir sa fenêtre)"
}

function StartWindow([string]$title, [string]$folder, [string]$command) {
    Start-Process powershell -WorkingDirectory (Join-Path $root $folder) -ArgumentList "-NoExit", "-Command",
        "`$Host.UI.RawUI.WindowTitle = 'SmartRecruit - $title'; $command"
}

if (-not (Test-Path (Join-Path $root ".env"))) {
    throw "Fichier .env absent : copier .env.example en .env et le remplir (voir README)."
}
$backendPort = [int](EnvValue "SERVER_PORT" "8080")
$aiPort = ([Uri](EnvValue "AI_SERVICE_URL" "http://127.0.0.1:8000")).Port

Write-Host "1/4 MySQL (Docker)"
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    WaitFor "de Docker Desktop" { docker info *> $null; $LASTEXITCODE -eq 0 } 180
}
Push-Location $root
docker compose up -d mysql *> $null
Pop-Location
WaitFor "de MySQL" { (docker inspect -f "{{.State.Health.Status}}" smartrecruit-mysql 2>$null) -eq "healthy" } 120

Write-Host "2/4 Backend Spring Boot (port $backendPort)"
if (Listening $backendPort) { Write-Host "  déjà démarré" } else { StartWindow "Backend" "backend" ".\mvnw.cmd spring-boot:run" }

Write-Host "3/4 Service IA FastAPI (port $aiPort)"
$aiDocs = "http://127.0.0.1:$aiPort/docs"
function AiDocsUp { try { (Invoke-WebRequest -UseBasicParsing $aiDocs -TimeoutSec 2).StatusCode -eq 200 } catch { $false } }
if ((Listening $aiPort) -and -not (AiDocsUp)) {
    # Déjà lancé sans la page Swagger : relancé avec (écoute toujours sur 127.0.0.1 uniquement).
    Get-NetTCPConnection -State Listen -LocalPort $aiPort | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
    Start-Sleep 2
}
if (Listening $aiPort) { Write-Host "  déjà démarré" } else {
    StartWindow "Service IA" "ai-service" "`$env:PYTHONUTF8=1; `$env:AI_DOCS_ENABLED='true'; .\.venv\Scripts\python.exe -m app"
}

# Le port 4200 peut être occupé par une autre application Angular : on reconnaît la nôtre à son dossier.
$frontPort = FrontendPort
if ($frontPort) {
    Write-Host "4/4 Frontend Angular (port $frontPort)"
    Write-Host "  déjà démarré"
} else {
    $frontPort = 4200
    while (Listening $frontPort) { $frontPort++ }
    Write-Host "4/4 Frontend Angular (port $frontPort)"
    if ($frontPort -ne 4200) { Write-Host "  le port 4200 est pris par un autre programme : SmartRecruit utilise le port $frontPort" }
    # NG_CLI_ANALYTICS : sinon Angular CLI peut bloquer sur sa question de statistiques d'usage.
    StartWindow "Frontend" "frontend" "`$env:NG_CLI_ANALYTICS='false'; npm start -- --port $frontPort"
}

WaitFor "du backend" { try { (Invoke-RestMethod "http://localhost:$backendPort/actuator/health" -TimeoutSec 2).status -eq "UP" } catch { $false } } 180
WaitFor "du service IA" { try { (Invoke-RestMethod "http://127.0.0.1:$aiPort/health" -TimeoutSec 2).status -eq "UP" } catch { $false } } 60
WaitFor "d'Angular" { (FrontendPort) -eq $frontPort } 120

WaitFor "de la page des modèles IA" { AiDocsUp } 30

Write-Host ""
Write-Host "=== SmartRecruit AI est prêt ==="
Write-Host "Application : http://localhost:$frontPort   (admin : ADMIN_EMAIL / ADMIN_PASSWORD du fichier .env)"
Write-Host "Modèles IA  : $aiDocs   (bouton Authorize : valeur AI_INTERNAL_API_KEY du fichier .env)"
Write-Host ""
Write-Host "Où voir chaque modèle IA dans l'application :"
Write-Host "  - CV Parser / NLP : candidat > Mon CV > importer un PDF"
Write-Host "  - Matching        : candidat > Offres > ouvrir une offre (score) ; recruteur > Mes offres > Candidats"
Write-Host "  - Chatbot         : candidat > Assistant RH"
Write-Host "  - ML turnover     : recruteur ou admin > Risque de turnover"
Write-Host ""
Write-Host "Arrêter : .\arreter"
Start-Process "http://localhost:$frontPort"
Start-Process $aiDocs
