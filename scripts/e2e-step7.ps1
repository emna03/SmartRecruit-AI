# Vérification réelle de l'ÉTAPE 7 : chaîne Spring Boot -> FastAPI avec la clé interne de .env.
#
# Prérequis : MySQL et le backend démarrés, environnement virtuel ai-service/.venv installé,
# service IA ARRÊTÉ (le script le démarre et l'arrête lui-même pour tester les pannes).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step7.ps1 [-BaseUrl http://localhost:8080]
param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\e2e-common.ps1"

$root = Split-Path -Parent $PSScriptRoot
$aiDir = Join-Path $root "ai-service"
$python = Join-Path $aiDir ".venv\Scripts\python.exe"
$aiLog = Join-Path $aiDir "ai-e2e.log"
if (-not (Test-Path $python)) { throw "Environnement virtuel absent : voir ai-service/README.md" }

$aiUrl = Get-EnvValue "AI_SERVICE_URL"
$aiKey = Get-EnvValue "AI_INTERNAL_API_KEY"
$aiPort = ([uri]$aiUrl).Port
$keyHeader = "X-Internal-Api-Key"

$adminToken = (Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }).Json.accessToken
$suffix = -join ((97..122) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
$candidate = Invoke-Api POST /api/auth/register @{ email = "e2e7-cand-$suffix@test.local"; password = "Passw0rd!"; firstName = "E2E7"; lastName = "Candidat"; role = "CANDIDAT" }
$ai = $null
$allLogs = ""

try {
    "--- Service IA FastAPI (appels directs) ---"
    $ai = Start-Ai
    $r = Invoke-Api GET "$aiUrl/health"
    Assert-Check "GET /health public -> 200 UP" ($r.Code -eq 200 -and $r.Json.status -eq "UP")
    $r = Invoke-Api GET "$aiUrl/api/status"
    Assert-Check "GET /api/status sans clé -> 401 (format ApiError)" ($r.Code -eq 401 -and $r.Json.message -eq "Clé d'API interne manquante ou invalide" -and $r.Json.path -eq "/api/status")
    $r = Invoke-Api GET "$aiUrl/api/status" -Headers @{ $keyHeader = "mauvaise-cle-0123456789abcdef" }
    Assert-Check "GET /api/status avec une mauvaise clé -> 401" ($r.Code -eq 401)
    $r = Invoke-Api GET "$aiUrl/api/status" -Token $aiKey
    Assert-Check "GET /api/status avec la clé en Bearer (mauvais en-tête) -> 401" ($r.Code -eq 401)
    $r = Invoke-Api GET "$aiUrl/api/status" -Headers @{ $keyHeader = $aiKey }
    $modules = @($r.Json.modules.PSObject.Properties)
    $available = @($modules | Where-Object { $_.Value -eq $true } | ForEach-Object { $_.Name })
    Assert-Check "GET /api/status avec la clé de .env -> 200, 4 modules (cv_parser, matching, chatbot disponibles)" ($r.Code -eq 200 -and $modules.Count -eq 4 -and
        (($available | Sort-Object) -join ",") -eq "chatbot,cv_parser,matching")
    $r = Invoke-Api GET "$aiUrl/docs"
    Assert-Check "Documentation Swagger désactivée par défaut -> 404" ($r.Code -eq 404)
    $listen = @(Get-NetTCPConnection -LocalPort $aiPort -State Listen -ErrorAction SilentlyContinue)
    Assert-Check "Écoute uniquement sur l'interface locale (127.0.0.1)" ($listen.Count -gt 0 -and -not ($listen | Where-Object { $_.LocalAddress -notin @("127.0.0.1", "::1") }))

    "--- Chaîne Angular/client -> Spring Boot -> FastAPI ---"
    $r = Invoke-Api GET /api/admin/ai/status
    Assert-Check "GET /api/admin/ai/status anonyme -> 401" ($r.Code -eq 401)
    $r = Invoke-Api GET /api/admin/ai/status -Token $candidate.Json.accessToken
    Assert-Check "GET /api/admin/ai/status candidat -> 403" ($r.Code -eq 403)
    $r = Invoke-Api GET /api/admin/ai/status -Token $adminToken
    Assert-Check "GET /api/admin/ai/status admin -> 200 (Spring Boot a appelé FastAPI)" ($r.Code -eq 200 -and $r.Json.status -eq "UP" -and $r.Json.service -eq "smartrecruit-ai" -and $r.Json.responseTimeMs -ge 0)
    Stop-Ai $ai
    $log = Get-Content $aiLog -Raw -Encoding UTF8
    $allLogs += $log
    Assert-Check "Journal FastAPI : appel de Spring Boot accepté (identifiant de corrélation)" ($log -match "GET /api/status -> 200 \(\d+ ms\) \[[0-9a-f]{32}\]")

    "--- Pannes : service IA arrêté ---"
    $start = Get-Date
    $r = Invoke-Api GET /api/admin/ai/status -Token $adminToken
    $elapsed = ((Get-Date) - $start).TotalSeconds
    Assert-Check "Service IA arrêté -> 503 avec message clair" ($r.Code -eq 503 -and $r.Json.message -like "*indisponible*")
    Assert-Check ("Réponse rapide malgré la panne ({0:N1} s < 5 s)" -f $elapsed) ($elapsed -lt 5)
    $r = Invoke-Api GET /api/jobs
    Assert-Check "Le reste de l'application fonctionne (GET /api/jobs -> 200)" ($r.Code -eq 200)

    "--- Pannes : clés différentes entre Spring Boot et FastAPI ---"
    $ai = Start-Ai -KeyOverride ("autre-cle-fastapi-" + [guid]::NewGuid().ToString("N"))
    $r = Invoke-Api GET /api/admin/ai/status -Token $adminToken
    Assert-Check "FastAPI refuse la clé de Spring Boot -> 503 côté client" ($r.Code -eq 503 -and $r.Json.message -like "*indisponible*")
    Stop-Ai $ai
    $log = Get-Content $aiLog -Raw -Encoding UTF8
    $allLogs += $log
    Assert-Check "Journal FastAPI : appel interne refusé (clé invalide)" ($log -match "Appel interne refusé : GET /api/status \(clé invalide")
    Assert-Check "Aucun journal ne contient la clé interne" (-not $allLogs.Contains($aiKey))
} finally {
    Stop-Ai $ai
    if ($candidate.Json.user.id) { Invoke-Api DELETE "/api/admin/users/$($candidate.Json.user.id)" -Token $adminToken | Out-Null }
    Remove-Item $aiLog, "$aiLog.out" -ErrorAction SilentlyContinue
}

Write-Summary
