# Test de bout en bout de l'authentification sur le backend lancé (MySQL).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-auth.ps1 [-BaseUrl http://localhost:8080]
# Le mot de passe admin est lu dans .env (ADMIN_EMAIL / ADMIN_PASSWORD). Les comptes de test sont supprimés à la fin.
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"

function Get-EnvValue([string]$key) {
    $line = Get-Content $envFile | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if (-not $line) { throw "$key absent de $envFile" }
    return ($line -split '=', 2)[1]
}

function Invoke-Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
    $params = @{
        UseBasicParsing = $true
        Method          = $Method
        Uri             = "$BaseUrl$Path"
        Headers         = @{}
        ContentType     = "application/json; charset=utf-8"
    }
    if ($Token) { $params.Headers.Authorization = "Bearer $Token" }
    if ($null -ne $Body) {
        [byte[]]$bytes = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
        $params.Body = $bytes
    }
    try {
        $response = Invoke-WebRequest @params
        $code = [int]$response.StatusCode
        $text = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    } catch {
        $errorResponse = $_.Exception.Response
        if (-not $errorResponse) { throw }
        $code = [int]$errorResponse.StatusCode
        $text = (New-Object IO.StreamReader($errorResponse.GetResponseStream(), [Text.Encoding]::UTF8)).ReadToEnd()
    }
    $json = $null
    if ($text) { $json = $text | ConvertFrom-Json }
    return [pscustomobject]@{ Code = $code; Json = $json }
}

$script:passed = 0
$script:failed = 0
function Assert-Check([string]$Name, [bool]$Condition) {
    if ($Condition) { $script:passed++; $status = "OK" } else { $script:failed++; $status = "KO" }
    "{0,-66} {1}" -f $Name, $status
}

$password = "E2eTest123"
$adminEmail = Get-EnvValue "ADMIN_EMAIL"
$adminPassword = Get-EnvValue "ADMIN_PASSWORD"

$r = Invoke-Api POST /api/auth/register @{ email = "e2e-candidat@test.local"; password = $password; firstName = "Élodie"; lastName = "Test"; role = "CANDIDAT" }
$candidateToken = $r.Json.accessToken
Assert-Check "E01 inscription candidat -> 201 + jeton" ($r.Code -eq 201 -and [bool]$candidateToken)

$r = Invoke-Api POST /api/auth/register @{ email = "e2e-recruteur@test.local"; password = $password; firstName = "Bob"; lastName = "Test"; role = "RECRUTEUR" }
$recruiterToken = $r.Json.accessToken
Assert-Check "E02 inscription recruteur -> 201" ($r.Code -eq 201)

$r = Invoke-Api POST /api/auth/register @{ email = "e2e-admin@test.local"; password = $password; firstName = "X"; lastName = "Y"; role = "ADMIN" }
Assert-Check "E03 inscription avec le rôle ADMIN refusée -> 400" ($r.Code -eq 400)

$r = Invoke-Api POST /api/auth/register @{ email = "E2E-CANDIDAT@test.local"; password = $password; firstName = "A"; lastName = "B"; role = "CANDIDAT" }
Assert-Check "E04 email déjà utilisé (casse différente) -> 409" ($r.Code -eq 409)

$r = Invoke-Api POST /api/auth/register @{ email = "pas-un-email"; password = "court"; firstName = ""; lastName = "B"; role = "CANDIDAT" }
$fields = if ($r.Json.fieldErrors) { ($r.Json.fieldErrors.PSObject.Properties.Name) -join ", " } else { "" }
Assert-Check "E05 données invalides -> 400 ($fields)" ($r.Code -eq 400 -and [bool]$r.Json.fieldErrors.email -and [bool]$r.Json.fieldErrors.password)

$r = Invoke-Api POST /api/auth/login @{ email = "e2e-candidat@test.local"; password = $password }
Assert-Check "E06 connexion candidat -> 200" ($r.Code -eq 200 -and $r.Json.user.role -eq "CANDIDAT")

$r = Invoke-Api POST /api/auth/login @{ email = "e2e-candidat@test.local"; password = "Mauvais123" }
Assert-Check "E07 mauvais mot de passe -> 401" ($r.Code -eq 401)

$r = Invoke-Api GET /api/auth/me
Assert-Check "E08 /api/auth/me sans jeton -> 401" ($r.Code -eq 401)

$r = Invoke-Api GET /api/auth/me $null "abc.def.ghi"
Assert-Check "E09 /api/auth/me avec jeton invalide -> 401" ($r.Code -eq 401)

$r = Invoke-Api GET /api/auth/me $null $candidateToken
Assert-Check "E10 /api/auth/me avec jeton -> 200, accents conservés ($($r.Json.firstName))" ($r.Code -eq 200 -and $r.Json.firstName -eq "Élodie")

$r = Invoke-Api GET /api/admin/users $null $candidateToken
Assert-Check "E11 candidat sur /api/admin/** -> 403" ($r.Code -eq 403)

$r = Invoke-Api GET /api/candidate/applications $null $recruiterToken
Assert-Check "E12 recruteur sur /api/candidate/** -> 403" ($r.Code -eq 403)

$r = Invoke-Api POST /api/auth/login @{ email = $adminEmail; password = $adminPassword }
$adminToken = $r.Json.accessToken
Assert-Check "E13 connexion admin (compte créé depuis .env) -> 200" ($r.Code -eq 200 -and $r.Json.user.role -eq "ADMIN")

$r = Invoke-Api GET "/api/admin/users?role=CANDIDAT" $null $adminToken
$candidates = @($r.Json)
Assert-Check "E14 admin liste les candidats -> 200 ($($candidates.Count) candidat(s))" ($r.Code -eq 200 -and ($candidates | Where-Object role -ne "CANDIDAT").Count -eq 0)

$candidateId = ($candidates | Where-Object email -eq "e2e-candidat@test.local").id
$r = Invoke-Api PATCH "/api/admin/users/$candidateId/status" @{ enabled = $false } $adminToken
Assert-Check "E15 admin désactive le candidat -> 200" ($r.Code -eq 200 -and $r.Json.enabled -eq $false)

$r = Invoke-Api POST /api/auth/login @{ email = "e2e-candidat@test.local"; password = $password }
Assert-Check "E16 connexion d'un compte désactivé -> 403" ($r.Code -eq 403)

$r = Invoke-Api GET /api/auth/me $null $candidateToken
Assert-Check "E17 ancien jeton d'un compte désactivé -> 401" ($r.Code -eq 401)

$all = Invoke-Api GET /api/admin/users $null $adminToken
foreach ($user in @($all.Json) | Where-Object { $_.email -like "e2e-*@test.local" }) {
    $d = Invoke-Api DELETE "/api/admin/users/$($user.id)" $null $adminToken
    Assert-Check "E18 suppression du compte de test $($user.email) -> 204" ($d.Code -eq 204)
}

""
"Résultat : $script:passed OK, $script:failed KO"
if ($script:failed -gt 0) { exit 1 }
