# Vérification réelle de l'ÉTAPE 11 : saisie RH -> Spring Boot (validation, rôles) -> FastAPI (modèle ML réellement
# entraîné sur le dataset IBM) -> prédiction validée -> API. Les valeurs attendues viennent du rapport produit par
# l'entraînement (ai-service/ml/reports/turnover_evaluation.json), jamais d'un score écrit dans ce script.
#
# Prérequis : MySQL (conteneur smartrecruit-mysql) et le backend démarrés, ai-service/.venv installé, dataset téléchargé
# et modèle entraîné (python -m ml.download_dataset ; python -m ml.train), service IA ARRÊTÉ (le script le démarre).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step11.ps1 [-BaseUrl http://localhost:8080]
param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\e2e-common.ps1"

$root = Split-Path -Parent $PSScriptRoot
$aiDir = Join-Path $root "ai-service"
$python = Join-Path $aiDir ".venv\Scripts\python.exe"
$aiLog = Join-Path $aiDir "ai-e2e.log"
if (-not (Test-Path $python)) { throw "Environnement virtuel absent : voir ai-service/README.md" }
foreach ($file in @("ml\data\emp_attrition.csv", "ml\artifacts\turnover_model.joblib", "ml\artifacts\turnover_model.json")) {
    if (-not (Test-Path (Join-Path $aiDir $file))) { throw "$file absent : lancer python -m ml.download_dataset puis python -m ml.train" }
}
$report = Get-Content (Join-Path $aiDir "ml\reports\turnover_evaluation.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$chosen = $report.selection.chosenModel
$testMetrics = $report.test.results.$chosen
$low = $report.examples.lowRisk
$high = $report.examples.highRisk

$aiUrl = Get-EnvValue "AI_SERVICE_URL"
$keyHeader = @{ "X-Internal-Api-Key" = (Get-EnvValue "AI_INTERNAL_API_KEY") }
$script:aiLogs = ""
function Stop-AiKeepLog($process) {
    Stop-Ai $process
    if (Test-Path $aiLog) { $script:aiLogs += Get-Content $aiLog -Raw -Encoding UTF8 }
}
# Copie modifiable d'un profil du rapport (objet JSON -> table de hachage).
function Profile($example, [hashtable]$changes = @{}) {
    $h = [ordered]@{}
    foreach ($p in $example.features.PSObject.Properties) { $h[$p.Name] = $p.Value }
    foreach ($k in $changes.Keys) { $h[$k] = $changes[$k] }
    return $h
}
function Without($example, [string]$field) { $h = Profile $example; $h.Remove($field); return $h }
function Predict($body, [string]$token) { return Invoke-Api POST /api/hr/turnover/predictions $body -Token $token }
function Direct($body, [hashtable]$headers = $keyHeader) { return Invoke-Api POST "$aiUrl/api/ml/turnover/predict" $body -Headers $headers }
function ValidFactors($prediction) {
    $features = @($prediction.factors | ForEach-Object { $_.feature })
    $known = @($low.features.PSObject.Properties.Name)
    $directions = @($prediction.factors | Where-Object { ($_.impact -gt 0 -and $_.direction -eq "INCREASES_RISK") -or ($_.impact -lt 0 -and $_.direction -eq "DECREASES_RISK") })
    return ($features.Count -ge 1 -and $features.Count -le 5 -and @($features | Where-Object { $known -contains $_ }).Count -eq $features.Count -and
        $directions.Count -eq $features.Count)
}

$adminToken = (Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }).Json.accessToken
$suffix = -join ((97..122) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
$users = @{}
function Register([string]$key, [string]$role) {
    $email = "e2e11-$key-$suffix@test.local"
    $r = Invoke-Api POST /api/auth/register @{ email = $email; password = "Passw0rd!"; firstName = "Test"; lastName = "E2E"; role = $role }
    $script:users[$key] = [pscustomobject]@{ Email = $email; Token = $r.Json.accessToken; Id = $r.Json.user.id }
    return $script:users[$key]
}
$rec = Register "rec" "RECRUTEUR"
$cand = Register "cand" "CANDIDAT"
$tablesBefore = (Invoke-Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")[0][0]
$ai = $null

try {
    "--- Reproductibilité : pipeline relancé sur le vrai dataset (graines fixes) ---"
    $check = Join-Path $env:TEMP "sr-ml-repro.py"
    [IO.File]::WriteAllText($check, @"
import json, pathlib, tempfile
from app.services.ml.training import REPORT_PATH, train
report = train(artifacts_dir=pathlib.Path(tempfile.mkdtemp()), report_path=None)
committed = json.loads(REPORT_PATH.read_text(encoding="utf-8"))
print("IDENTIQUE" if json.loads(json.dumps(report)) == committed else "DIFFERENT")
"@, (New-Object Text.UTF8Encoding($false)))
    Push-Location $aiDir
    $env:PYTHONUTF8 = "1"
    try { $repro = (& $python $check 2>$null) | Select-Object -Last 1 } finally { Pop-Location; Remove-Item $check -ErrorAction SilentlyContinue }
    Assert-Check "Réentraînement -> rapport identique au rapport versionné" ($repro -eq "IDENTIQUE")
    Assert-Check ("Modèle retenu {0} : F1 CV {1} ; test ROC-AUC {2}, rappel {3}" -f $chosen, $report.crossValidation.results.$chosen.mean.f1,
        $testMetrics.rocAuc, $testMetrics.recall) ($report.split.testRows -eq 294 -and $report.split.trainRows -eq 1176)

    "--- Service IA FastAPI : POST /api/ml/turnover/predict (appel direct) ---"
    $ai = Start-Ai
    $status = Invoke-Api GET "$aiUrl/api/status" -Headers $keyHeader
    Assert-Check "GET /api/status : module ml disponible (modèle chargé et vérifié)" ($status.Code -eq 200 -and $status.Json.modules.ml -eq $true)
    Assert-Check "Sans clé interne -> 401" ((Direct (Profile $high) @{}).Code -eq 401)
    $r = Direct (Profile $high)
    Assert-Check ("Profil à risque élevé -> {0} (rapport : {1})" -f $r.Json.probability, $high.probability) ($r.Code -eq 200 -and
        $r.Json.probability -eq $high.probability -and $r.Json.riskLevel -eq "ELEVE" -and $r.Json.model.name -eq $chosen)
    $r2 = Direct (Profile $high)
    Assert-Check "Même entrée -> même prédiction (déterministe)" (($r.Json | ConvertTo-Json -Depth 5) -eq ($r2.Json | ConvertTo-Json -Depth 5))
    Assert-Check "Champ sensible non prévu (age) -> 400" ((Direct (Profile $low @{ age = 30 })).Code -eq 400)
    Assert-Check "Champ manquant (monthlyIncome) -> 400" ((Direct (Without $low "monthlyIncome")).Code -eq 400)

    "--- Sécurité de l'API RH ---"
    Assert-Check "Anonyme -> 401" ((Predict (Profile $low) $null).Code -eq 401)
    Assert-Check "Jeton invalide -> 401" ((Predict (Profile $low) "abc.def.ghi").Code -eq 401)
    Assert-Check "Candidat -> 403" ((Predict (Profile $low) $cand.Token).Code -eq 403)

    "--- Prédictions réelles via Spring Boot (recruteur et administrateur) ---"
    $r = Predict (Profile $high) $rec.Token
    Assert-Check ("Recruteur, profil 2 -> ÉLEVÉ, score {0} = rapport" -f $r.Json.probability) ($r.Code -eq 200 -and $r.Json.riskLevel -eq "ELEVE" -and
        $r.Json.probability -eq $high.probability -and $r.Json.probability -ge $r.Json.highThreshold)
    Assert-Check "Facteurs : 1 à 5 variables du modèle, sens cohérent avec l'impact" (ValidFactors $r.Json)
    Assert-Check "Métriques affichées = métriques de test du rapport" ($r.Json.model.testRocAuc -eq $testMetrics.rocAuc -and
        $r.Json.model.testRecall -eq $testMetrics.recall -and $r.Json.model.testF1 -eq $testMetrics.f1 -and
        $r.Json.model.dataset -eq "IBM HR Analytics Employee Attrition & Performance")
    $r = Predict (Profile $low) $adminToken
    Assert-Check ("Administrateur, profil 1 -> FAIBLE, score {0} = rapport" -f $r.Json.probability) ($r.Code -eq 200 -and $r.Json.riskLevel -eq "FAIBLE" -and
        $r.Json.probability -eq $low.probability -and $r.Json.probability -lt $r.Json.mediumThreshold -and (ValidFactors $r.Json))
    $more = Predict (Profile $high @{ overTime = $false; businessTravel = "NON_TRAVEL"; stockOptionLevel = 1 }) $rec.Token
    Assert-Check ("Sans heures sup., sans déplacements, stock-options -> score plus bas ({0})" -f $more.Json.probability) ($more.Code -eq 200 -and
        $more.Json.probability -lt $high.probability)
    $r = Predict (Profile $low @{ age = 35; gender = "Female"; maritalStatus = "Single"; employeeName = "Alice" }) $rec.Token
    Assert-Check "Données personnelles ajoutées par le client -> ignorées, non transmises (200)" ($r.Code -eq 200 -and $r.Json.probability -eq $low.probability)

    "--- Données manquantes ou invalides (refusées par Spring Boot) ---"
    foreach ($field in @("monthlyIncome", "overTime", "jobRole")) {
        $r = Predict (Without $low $field) $rec.Token
        Assert-Check "Champ $field manquant -> 400 fieldErrors.$field" ($r.Code -eq 400 -and $null -ne $r.Json.fieldErrors.$field)
    }
    foreach ($case in @(@("monthlyIncome", 500), @("jobSatisfaction", 5), @("performanceRating", 2))) {
        $r = Predict (Profile $low @{ $case[0] = $case[1] }) $rec.Token
        Assert-Check ("{0} = {1} (hors domaine) -> 400" -f $case[0], $case[1]) ($r.Code -eq 400 -and $null -ne $r.Json.fieldErrors.($case[0]))
    }
    foreach ($case in @(@("education", 2.5), @("monthlyIncome", "5000"), @("overTime", "yes"), @("jobRole", "CEO"))) {
        $r = Predict (Profile $low @{ $case[0] = $case[1] }) $rec.Token
        Assert-Check ("{0} = {1} (mauvais type ou code inconnu) -> 400" -f $case[0], $case[1]) ($r.Code -eq 400)
    }
    $r = Predict (Profile $low @{ totalWorkingYears = 3 }) $rec.Token
    Assert-Check "Ancienneté > expérience totale -> 400" ($r.Code -eq 400 -and $null -ne $r.Json.fieldErrors.yearsAtCompanyWithinCareer)
    $r = Predict (Profile $low @{ department = "SALES" }) $rec.Token
    Assert-Check "Poste absent du département -> 400" ($r.Code -eq 400 -and $null -ne $r.Json.fieldErrors.jobRoleInDepartment)

    "--- Aucune donnée enregistrée, aucune migration ---"
    $tablesAfter = (Invoke-Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")[0][0]
    $migrations = (Invoke-Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")[0][0]
    Assert-Check "Même nombre de tables ; une seule migration Flyway (V1)" ($tablesAfter -eq $tablesBefore -and $migrations -eq "1")

    Predict (Profile $high @{ monthlyIncome = 12345 }) $rec.Token | Out-Null

    "--- Service IA indisponible ---"
    Stop-AiKeepLog $ai
    $ai = $null
    $start = Get-Date
    $r = Predict (Profile $high) $rec.Token
    $elapsed = ((Get-Date) - $start).TotalSeconds
    Assert-Check ("Prédiction -> 503 « momentanément indisponible » ({0:N1} s)" -f $elapsed) ($r.Code -eq 503 -and
        $r.Json.message -eq "Le service IA est momentanément indisponible. Réessayez plus tard." -and $elapsed -lt 5)
    Assert-Check "Le reste de la plateforme fonctionne (GET /api/jobs -> 200)" ((Invoke-Api GET /api/jobs).Code -eq 200)

    "--- Journaux : score et niveau seulement, jamais les variables saisies ---"
    $log = $script:aiLogs
    Assert-Check "Journal FastAPI : « Prédiction turnover : score ..., niveau ELEVE »" ($log -match "Prédiction turnover : score 0\.\d{3}, niveau ELEVE, modèle $chosen v1\.0\.0")
    Assert-Check "Journal FastAPI : aucune valeur saisie (12345, codes de poste)" (-not $log.Contains("12345") -and -not $log.Contains("SALES_REPRESENTATIVE"))
    $backendLog = Join-Path $env:TEMP "sr-backend.log"
    if (Test-Path $backendLog) {
        $b = Get-Content $backendLog -Raw -Encoding UTF8
        Assert-Check "Journal Spring Boot : niveau et modèle seulement" ($b -match "Prédiction de turnover : niveau ELEVE, modèle $chosen v1\.0\.0" -and
            -not $b.Contains("12345") -and -not $b.Contains("SALES_REPRESENTATIVE"))
    }
} finally {
    Stop-Ai $ai
    Remove-Item $aiLog, "$aiLog.out" -ErrorAction SilentlyContinue
    "--- Nettoyage (comptes de test, par l'admin) ---"
    foreach ($u in $users.Values) { if ($u.Id) { Invoke-Api DELETE "/api/admin/users/$($u.Id)" -Token $adminToken | Out-Null } }
    $left = (Invoke-Sql "SELECT COUNT(*) FROM users WHERE email LIKE 'e2e11-%'")[0][0]
    Assert-Check "Comptes de test supprimés de MySQL" ($left -eq "0")
}

Write-Summary
