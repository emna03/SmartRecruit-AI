# Audit de sécurité de l'ÉTAPE 13 sur la vraie pile (Spring Boot + MySQL + FastAPI) : JWT, rôles, appartenance des
# ressources, validation, upload PDF, clé interne Spring Boot <-> FastAPI, CORS, codes d'erreur, journaux sans données
# personnelles, absence de secrets dans Git.
#
# Prérequis : MySQL (conteneur smartrecruit-mysql) et le backend démarrés, ai-service/.venv installé,
# service IA ARRÊTÉ (le script le démarre puis l'arrête pour tester la panne).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step13.ps1 [-BaseUrl http://localhost:8080]
param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\e2e-common.ps1"

$root = Split-Path -Parent $PSScriptRoot
$aiDir = Join-Path $root "ai-service"
$python = Join-Path $aiDir ".venv\Scripts\python.exe"
$aiLog = Join-Path $aiDir "ai-e2e.log"
$fixtures = Join-Path $aiDir "tests\fixtures\cv"
if (-not (Test-Path $python)) { throw "Environnement virtuel absent : voir ai-service/README.md" }
$aiUrl = Get-EnvValue "AI_SERVICE_URL"
$aiKey = Get-EnvValue "AI_INTERNAL_API_KEY"

function B64Url([byte[]]$bytes) { return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
function FromB64Url([string]$text) {
    $s = $text.Replace('-', '+').Replace('_', '/')
    while ($s.Length % 4) { $s += "=" }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s))
}
function Utf8([string]$text) { return [Text.Encoding]::UTF8.GetBytes($text) }
function Code([string]$method, [string]$path, $body = $null, [string]$token = $null) { return (Invoke-Api $method $path $body $token).Code }
function Preflight([string]$origin) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Method OPTIONS "$BaseUrl/api/auth/login" -Headers @{
            Origin = $origin; "Access-Control-Request-Method" = "POST"; "Access-Control-Request-Headers" = "content-type,authorization" }
        return [pscustomobject]@{ Code = [int]$r.StatusCode; Origin = $r.Headers["Access-Control-Allow-Origin"] }
    } catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        return [pscustomobject]@{ Code = [int]$resp.StatusCode; Origin = $resp.Headers["Access-Control-Allow-Origin"] }
    }
}

$admin = (Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }).Json.accessToken
$suffix = -join ((97..122) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
$users = @{}
$companies = @()
function Register([string]$key, [string]$role) {
    $email = "e2e13-$key-$suffix@test.local"
    $r = Invoke-Api POST /api/auth/register @{ email = $email; password = "Passw0rd!"; firstName = "Test"; lastName = "Securite"; role = $role }
    if ($r.Code -ne 201) { throw "Inscription refusée ($email) : $($r.Code)" }
    $script:users[$key] = [pscustomobject]@{ Email = $email; Token = $r.Json.accessToken; Id = $r.Json.user.id }
    return $script:users[$key]
}

$cand = Register "cand" "CANDIDAT"
$other = Register "other" "CANDIDAT"
$recA = Register "reca" "RECRUTEUR"
$recB = Register "recb" "RECRUTEUR"
$doomed = Register "doomed" "CANDIDAT"
foreach ($r in @($recA, $recB)) {
    $script:companies += (Invoke-Api POST /api/recruiter/company @{ name = "E2E13 Secu $($r.Email.Split('-')[1]) $suffix"; sector = "IT" } -Token $r.Token).Json.id
}
$offerBody = @{ title = "Offre sécurité $suffix"; description = "Offre de test E2E étape 13"; location = "Tunis"; contractType = "CDI";
    minExperienceYears = 0; requiredSkills = @("Java"); status = "PUBLIEE" }
$offer = (Invoke-Api POST /api/recruiter/jobs $offerBody -Token $recA.Token).Json.id
$draftBody = $offerBody.Clone(); $draftBody.title = "Brouillon sécurité $suffix"; $draftBody.status = "BROUILLON"
$draft = (Invoke-Api POST /api/recruiter/jobs $draftBody -Token $recA.Token).Json.id
$application = (Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offer; coverLetter = "Lettre confidentielle $suffix" } -Token $cand.Token).Json.id
$ai = $null

