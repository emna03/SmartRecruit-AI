# Vérification réelle de l'ÉTAPE 10 : candidat -> Spring Boot (catalogue des offres publiées) -> FastAPI (chatbot)
# -> réponse construite sur les vraies offres de MySQL -> API candidat.
#
# Prérequis : MySQL (conteneur smartrecruit-mysql) et le backend démarrés, ai-service/.venv installé,
# service IA ARRÊTÉ (le script le démarre et l'arrête lui-même pour tester la panne).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step10.ps1 [-BaseUrl http://localhost:8080]
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
$keyHeader = @{ "X-Internal-Api-Key" = $aiKey }
$script:aiLogs = ""
function Stop-AiKeepLog($process) {
    Stop-Ai $process
    if (Test-Path $aiLog) { $script:aiLogs += Get-Content $aiLog -Raw -Encoding UTF8 }
}
function Ids($offers) { return @($offers | ForEach-Object { [long]$_.id } | Sort-Object) }
function SameList($actual, $expected) { return (@($actual) -join "|") -eq (@($expected) -join "|") }

$adminToken = (Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }).Json.accessToken
$suffix = -join ((97..122) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
# Compétence inventée pour ce test : seules les offres créées ici la demandent, les résultats attendus sont donc exacts.
$skill = "Zorg" + $suffix
$users = @{}
function Register([string]$key, [string]$firstName, [string]$role) {
    $email = "e2e10-$key-$suffix@test.local"
    $r = Invoke-Api POST /api/auth/register @{ email = $email; password = "Passw0rd!"; firstName = $firstName; lastName = "E2E"; role = $role }
    $script:users[$key] = [pscustomobject]@{ Email = $email; Token = $r.Json.accessToken; Id = $r.Json.user.id }
    return $script:users[$key]
}
$offers = @()
function NewOffer([string]$title, [string[]]$skills, [string]$contract, [string]$city, [int]$years, $level, $salaryMin, $salaryMax, [string]$status) {
    $body = @{ title = $title; description = "Offre de test E2E étape 10 (chatbot)"; location = $city; contractType = $contract;
        minExperienceYears = $years; educationLevel = $level; salaryMin = $salaryMin; salaryMax = $salaryMax; requiredSkills = $skills; status = $status }
    $r = Invoke-Api POST /api/recruiter/jobs $body -Token $rec.Token
    if ($r.Code -ne 201) { throw "Création de l'offre « $title » refusée : $($r.Code)" }
    $script:offers += $r.Json.id
    return $r.Json.id
}
function Ask($user, [string]$message, $offerId = $null) {
    return Invoke-Api POST /api/candidate/chatbot/messages @{ message = $message; jobOfferId = $offerId } -Token $user.Token
}
# Offres publiées demandant une compétence, lues directement dans MySQL (référence pour vérifier le chatbot).
function PublishedWith([string]$name) {
    $rows = Invoke-Sql ("SELECT o.id FROM job_offers o JOIN job_offer_skills js ON js.job_offer_id = o.id JOIN skills s ON s.id = js.skill_id " +
        "WHERE o.status = 'PUBLIEE' AND s.name = '$name'")
    return @($rows | ForEach-Object { [long]$_[0] } | Sort-Object)
}
# Chaque offre citée existe dans MySQL, est publiée et demande bien la compétence ; le total correspond à MySQL.
function GroundedSearch($reply, [string]$name) {
    $expected = PublishedWith $name
    $shown = Ids $reply.offers
    $grounded = @($shown | Where-Object { $expected -contains $_ }).Count -eq $shown.Count
    $cards = @($reply.offers | Where-Object { $_.requiredSkills -contains $name }).Count -eq $shown.Count
    return ($shown.Count -gt 0 -and $shown.Count -le 5 -and $grounded -and $cards -and $reply.totalMatches -eq $expected.Count)
}

$rec = Register "rec" "Rania" "RECRUTEUR"
$companyId = (Invoke-Api POST /api/recruiter/company @{ name = "E2E10 Corp $suffix"; sector = "IT"; location = "Tunis" } -Token $rec.Token).Json.id
$cand = Register "cand" "Camille" "CANDIDAT"
$titleA = "Ingénieur Plateforme $suffix"
$titleB = "Stagiaire Front $suffix"
$offerA = NewOffer $titleA @("Java", "Spring Boot", $skill) "CDI" "Tunis" 3 "BAC_PLUS_5" 2800 3600 "PUBLIEE"
$offerB = NewOffer $titleB @($skill, "Angular") "STAGE" "Sousse" 0 $null $null $null "PUBLIEE"
$draft = NewOffer "Brouillon secret $suffix" @($skill, "Python") "CDI" "Tunis" 1 $null $null $null "BROUILLON"
$ai = $null

try {
    "--- Service IA FastAPI : POST /api/chatbot/ask (appel direct) ---"
    $ai = Start-Ai
    $status = Invoke-Api GET "$aiUrl/api/status" -Headers $keyHeader
    Assert-Check "GET /api/status : module chatbot disponible" ($status.Code -eq 200 -and $status.Json.modules.chatbot -eq $true)
    $catalogue = @(@{ id = 1; title = "Développeur Java"; company = "Acme"; location = "Tunis"; contractType = "CDI"; minExperienceYears = 2;
        educationLevel = "BAC_PLUS_5"; salaryMin = $null; salaryMax = $null; requiredSkills = @("Java", "MySQL") })
    $direct = @{ message = "Quelles offres demandent Java ?"; contextOfferId = $null; offers = $catalogue }
    Assert-Check "Sans clé interne -> 401" ((Invoke-Api POST "$aiUrl/api/chatbot/ask" $direct).Code -eq 401)
    $r = Invoke-Api POST "$aiUrl/api/chatbot/ask" $direct -Headers $keyHeader
    Assert-Check "Avec la clé -> 200, SEARCH_OFFERS, offre 1 seulement" ($r.Code -eq 200 -and $r.Json.intent -eq "SEARCH_OFFERS" -and
        (SameList $r.Json.offerIds @(1)) -and $r.Json.chatbotVersion -eq "1.0.0")
    $r2 = Invoke-Api POST "$aiUrl/api/chatbot/ask" $direct -Headers $keyHeader
    Assert-Check "Même entrée -> même réponse (déterministe)" (($r.Json | ConvertTo-Json -Depth 5) -eq ($r2.Json | ConvertTo-Json -Depth 5))
    $withEmail = @{ message = "Bonjour"; contextOfferId = $null; offers = @(@{ id = 1; title = "X"; company = "Y"; location = $null; contractType = "CDI";
        minExperienceYears = 0; educationLevel = $null; salaryMin = $null; salaryMax = $null; requiredSkills = @(); email = "x@y.z" }) }
    Assert-Check "Champ personnel non prévu (email) -> 400" ((Invoke-Api POST "$aiUrl/api/chatbot/ask" $withEmail -Headers $keyHeader).Code -eq 400)
    $empty = @{ message = "   "; contextOfferId = $null; offers = @() }
    Assert-Check "Question vide -> 400" ((Invoke-Api POST "$aiUrl/api/chatbot/ask" $empty -Headers $keyHeader).Code -eq 400)

    "--- Sécurité de l'API candidat ---"
    Assert-Check "Anonyme -> 401" ((Invoke-Api POST /api/candidate/chatbot/messages @{ message = "Bonjour" }).Code -eq 401)
    Assert-Check "Jeton invalide -> 401" ((Invoke-Api POST /api/candidate/chatbot/messages @{ message = "Bonjour" } -Token "abc.def.ghi").Code -eq 401)
    Assert-Check "Recruteur -> 403" ((Ask $rec "Bonjour").Code -eq 403)
    Assert-Check "Administrateur -> 403" ((Invoke-Api POST /api/candidate/chatbot/messages @{ message = "Bonjour" } -Token $adminToken).Code -eq 403)
    $r = Ask $cand ""
    Assert-Check "Question vide -> 400 « La question ne peut pas être vide »" ($r.Code -eq 400 -and ($r.Json | ConvertTo-Json -Depth 5) -match "ne peut pas")
    Assert-Check "Question blanche -> 400" ((Ask $cand "    ").Code -eq 400)
    Assert-Check "Question de 501 caractères -> 400" ((Ask $cand ("a" * 501)).Code -eq 400)

    "--- Recherche d'offres par compétence (vraies offres MySQL) ---"
    $r = Ask $cand "Quelles offres demandent $skill ?"
    Assert-Check "Compétence $skill -> exactement les 2 offres publiées (brouillon exclu)" ($r.Code -eq 200 -and $r.Json.intent -eq "SEARCH_OFFERS" -and
        (SameList (Ids $r.Json.offers) (@($offerA, $offerB) | Sort-Object)) -and $r.Json.totalMatches -eq 2 -and
        (SameList (PublishedWith $skill) (@($offerA, $offerB) | Sort-Object)))
    Assert-Check "Cartes reconstruites depuis MySQL (titre, entreprise, compétences)" (
        @($r.Json.offers | Where-Object { $_.id -eq $offerA -and $_.title -eq $titleA -and $_.companyName -eq "E2E10 Corp $suffix" -and
            $_.requiredSkills -contains "Spring Boot" }).Count -eq 1 -and $r.Json.answer -like "J'ai trouvé 2 offres publiées qui demandent $skill*")
    Assert-Check "Le brouillon n'apparaît jamais" (-not ((Ids $r.Json.offers) -contains [long]$draft) -and -not $r.Json.answer.Contains("Brouillon"))
    foreach ($q in @(@("Quelles offres correspondent à Java ?", "Java"), @("Je cherche un poste Angular.", "Angular"), @("Quelles offres demandent Spring Boot ?", "Spring Boot"))) {
        $r = Ask $cand $q[0]
        Assert-Check ("« {0} » -> offres publiées demandant {1} ({2} au total)" -f $q[0], $q[1], $r.Json.totalMatches) ($r.Code -eq 200 -and
            $r.Json.intent -eq "SEARCH_OFFERS" -and (GroundedSearch $r.Json $q[1]) -and $r.Json.entities.skills -contains $q[1])
    }
    $r = Ask $cand "Je cherche un stage $skill"
    Assert-Check "Stage + $skill -> seulement l'offre B (filtre contrat)" ((SameList (Ids $r.Json.offers) @($offerB)) -and
        $r.Json.entities.contractType -eq "STAGE" -and $r.Json.contextOffer.id -eq $offerB)
    $r = Ask $cand "Je cherche un poste de pilote d'hélicoptère"
    Assert-Check "Métier absent du catalogue -> aucune offre, aucune invention" ($r.Code -eq 200 -and $r.Json.offers.Count -eq 0 -and
        $r.Json.totalMatches -eq 0 -and $r.Json.answer -like "Je n'ai trouvé aucune offre publiée*")

    "--- Questions sur une offre (contexte) ---"
    $r = Ask $cand "Quelles sont les compétences demandées pour cette offre ?" $offerA
    Assert-Check "Compétences de l'offre A : Java, Spring Boot, $skill" ($r.Json.intent -eq "OFFER_SKILLS" -and $r.Json.contextOffer.id -eq $offerA -and
        $r.Json.answer -like "*$titleA*" -and $r.Json.answer -like "*Java*" -and $r.Json.answer -like "*Spring Boot*" -and $r.Json.answer -like "*$skill*")
    $r = Ask $cand "Combien d'années d'expérience sont nécessaires ?" $offerA
    Assert-Check "Expérience de l'offre A : au moins 3 ans" ($r.Json.intent -eq "OFFER_EXPERIENCE" -and $r.Json.answer -like "*au moins 3 ans d'expérience*")
    $r = Ask $cand "J'ai 2 ans d'expérience, est-ce suffisant pour cette offre ?" $offerA
    Assert-Check "2 ans pour 3 demandés -> en dessous du minimum, décision du recruteur" ($r.Json.intent -eq "OFFER_EXPERIENCE" -and
        $r.Json.entities.experienceYears -eq 2 -and $r.Json.answer -like "*en dessous du minimum*")
    $r = Ask $cand "Quel niveau d'étude est demandé ?" $offerA
    Assert-Check "Formation de l'offre A : Bac+5" ($r.Json.intent -eq "OFFER_EDUCATION" -and $r.Json.answer -like "*Bac+5*")
    $r = Ask $cand "Quel niveau d'étude est demandé ?" $offerB
    Assert-Check "Formation de l'offre B : aucun niveau minimum indiqué" ($r.Json.intent -eq "OFFER_EDUCATION" -and $r.Json.answer -like "*n'indique pas de niveau d'études minimum*")
    $r = Ask $cand "Quel est le salaire ?" $offerA
    Assert-Check "Salaire de l'offre A : entre 2 800 et 3 600" ($r.Json.intent -eq "OFFER_SALARY" -and $r.Json.answer -match "entre 2.800 et 3.600")
    $r = Ask $cand "Combien d'années d'expérience pour l'offre « $titleB » ?"
    Assert-Check "Titre cité dans la question -> offre B, ouverte aux débutants" ($r.Json.intent -eq "OFFER_EXPERIENCE" -and
        $r.Json.contextOffer.id -eq $offerB -and $r.Json.answer -like "*ouverte aux débutants*")
    $r = Ask $cand "Quelles sont les compétences demandées pour l'offre « Astronaute lunaire » ?"
    Assert-Check "Offre inexistante -> le dit clairement, aucune offre citée" ($r.Code -eq 200 -and $r.Json.offers.Count -eq 0 -and
        $null -eq $r.Json.contextOffer -and $r.Json.answer -like "Je ne trouve aucune offre publiée intitulée*")
    $r = Ask $cand "Quel niveau d'étude est demandé ?" $draft
    Assert-Check "Brouillon comme contexte -> ignoré (« De quelle offre parlez-vous ? »)" ($r.Code -eq 200 -and $null -eq $r.Json.contextOffer -and
        $r.Json.answer -like "De quelle offre parlez-vous*")
    $r = Ask $cand "Quel niveau d'étude est demandé ?" 999999999
    Assert-Check "Offre inexistante comme contexte -> ignorée" ($r.Code -eq 200 -and $null -eq $r.Json.contextOffer)

    "--- Fonctionnement de la plateforme ---"
    $r = Ask $cand "Comment postuler à une offre ?"
    Assert-Check "FAQ_APPLY : bouton « Postuler à cette offre » + lien vers les offres" ($r.Json.intent -eq "FAQ_APPLY" -and
        $r.Json.answer -like "*Postuler à cette offre*" -and $r.Json.actions -contains "OPEN_JOBS")
    $r = Ask $cand "Comment importer mon CV ?"
    Assert-Check "FAQ_CV : page « Mon CV » + lien" ($r.Json.intent -eq "FAQ_CV" -and $r.Json.answer -like "*Mon CV*" -and $r.Json.actions -contains "OPEN_CV")
    $r = Ask $cand "Comment fonctionne le score de compatibilité ?"
    Assert-Check "FAQ_MATCHING : pondération 60/25/15, pas une décision automatique" ($r.Json.intent -eq "FAQ_MATCHING" -and
        $r.Json.answer -like "*60*25*15*" -and $r.Json.answer -like "*jamais une décision automatique*")
    $r = Ask $cand "Quelle est la recette de la pâte à crêpes ?"
    Assert-Check "Question hors sujet -> UNKNOWN « Je ne sais pas répondre »" ($r.Json.intent -eq "UNKNOWN" -and
        $r.Json.answer -like "Je ne sais pas répondre*" -and $r.Json.offers.Count -eq 0)

    "--- Service IA indisponible ---"
    Stop-AiKeepLog $ai
    $ai = $null
    $start = Get-Date
    $r = Ask $cand "Quelles offres demandent Java ?"
    $elapsed = ((Get-Date) - $start).TotalSeconds
    Assert-Check ("Chatbot -> 503 « momentanément indisponible » ({0:N1} s)" -f $elapsed) ($r.Code -eq 503 -and
        $r.Json.message -eq "Le service IA est momentanément indisponible. Réessayez plus tard." -and $elapsed -lt 5)
    Assert-Check "Le reste de la plateforme fonctionne (GET /api/jobs -> 200)" ((Invoke-Api GET /api/jobs).Code -eq 200)
    Assert-Check "Candidature possible pendant la panne -> 201" ((Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offerA } -Token $cand.Token).Code -eq 201)

    "--- Journaux : ni question, ni donnée personnelle ---"
    $log = $script:aiLogs
    Assert-Check "Journal FastAPI : « Chatbot : intention SEARCH_OFFERS (confiance ...) »" ($log -match "Chatbot : intention SEARCH_OFFERS \(confiance \d\.\d\d\), \d+ offres reçues, 2 citées, version 1\.0\.0")
    $leaks = @("pilote", "Astronaute", "crêpes", "suffisant", $skill, $suffix, $cand.Email, "Camille") | Where-Object { $log.Contains($_) }
    Assert-Check "Journal FastAPI : aucune question, compétence, titre ni email" ($leaks.Count -eq 0)
    $backendLog = Join-Path $env:TEMP "sr-backend.log"
    if (Test-Path $backendLog) {
        $b = Get-Content $backendLog -Raw -Encoding UTF8
        $leaks = @("pilote", "Astronaute", "crêpes", "suffisant", $skill, $cand.Email) | Where-Object { $b.Contains($_) }
        Assert-Check "Journal Spring Boot : intention et nombres seulement" ($leaks.Count -eq 0 -and $b -match "Chatbot : intention SEARCH_OFFERS, 2 offre\(s\) citée\(s\) sur \d+ envoyée\(s\)")
    }
} finally {
    Stop-Ai $ai
    Remove-Item $aiLog, "$aiLog.out" -ErrorAction SilentlyContinue
    "--- Nettoyage (comptes, offres puis entreprise de test, par l'admin) ---"
    foreach ($u in $users.Values) { if ($u.Id) { Invoke-Api DELETE "/api/admin/users/$($u.Id)" -Token $adminToken | Out-Null } }
    foreach ($id in $offers) { Invoke-Api DELETE "/api/admin/jobs/$id" -Token $adminToken | Out-Null }
    if ($companyId) { Invoke-Api DELETE "/api/admin/companies/$companyId" -Token $adminToken | Out-Null }
    $left = (Invoke-Sql "SELECT COUNT(*) FROM job_offers WHERE title LIKE '%$suffix'")[0][0]
    $leftCompany = (Invoke-Sql "SELECT COUNT(*) FROM companies WHERE name = 'E2E10 Corp $suffix'")[0][0]
    Assert-Check "Offres et entreprise de test supprimées de MySQL" ($left -eq "0" -and $leftCompany -eq "0")
}

Write-Summary