try {
    "--- JWT ---"
    $parts = $cand.Token.Split('.')
    $payload = FromB64Url $parts[1]
    Assert-Check "Jeton : en-tête HS*, aucune donnée sensible dans la charge utile" ((FromB64Url $parts[0]) -match '"alg":"HS(256|384|512)"' -and
        $payload -notmatch "password|hash" -and -not $payload.Contains("Securite"))
    Assert-Check "Sans jeton -> 401" ((Code GET /api/candidate/profile) -eq 401)
    Assert-Check "Jeton mal formé -> 401" ((Code GET /api/candidate/profile -token "abc.def") -eq 401)
    $key = New-Object byte[] 48; (New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($key)
    $header = B64Url (Utf8 '{"alg":"HS384","typ":"JWT"}')
    $sig = B64Url ((New-Object Security.Cryptography.HMACSHA384(, $key)).ComputeHash([Text.Encoding]::ASCII.GetBytes("$header.$($parts[1])")))
    Assert-Check "Jeton signé avec une autre clé -> 401" ((Code GET /api/candidate/profile -token "$header.$($parts[1]).$sig") -eq 401)
    $none = B64Url (Utf8 '{"alg":"none","typ":"JWT"}')
    Assert-Check "Jeton « alg: none » non signé -> 401" ((Code GET /api/candidate/profile -token "$none.$($parts[1]).") -eq 401)
    $forged = B64Url (Utf8 ($payload -replace '"CANDIDAT"', '"ADMIN"' -replace '"exp":\d+', '"exp":4102444800'))
    Assert-Check "Charge utile modifiée (rôle ADMIN, expiration repoussée) -> 401" ((Code GET /api/admin/users -token "$($parts[0]).$forged.$($parts[2])") -eq 401)
    Assert-Check "Compte supprimé par l'admin : son ancien jeton -> 401" ((Code DELETE "/api/admin/users/$($doomed.Id)" -token $admin) -eq 204 -and
        (Code GET /api/candidate/profile -token $doomed.Token) -eq 401)
    $users.Remove("doomed")

    "--- Rôles ---"
    Assert-Check "Inscription en ADMIN -> 400" ((Code POST /api/auth/register @{ email = "e2e13-x-$suffix@test.local"; password = "Passw0rd!"; firstName = "X"; lastName = "Y"; role = "ADMIN" }) -eq 400)
    Assert-Check "Candidat : API recruteur, admin, RH -> 403" ((Code GET /api/recruiter/jobs -token $cand.Token) -eq 403 -and
        (Code GET /api/admin/users -token $cand.Token) -eq 403 -and (Code POST /api/hr/turnover/predictions @{} -token $cand.Token) -eq 403)
    Assert-Check "Recruteur : API candidat, admin -> 403" ((Code GET /api/candidate/cv -token $recA.Token) -eq 403 -and (Code GET /api/admin/dashboard -token $recA.Token) -eq 403)
    Assert-Check "Admin : API candidat et recruteur -> 403" ((Code GET /api/candidate/profile -token $admin) -eq 403 -and (Code GET /api/recruiter/jobs -token $admin) -eq 403)

    "--- Appartenance des ressources (ownership) ---"
    Assert-Check "Recruteur B : offre de A (lecture, modification, statut, suppression) -> 404" ((Code GET "/api/recruiter/jobs/$offer" -token $recB.Token) -eq 404 -and
        (Code PUT "/api/recruiter/jobs/$offer" $offerBody -token $recB.Token) -eq 404 -and
        (Code PATCH "/api/recruiter/jobs/$offer/status" @{ status = "FERMEE" } -token $recB.Token) -eq 404 -and
        (Code DELETE "/api/recruiter/jobs/$offer" -token $recB.Token) -eq 404)
    Assert-Check "Recruteur B : candidats et candidature de A, changement de statut -> 404" ((Code GET "/api/recruiter/jobs/$offer/applications" -token $recB.Token) -eq 404 -and
        (Code GET "/api/recruiter/applications/$application" -token $recB.Token) -eq 404 -and
        (Code PATCH "/api/recruiter/applications/$application/status" @{ status = "ACCEPTE" } -token $recB.Token) -eq 404)
    Assert-Check "Recruteur B : recalcul des scores de l'offre de A -> 404" ((Code POST "/api/recruiter/jobs/$offer/matching" -token $recB.Token) -eq 404)
    Assert-Check "MySQL : offre et candidature de A inchangées" ((Invoke-Sql "SELECT o.status, a.status FROM job_offers o JOIN applications a ON a.job_offer_id = o.id WHERE a.id = $application")[0] -join "/" -eq "PUBLIEE/EN_ATTENTE")
    Assert-Check "Autre candidat : candidature d'un tiers -> 404" ((Code GET "/api/candidate/applications/$application" -token $other.Token) -eq 404)
    Assert-Check "Brouillon invisible : public et candidat -> 404, absent de la recherche" ((Code GET "/api/jobs/$draft") -eq 404 -and
        (Code GET "/api/candidate/jobs/$draft/matching" -token $cand.Token) -eq 404 -and
        @((Invoke-Api GET "/api/jobs?keyword=$suffix").Json.content | Where-Object { $_.id -eq $draft }).Count -eq 0)

    "--- Validation des entrées et conflits ---"
    $r = Invoke-Api POST /api/auth/register @{ email = "pas-un-email"; password = "court"; firstName = ""; lastName = "Y"; role = "CANDIDAT" }
    Assert-Check "Inscription invalide -> 400 avec erreurs par champ" ($r.Code -eq 400 -and [bool]$r.Json.fieldErrors.email -and [bool]$r.Json.fieldErrors.password)
    Assert-Check "Email déjà utilisé (majuscules) -> 409" ((Code POST /api/auth/register @{ email = $cand.Email.ToUpper(); password = "Passw0rd!"; firstName = "X"; lastName = "Y"; role = "CANDIDAT" }) -eq 409)
    Assert-Check "Candidature en double -> 409" ((Code POST /api/candidate/applications @{ jobOfferId = $offer } -token $cand.Token) -eq 409)
    $bad = $offerBody.Clone(); $bad.salaryMin = 5000; $bad.salaryMax = 1000
    Assert-Check "Offre avec salaire max < min -> 400" ((Code POST /api/recruiter/jobs $bad -token $recA.Token) -eq 400)
    $bad = $offerBody.Clone(); $bad.contractType = "ESCLAVAGE"
    Assert-Check "Valeur d'énumération inconnue -> 400" ((Code POST /api/recruiter/jobs $bad -token $recA.Token) -eq 400)
    Assert-Check "Identifiant non numérique -> 400" ((Code GET "/api/recruiter/jobs/abc" -token $recA.Token) -eq 400)
    Assert-Check "Chatbot : question vide ou > 500 caractères -> 400" ((Code POST /api/candidate/chatbot/messages @{ message = "" } -token $cand.Token) -eq 400 -and
        (Code POST /api/candidate/chatbot/messages @{ message = ("a" * 501) } -token $cand.Token) -eq 400)
    $r = Invoke-Api POST /api/recruiter/jobs @{ title = "" } -Token $recA.Token
    $fields = @($r.Json.PSObject.Properties.Name)
    Assert-Check "Réponse d'erreur sans trace technique (pas de stacktrace / exception)" ($r.Code -eq 400 -and
        -not (@("trace", "exception", "stackTrace") | Where-Object { $_ -in $fields }) -and -not (($r.Json | ConvertTo-Json -Depth 5) -match "\.java|org\.spring|at com\."))

    "--- Upload PDF ---"
    $ai = Start-Ai
    $pdf = [IO.File]::ReadAllBytes((Join-Path $fixtures "cv_dev_fullstack_fr.pdf"))
    $r = Invoke-Upload /api/candidate/cv $pdf "..\..\..\evil.pdf" "application/pdf" $cand.Token
    $stored = (Invoke-Sql "SELECT c.stored_file_name, c.original_file_name FROM cvs c JOIN candidate_profiles p ON p.id = c.candidate_id JOIN users u ON u.id = p.user_id WHERE u.email = '$($cand.Email)'")[0]
    Assert-Check "Nom de fichier « ..\..\..\evil.pdf » : stocké sous un nom aléatoire dans uploads/cvs" ($r.Code -eq 200 -and $stored[0] -match '^[0-9a-f]{32}\.pdf$' -and
        (Test-Path (Join-Path $root "backend\uploads\cvs\$($stored[0])")) -and -not (Test-Path (Join-Path $root "evil.pdf")))
    Assert-Check "Recruteur : import de CV -> 403" ((Invoke-Upload /api/candidate/cv $pdf "cv.pdf" "application/pdf" $recA.Token).Code -eq 403)
    # Seul le contenu fait foi (signature %PDF-) : le type MIME et l'extension déclarés par le navigateur sont ignorés.
    $png = [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + (Utf8 ("x" * 100))
    Assert-Check "Vraie image PNG -> 400 ; texte renommé .pdf -> 400 (signature PDF absente)" ((Invoke-Upload /api/candidate/cv $png "photo.png" "image/png" $other.Token).Code -eq 400 -and
        (Invoke-Upload /api/candidate/cv (Utf8 "pas un pdf") "cv.pdf" "application/pdf" $other.Token).Code -eq 400)
    Assert-Check "Type de contenu non pris en charge -> 415 (Spring Boot et FastAPI)" ((Invoke-Binary "$BaseUrl/api/auth/login" (Utf8 "x") "text/plain").Code -eq 415 -and
        (Invoke-Binary "$aiUrl/api/cv/parse" $pdf "text/plain" @{ "X-Internal-Api-Key" = $aiKey }).Code -eq 415)
    # curl.exe : le HttpClient de .NET Framework abandonne quand le serveur répond avant la fin de l'envoi.
    $bigFile = Join-Path $env:TEMP "e2e13-gros-$suffix.pdf"
    $big = New-Object byte[] (6MB); [Array]::Copy([Text.Encoding]::ASCII.GetBytes("%PDF-1.4"), $big, 8)
    [IO.File]::WriteAllBytes($bigFile, $big)
    $out = @(curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $($other.Token)" -F "file=@$bigFile;type=application/pdf" "$BaseUrl/api/candidate/cv")
    Remove-Item $bigFile -ErrorAction SilentlyContinue
    Assert-Check "Fichier de 6 Mo -> 413 (limite MAX_CV_SIZE_MB)" ($out[-1] -eq "413")
    $r = Invoke-Upload /api/candidate/cv ([IO.File]::ReadAllBytes((Join-Path $fixtures "cv_corrupted.pdf"))) "cv.pdf" "application/pdf" $other.Token
    Assert-Check "PDF endommagé -> 422 (refusé par le CV Parser)" ($r.Code -eq 422)

    "--- Clé interne Spring Boot <-> FastAPI ---"
    Assert-Check "FastAPI /health public, sans détail sensible" ((Invoke-Api GET "$aiUrl/health").Code -eq 200 -and
        -not (((Invoke-Api GET "$aiUrl/health").Json | ConvertTo-Json) -match "key|secret|password"))
    Assert-Check "FastAPI /api/status : sans clé -> 401, mauvaise clé -> 401, bonne clé -> 200" ((Invoke-Api GET "$aiUrl/api/status").Code -eq 401 -and
        (Invoke-Api GET "$aiUrl/api/status" -Headers @{ "X-Internal-Api-Key" = "mauvaise-cle" }).Code -eq 401 -and
        (Invoke-Api GET "$aiUrl/api/status" -Headers @{ "X-Internal-Api-Key" = $aiKey }).Code -eq 200)
    $port = ([Uri]$aiUrl).Port
    $listen = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | ForEach-Object { $_.LocalAddress } | Sort-Object -Unique)
    Assert-Check "FastAPI n'écoute que sur 127.0.0.1 ($($listen -join ', '))" ($listen.Count -gt 0 -and -not ($listen | Where-Object { $_ -notin @("127.0.0.1", "::1") }))
    $myPort = docker port smartrecruit-mysql 3306
    Assert-Check "MySQL (Docker) publié uniquement sur 127.0.0.1 ($myPort)" (@($myPort | Where-Object { $_ -notmatch '^127\.0\.0\.1:' }).Count -eq 0)

    "--- CORS ---"
    $allowed = Get-EnvValue "CORS_ALLOWED_ORIGINS"
    $p = Preflight ($allowed.Split(',')[0])
    Assert-Check "Pré-vol depuis l'origine Angular autorisée -> en-tête Access-Control-Allow-Origin" ($p.Code -eq 200 -and $p.Origin -eq $allowed.Split(',')[0])
    $p = Preflight "http://evil.example"
    Assert-Check "Pré-vol depuis une origine inconnue -> refusé, sans en-tête CORS" ($p.Code -eq 403 -and -not $p.Origin)

    "--- Service IA arrêté -> 503 clair, le reste fonctionne ---"
    Stop-Ai $ai
    $ai = $null
    $r = Invoke-Api POST /api/candidate/chatbot/messages @{ message = "Quelles offres demandent Java ?" } -Token $cand.Token
    Assert-Check "Chatbot -> 503 avec message lisible" ($r.Code -eq 503 -and $r.Json.message -like "*indisponible*")
    Assert-Check "Recalcul des scores -> 503 ; statut IA admin -> 503" ((Code POST "/api/recruiter/jobs/$offer/matching" -token $recA.Token) -eq 503 -and
        (Code GET /api/admin/ai/status -token $admin) -eq 503)
    Assert-Check "Pendant la panne : offres, candidatures et dashboards restent disponibles" ((Code GET /api/jobs) -eq 200 -and
        (Code GET /api/candidate/applications -token $cand.Token) -eq 200 -and (Code GET /api/recruiter/dashboard -token $recA.Token) -eq 200)

    "--- Journaux sans données personnelles ---"
    $aiText = if (Test-Path $aiLog) { Get-Content $aiLog -Raw -Encoding UTF8 } else { "" }
    $backendLog = Join-Path $env:TEMP "sr-backend.log"
    $backText = if (Test-Path $backendLog) { Get-Content $backendLog -Raw -Encoding UTF8 } else { "" }
    $secrets = @("DB_PASSWORD", "JWT_SECRET", "ADMIN_PASSWORD", "AI_INTERNAL_API_KEY") | ForEach-Object { Get-EnvValue $_ }
    $personal = @($cand.Email, $other.Email, $recA.Email, "Lettre confidentielle", "Passw0rd!", "camille.exemple@example.com", "Camille Exemple", $cand.Token)
    $leaks = @($personal + $secrets | Where-Object { $_ -and ($aiText.Contains($_) -or $backText.Contains($_)) })
    Assert-Check "Journaux Spring Boot et FastAPI : ni email, ni CV, ni lettre, ni mot de passe, ni jeton, ni secret ($($leaks.Count))" ($backText.Length -gt 0 -and $leaks.Count -eq 0)
    $emails = @([regex]::Matches($backText + $aiText, '[\w.+-]+@[\w-]+\.[\w.-]+') | ForEach-Object { $_.Value } | Sort-Object -Unique)
    Assert-Check "Journaux : aucune adresse email, quel que soit le compte ($($emails.Count))" ($emails.Count -eq 0)

    "--- Secrets et Git ---"
    Push-Location $root
    try {
        $history = (git log -p --all --no-color) -join "`n"
        $found = @($secrets + (Get-EnvValue "DB_ROOT_PASSWORD") | Where-Object { $history.Contains($_) })
        Assert-Check "Aucune valeur secrète de .env dans tout l'historique Git ($((git rev-list --all).Count) commits)" ($found.Count -eq 0)
        Assert-Check ".env ignoré et jamais commité ; uploads/, modèle et dataset ignorés" ((git check-ignore .env) -and -not (git log --all --format=%H -- .env) -and
            (git check-ignore backend/uploads/cvs/x.pdf) -and (git check-ignore ai-service/ml/artifacts/turnover_model.joblib) -and (git check-ignore ai-service/ml/data/emp_attrition.csv))
        Assert-Check "Aucune clé privée ni jeton JWT dans l'historique" (-not ($history -match "BEGIN (RSA |EC )?PRIVATE KEY") -and -not ($history -match "eyJhbGciOi[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}"))
    } finally { Pop-Location }
} finally {
    Stop-Ai $ai
    Remove-Item $aiLog, "$aiLog.out" -ErrorAction SilentlyContinue
    "--- Nettoyage ---"
    foreach ($u in $users.Values) { if ($u.Id) { Invoke-Api DELETE "/api/admin/users/$($u.Id)" -Token $admin | Out-Null } }
    foreach ($id in $companies) { if ($id) { Invoke-Api DELETE "/api/admin/companies/$id" -Token $admin | Out-Null } }
    Assert-Check "MySQL et disque : plus aucun compte, offre ni CV de test" ((Invoke-Sql "SELECT COUNT(*) FROM users WHERE email LIKE 'e2e13-%'")[0][0] -eq "0" -and
        (Invoke-Sql "SELECT COUNT(*) FROM job_offers WHERE title LIKE '%$suffix'")[0][0] -eq "0" -and
        @(Get-ChildItem (Join-Path $root "backend\uploads\cvs") -File -ErrorAction SilentlyContinue).Count -eq 0)
}

Write-Summary
