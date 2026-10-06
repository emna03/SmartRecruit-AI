# Vérification réelle de l'ÉTAPE 12 : données créées par l'API (vrais CV analysés par le CV Parser, vrais scores de
# matching FastAPI) -> MySQL -> endpoints de statistiques. Chaque chiffre des trois dashboards est comparé à une
# requête SQL directe sur MySQL, avant et après de nouvelles candidatures et des changements de statut.
#
# Prérequis : MySQL (conteneur smartrecruit-mysql) et le backend démarrés, ai-service/.venv installé,
# service IA ARRÊTÉ (le script le démarre et l'arrête lui-même).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step12.ps1 [-BaseUrl http://localhost:8080]
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

$appStatuses = @("EN_ATTENTE", "PRESELECTIONNE", "ACCEPTE", "REFUSE")
$offerStatuses = @("BROUILLON", "PUBLIEE", "FERMEE")
$roles = @("ADMIN", "RECRUTEUR", "CANDIDAT")
$cvStatuses = @("EN_COURS", "TERMINE", "ECHEC")
$contracts = @("CDI", "CDD", "STAGE", "ALTERNANCE", "FREELANCE")
# Même période que le backend : les 6 derniers mois UTC, mois en cours compris.
$currentMonth = [DateTime]::UtcNow.ToString("yyyy-MM")
$monthStart = [DateTime]::UtcNow.ToString("yyyy-MM-01 00:00:00")
$periodStart = [DateTime]::new([DateTime]::UtcNow.Year, [DateTime]::UtcNow.Month, 1).AddMonths(-5).ToString("yyyy-MM-dd HH:mm:ss")

function Scalar([string]$query) { return [long](Invoke-Sql $query)[0][0] }
function SqlCounts([string]$query) {
    $map = @{}
    foreach ($row in (Invoke-Sql $query)) { $map[$row[0]] = [long]$row[1] }
    return $map
}
# Chaque clé de l'énumération doit être présente dans la réponse, avec 0 quand MySQL n'a aucune ligne.
function SameCounts($json, $sqlMap, [string[]]$keys) {
    foreach ($k in $keys) {
        $expected = if ($sqlMap.ContainsKey($k)) { $sqlMap[$k] } else { 0 }
        if ($null -eq $json.$k -or [long]$json.$k -ne $expected) { return $false }
    }
    return $true
}
# Lignes SQL au format « a:b|c:d », nombres normalisés (« 61.80 » -> « 61.8 ») pour les comparer au JSON.
function Normalize([string]$value) {
    $number = 0.0
    if ([double]::TryParse($value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$number)) { return "$number" }
    return $value
}
function SqlList([string]$query) { return (@((Invoke-Sql $query) | ForEach-Object { ($_ | ForEach-Object { Normalize $_ }) -join ":" }) -join "|") }
function SqlAverage([string]$value) { if ($value -eq "NULL") { return $null } return [double]$value }
function CandidateId([string]$email) {
    return Scalar "SELECT p.id FROM candidate_profiles p JOIN users u ON u.id = p.user_id WHERE u.email = '$email'"
}
function Dashboard([string]$path, $user) {
    $start = Get-Date
    $r = Invoke-Api GET $path -Token $user.Token
    $script:slowest = [Math]::Max($script:slowest, ((Get-Date) - $start).TotalMilliseconds)
    if ($r.Code -ne 200) { throw "$path -> $($r.Code)" }
    return $r.Json
}
$script:slowest = 0
# Dans les fonctions qui renvoient un dashboard, le résultat du contrôle est affiché sans polluer la valeur renvoyée.
function Check([string]$Name, [bool]$Condition) { Assert-Check $Name $Condition | Out-Host }

function Check-Candidate($user, [string]$label) {
    $cid = CandidateId $user.Email
    $d = Dashboard /api/candidate/dashboard $user
    $visible = "FROM matching_results m JOIN job_offers o ON o.id = m.job_offer_id WHERE m.candidate_id = $cid AND (o.status = 'PUBLIEE' " +
        "OR EXISTS (SELECT 1 FROM applications a WHERE a.candidate_id = $cid AND a.job_offer_id = o.id))"
    $cv = Invoke-Sql "SELECT c.parsing_status, (SELECT COUNT(*) FROM cv_skills s WHERE s.cv_id = c.id) FROM cvs c WHERE c.candidate_id = $cid"
    $state = if ($cv.Count -eq 0) { "ABSENT" } elseif ($cv[0][0] -eq "TERMINE") { "ANALYSE" } else { "IMPORTE" }
    Check "$label : candidatures (total et par statut) = MySQL" ($d.totalApplications -eq (Scalar "SELECT COUNT(*) FROM applications WHERE candidate_id = $cid") -and
        (SameCounts $d.applicationsByStatus (SqlCounts "SELECT status, COUNT(*) FROM applications WHERE candidate_id = $cid GROUP BY status") $appStatuses))
    Check "$label : offres publiées / non postulées = MySQL" ($d.publishedOffers -eq (Scalar "SELECT COUNT(*) FROM job_offers WHERE status = 'PUBLIEE'") -and
        $d.publishedOffersNotApplied -eq (Scalar ("SELECT COUNT(*) FROM job_offers o WHERE o.status = 'PUBLIEE' AND NOT EXISTS " +
            "(SELECT 1 FROM applications a WHERE a.job_offer_id = o.id AND a.candidate_id = $cid)")))
    Check "$label : état du CV $state = MySQL" ($d.cv.state -eq $state -and
        ($cv.Count -eq 0 -or ($d.cv.parsingStatus -eq $cv[0][0] -and $d.cv.extractedSkills -eq [long]$cv[0][1])))
    $expected = SqlList "SELECT o.id, m.score $visible ORDER BY m.score DESC, m.computed_at DESC, o.id DESC LIMIT 5"
    $actual = @($d.topMatches | ForEach-Object { "$($_.jobOfferId):$([double]$_.score)" }) -join "|"
    Check "$label : meilleurs scores (offre:score) = MySQL" ($d.scoredOffers -eq (Scalar "SELECT COUNT(*) $visible") -and $actual -eq $expected)
    return $d
}

function Check-Recruiter($user, $companyId, [string]$label) {
    $d = Dashboard /api/recruiter/dashboard $user
    $apps = "FROM applications a JOIN job_offers o ON o.id = a.job_offer_id WHERE o.company_id = $companyId"
    $scored = "FROM applications a JOIN job_offers o ON o.id = a.job_offer_id JOIN matching_results m " +
        "ON m.candidate_id = a.candidate_id AND m.job_offer_id = a.job_offer_id WHERE o.company_id = $companyId"
    Check "$label : offres (total et par statut) = MySQL" ($d.hasCompany -and $d.totalOffers -eq (Scalar "SELECT COUNT(*) FROM job_offers WHERE company_id = $companyId") -and
        (SameCounts $d.offersByStatus (SqlCounts "SELECT status, COUNT(*) FROM job_offers WHERE company_id = $companyId GROUP BY status") $offerStatuses))
    Check "$label : candidatures (total et par statut) = MySQL" ($d.totalApplications -eq (Scalar "SELECT COUNT(*) $apps") -and
        (SameCounts $d.applicationsByStatus (SqlCounts "SELECT a.status, COUNT(*) $apps GROUP BY a.status") $appStatuses))
    $perOffer = SqlList "SELECT o.id, COUNT(a.id) FROM job_offers o LEFT JOIN applications a ON a.job_offer_id = o.id WHERE o.company_id = $companyId GROUP BY o.id ORDER BY o.id"
    $actual = @($d.offers | Sort-Object id | ForEach-Object { "$($_.id):$($_.applications)" }) -join "|"
    Check "$label : candidats par offre (uniquement ses offres) = MySQL" ($actual -eq $perOffer)
    $stats = (Invoke-Sql "SELECT COUNT(*), ROUND(AVG(m.score), 1) $scored")[0]
    Check "$label : scores disponibles et moyenne = MySQL" ($d.scoredApplications -eq [long]$stats[0] -and $d.averageMatchingScore -eq (SqlAverage $stats[1]))
    $perOfferScores = SqlList "SELECT o.id, COUNT(*), ROUND(AVG(m.score), 1), MAX(m.score) $scored GROUP BY o.id ORDER BY o.id"
    $actual = @($d.offers | Where-Object { $_.scoredApplications -gt 0 } | Sort-Object id |
        ForEach-Object { "$($_.id):$($_.scoredApplications):$([double]$_.averageScore):$([double]$_.bestScore)" }) -join "|"
    Check "$label : score moyen et meilleur score par offre = MySQL" ($actual -eq $perOfferScores)
    $expected = SqlList "SELECT a.id, m.score $scored ORDER BY m.score DESC, a.created_at DESC, a.id DESC LIMIT 5"
    $actual = @($d.topCandidates | ForEach-Object { "$($_.applicationId):$([double]$_.score)" }) -join "|"
    Check "$label : meilleurs candidats (candidature:score) = MySQL" ($actual -eq $expected)
    $months = @($d.applicationsByMonth)
    Check "$label : candidatures par mois (6 mois, mois courant) = MySQL" ($months.Count -eq 6 -and $months[5].month -eq $currentMonth -and
        $months[5].count -eq (Scalar "SELECT COUNT(*) $apps AND a.created_at >= '$monthStart'") -and
        ($months | Measure-Object count -Sum).Sum -eq (Scalar "SELECT COUNT(*) $apps AND a.created_at >= '$periodStart'"))
    return $d
}

function Check-Admin([string]$label) {
    $d = Dashboard /api/admin/dashboard $admin
    Check "$label : utilisateurs (total, par rôle, désactivés) = MySQL" ($d.totalUsers -eq (Scalar "SELECT COUNT(*) FROM users") -and
        (SameCounts $d.usersByRole (SqlCounts "SELECT r.name, COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id GROUP BY r.name") $roles) -and
        $d.disabledUsers -eq (Scalar "SELECT COUNT(*) FROM users WHERE enabled = b'0'"))
    Check "$label : entreprises, recruteurs sans entreprise = MySQL" ($d.companies -eq (Scalar "SELECT COUNT(*) FROM companies") -and
        $d.recruitersWithoutCompany -eq (Scalar "SELECT COUNT(*) FROM recruiter_profiles WHERE company_id IS NULL"))
    Check "$label : offres (total, statut, contrats publiés) = MySQL" ($d.totalOffers -eq (Scalar "SELECT COUNT(*) FROM job_offers") -and
        (SameCounts $d.offersByStatus (SqlCounts "SELECT status, COUNT(*) FROM job_offers GROUP BY status") $offerStatuses) -and
        (SameCounts $d.publishedOffersByContract (SqlCounts "SELECT contract_type, COUNT(*) FROM job_offers WHERE status = 'PUBLIEE' GROUP BY contract_type") $contracts))
    Check "$label : candidatures (total et par statut) = MySQL" ($d.totalApplications -eq (Scalar "SELECT COUNT(*) FROM applications") -and
        (SameCounts $d.applicationsByStatus (SqlCounts "SELECT status, COUNT(*) FROM applications GROUP BY status") $appStatuses))
    $stats = (Invoke-Sql "SELECT COUNT(*), ROUND(AVG(score), 1) FROM matching_results")[0]
    Check "$label : CV par statut, scores stockés et moyenne = MySQL" ($d.candidatesWithCv -eq (Scalar "SELECT COUNT(*) FROM cvs") -and
        (SameCounts $d.cvsByParsingStatus (SqlCounts "SELECT parsing_status, COUNT(*) FROM cvs GROUP BY parsing_status") $cvStatuses) -and
        $d.storedMatchingScores -eq [long]$stats[0] -and $d.averageMatchingScore -eq (SqlAverage $stats[1]))
    $expected = SqlList ("SELECT s.name, COUNT(*) FROM job_offer_skills js JOIN skills s ON s.id = js.skill_id JOIN job_offers o ON o.id = js.job_offer_id " +
        "WHERE o.status = 'PUBLIEE' GROUP BY s.name ORDER BY COUNT(*) DESC, s.name LIMIT 8")
    $actual = @($d.topRequestedSkills | ForEach-Object { "$($_.skill):$($_.offers)" }) -join "|"
    Check "$label : compétences les plus demandées = MySQL" ($actual -eq $expected)
    $now = @($d.activityByMonth)[5]
    Check "$label : activité du mois courant (comptes, offres, candidatures) = MySQL" (@($d.activityByMonth).Count -eq 6 -and $now.month -eq $currentMonth -and
        $now.newUsers -eq (Scalar "SELECT COUNT(*) FROM users WHERE created_at >= '$monthStart'") -and
        $now.newOffers -eq (Scalar "SELECT COUNT(*) FROM job_offers WHERE created_at >= '$monthStart'") -and
        $now.applications -eq (Scalar "SELECT COUNT(*) FROM applications WHERE created_at >= '$monthStart'"))
    return $d
}

$admin = [pscustomobject]@{ Token = (Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }).Json.accessToken }
$suffix = -join ((97..122) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
$users = @{}
$companies = @()
function Register([string]$key, [string]$firstName, [string]$role) {
    $email = "e2e12-$key-$suffix@test.local"
    $r = Invoke-Api POST /api/auth/register @{ email = $email; password = "Passw0rd!"; firstName = $firstName; lastName = "E2E"; role = $role }
    if ($r.Code -ne 201) { throw "Inscription refusée ($email) : $($r.Code)" }
    $script:users[$key] = [pscustomobject]@{ Email = $email; Token = $r.Json.accessToken; Id = $r.Json.user.id }
    return $script:users[$key]
}
function NewCompany($user, [string]$name) {
    $id = (Invoke-Api POST /api/recruiter/company @{ name = "$name $suffix"; sector = "IT"; location = "Tunis" } -Token $user.Token).Json.id
    $script:companies += $id
    return $id
}
function NewOffer($user, [string]$title, [string]$contract, [string[]]$skills, [string]$status = "PUBLIEE") {
    $body = @{ title = "$title $suffix"; description = "Offre de test E2E étape 12"; location = "Tunis"; contractType = $contract;
        minExperienceYears = 0; requiredSkills = $skills; status = $status }
    $r = Invoke-Api POST /api/recruiter/jobs $body -Token $user.Token
    if ($r.Code -ne 201) { throw "Offre refusée : $($r.Code)" }
    return $r.Json.id
}
function Apply($user, $offer) {
    $r = Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offer } -Token $user.Token
    if ($r.Code -ne 201) { throw "Candidature refusée ($($user.Email)) : $($r.Code)" }
    return $r.Json.id
}
function SetStatus($user, $application, [string]$status) {
    return (Invoke-Api PATCH "/api/recruiter/applications/$application/status" @{ status = $status } -Token $user.Token).Code
}

$recA = Register "reca" "Rania" "RECRUTEUR"
$recB = Register "recb" "Bilel" "RECRUTEUR"
$solo = Register "solo" "Salma" "RECRUTEUR"
$fr = Register "fr" "Camille" "CANDIDAT"
$en = Register "en" "Alex" "CANDIDAT"
$nocv = Register "nocv" "Nora" "CANDIDAT"
$coA = NewCompany $recA "E2E12 Alpha"
$coB = NewCompany $recB "E2E12 Beta"
$java = NewOffer $recA "Développeur Full Stack Java" "CDI" @("Java", "Spring Boot", "Angular", "MySQL", "Docker")
$data = NewOffer $recA "Data Engineer" "CDD" @("Python", "SQL", "Docker")
$draft = NewOffer $recA "Brouillon RH" "STAGE" @("Excel") "BROUILLON"
$closed = NewOffer $recA "Offre close" "CDI" @("Java")
$beta = NewOffer $recB "Ingénieur DevOps" "FREELANCE" @("Kubernetes", "Docker", "Terraform")
$ai = $null

try {
    "--- Dashboards vides (nouveaux comptes) ---"
    $d = Check-Candidate $nocv "Candidat sans donnée"
    Assert-Check "Candidat sans donnée : 0 candidature, CV absent, aucun score" ($d.totalApplications -eq 0 -and $d.cv.state -eq "ABSENT" -and
        $d.scoredOffers -eq 0 -and @($d.topMatches).Count -eq 0)
    $d = Dashboard /api/recruiter/dashboard $solo
    Assert-Check "Recruteur sans entreprise : hasCompany=false, tout à 0" (-not $d.hasCompany -and $d.totalOffers -eq 0 -and $d.totalApplications -eq 0 -and
        @($d.offers).Count -eq 0 -and @($d.topCandidates).Count -eq 0 -and $null -eq $d.averageMatchingScore)
    Assert-Check "Fermeture d'une offre -> 200" ((Invoke-Api PATCH "/api/recruiter/jobs/$closed/status" @{ status = "FERMEE" } -Token $recA.Token).Code -eq 200)
    $d = Check-Recruiter $recA $coA "Recruteur A sans candidature"
    Assert-Check "Recruteur A : 4 offres = 2 publiées / 1 brouillon / 1 fermée, 0 candidature" ($d.totalOffers -eq 4 -and $d.offersByStatus.PUBLIEE -eq 2 -and
        $d.offersByStatus.BROUILLON -eq 1 -and $d.offersByStatus.FERMEE -eq 1 -and $d.totalApplications -eq 0)

    "--- Vrais CV analysés + scores de matching réels (FastAPI) ---"
    $ai = Start-Ai
    foreach ($c in @(@($fr, "cv_dev_fullstack_fr.pdf"), @($en, "cv_data_scientist_en.pdf"))) {
        $r = Invoke-Upload /api/candidate/cv ([IO.File]::ReadAllBytes((Join-Path $fixtures $c[1]))) $c[1] "application/pdf" $c[0].Token
        Assert-Check "Upload $($c[1]) -> TERMINE" ($r.Code -eq 200 -and $r.Json.parsingStatus -eq "TERMINE")
    }
    foreach ($pair in @(@($fr, $java), @($fr, $data), @($fr, $beta), @($en, $java), @($en, $data))) {
        $r = Invoke-Api GET "/api/candidate/jobs/$($pair[1])/matching" -Token $pair[0].Token
        if ($r.Json.status -ne "CALCULE") { throw "Matching non calculé : $($r.Json.status)" }
    }
    Assert-Check "MySQL : 5 scores réels enregistrés pour les candidats de test" ((Scalar ("SELECT COUNT(*) FROM matching_results WHERE candidate_id IN " +
        "($(CandidateId $fr.Email), $(CandidateId $en.Email))")) -eq 5)
    Stop-Ai $ai
    $ai = $null

    "--- Candidatures puis comparaison avec MySQL ---"
    $frJava = Apply $fr $java
    $enJava = Apply $en $java
    $nocvJava = Apply $nocv $java
    $frBeta = Apply $fr $beta
    $enData = Apply $en $data
    $d = Check-Candidate $fr "Candidat FR"
    Assert-Check "Candidat FR : 2 candidatures, CV analysé, 3 offres scorées" ($d.totalApplications -eq 2 -and $d.cv.state -eq "ANALYSE" -and $d.cv.extractedSkills -gt 0 -and
        $d.scoredOffers -eq 3 -and @($d.topMatches | Where-Object { $_.applied }).Count -eq 2)
    $null = Check-Candidate $en "Candidat EN"
    $d = Check-Candidate $nocv "Candidat sans CV"
    Assert-Check "Candidat sans CV : 1 candidature, aucun score" ($d.totalApplications -eq 1 -and $d.scoredOffers -eq 0)
    $d = Check-Recruiter $recA $coA "Recruteur A"
    Assert-Check "Recruteur A : 4 candidatures dont 3 avec score (le candidat sans CV n'en a pas)" ($d.totalApplications -eq 4 -and $d.scoredApplications -eq 3)
    $javaStats = $d.offers | Where-Object { $_.id -eq $java }
    Assert-Check "Recruteur A : offre Java -> 3 candidats, 2 scores" ($javaStats.applications -eq 3 -and $javaStats.scoredApplications -eq 2)
    Assert-Check "Recruteur A : meilleurs candidats = uniquement des candidatures à ses offres" (@($d.topCandidates | Where-Object { $_.jobOfferId -notin @($java, $data, $draft, $closed) }).Count -eq 0 -and
        @($d.topCandidates).Count -eq 3)

    "--- Isolation entre recruteurs ---"
    $b = Check-Recruiter $recB $coB "Recruteur B"
    Assert-Check "Recruteur B : 1 offre, 1 candidature, aucune donnée de l'entreprise A" ($b.totalOffers -eq 1 -and $b.totalApplications -eq 1 -and
        @($b.offers).Count -eq 1 -and $b.offers[0].id -eq $beta -and @($b.topCandidates).Count -eq 1 -and $b.topCandidates[0].applicationId -eq $frBeta)
    $json = (Invoke-Api GET /api/recruiter/dashboard -Token $recB.Token).Json | ConvertTo-Json -Depth 6
    Assert-Check "Recruteur B : ni titre d'offre ni candidat de A dans la réponse" (-not $json.Contains("Full Stack Java $suffix") -and -not $json.Contains("Data Engineer") -and
        -not $json.Contains("Alex"))
    Assert-Check "Paramètre ?companyId ignoré (le périmètre vient du jeton)" (((Invoke-Api GET "/api/recruiter/dashboard?companyId=$coA" -Token $recB.Token).Json.totalOffers) -eq 1)
    Assert-Check "Recruteur B ne peut pas changer le statut d'une candidature de A -> 403/404" ((SetStatus $recB $enJava "ACCEPTE") -in @(403, 404))

    "--- Dashboard admin ---"
    $d = Check-Admin "Admin"

    "--- Évolution : nouvelle candidature et changements de statut ---"
    $before = $d
    Assert-Check "Recruteur A : préselection EN -> 200, refus sans CV -> 200, acceptation FR -> 200" ((SetStatus $recA $enJava "PRESELECTIONNE") -eq 200 -and
        (SetStatus $recA $nocvJava "REFUSE") -eq 200 -and (SetStatus $recA $frJava "ACCEPTE") -eq 200)
    $null = Apply $nocv $data
    $d = Check-Candidate $fr "Candidat FR après statut"
    Assert-Check "Candidat FR : ACCEPTE 1, EN_ATTENTE 1" ($d.applicationsByStatus.ACCEPTE -eq 1 -and $d.applicationsByStatus.EN_ATTENTE -eq 1)
    $d = Check-Candidate $nocv "Candidat sans CV après nouvelle candidature"
    Assert-Check "Candidat sans CV : 2 candidatures (REFUSE 1, EN_ATTENTE 1)" ($d.totalApplications -eq 2 -and $d.applicationsByStatus.REFUSE -eq 1 -and
        $d.applicationsByStatus.EN_ATTENTE -eq 1)
    $d = Check-Recruiter $recA $coA "Recruteur A après changements"
    Assert-Check "Recruteur A : 5 candidatures, 1 de chaque statut final + 2 en attente" ($d.totalApplications -eq 5 -and $d.applicationsByStatus.ACCEPTE -eq 1 -and
        $d.applicationsByStatus.PRESELECTIONNE -eq 1 -and $d.applicationsByStatus.REFUSE -eq 1 -and $d.applicationsByStatus.EN_ATTENTE -eq 2)
    $null = Check-Recruiter $recB $coB "Recruteur B inchangé"
    $d = Check-Admin "Admin après changements"
    Assert-Check "Admin : +1 candidature, ACCEPTE +1, REFUSE +1" ($d.totalApplications -eq $before.totalApplications + 1 -and
        $d.applicationsByStatus.ACCEPTE -eq $before.applicationsByStatus.ACCEPTE + 1 -and $d.applicationsByStatus.REFUSE -eq $before.applicationsByStatus.REFUSE + 1)

    "--- Autorisations ---"
    foreach ($path in @("/api/candidate/dashboard", "/api/recruiter/dashboard", "/api/admin/dashboard")) {
        Assert-Check "Anonyme sur $path -> 401" ((Invoke-Api GET $path).Code -eq 401)
    }
    Assert-Check "Candidat sur les dashboards recruteur et admin -> 403" ((Invoke-Api GET /api/recruiter/dashboard -Token $fr.Token).Code -eq 403 -and
        (Invoke-Api GET /api/admin/dashboard -Token $fr.Token).Code -eq 403)
    Assert-Check "Recruteur sur les dashboards candidat et admin -> 403" ((Invoke-Api GET /api/candidate/dashboard -Token $recA.Token).Code -eq 403 -and
        (Invoke-Api GET /api/admin/dashboard -Token $recA.Token).Code -eq 403)
    Assert-Check "Admin sur les dashboards candidat et recruteur -> 403" ((Invoke-Api GET /api/candidate/dashboard -Token $admin.Token).Code -eq 403 -and
        (Invoke-Api GET /api/recruiter/dashboard -Token $admin.Token).Code -eq 403)
    Assert-Check ("Temps de réponse des dashboards < 2 s (max {0:N0} ms)" -f $script:slowest) ($script:slowest -lt 2000)
} finally {
    Stop-Ai $ai
    Remove-Item $aiLog, "$aiLog.out" -ErrorAction SilentlyContinue
    "--- Nettoyage (suppression des comptes puis des entreprises de test et de leurs offres par l'admin) ---"
    foreach ($u in $users.Values) { if ($u.Id) { Invoke-Api DELETE "/api/admin/users/$($u.Id)" -Token $admin.Token | Out-Null } }
    foreach ($id in $companies) { if ($id) { Invoke-Api DELETE "/api/admin/companies/$id" -Token $admin.Token | Out-Null } }
    Assert-Check "MySQL : plus aucun compte, offre ni score de test" ((Scalar "SELECT COUNT(*) FROM users WHERE email LIKE 'e2e12-%'") -eq 0 -and
        (Scalar "SELECT COUNT(*) FROM job_offers WHERE title LIKE '%$suffix'") -eq 0 -and
        (Scalar "SELECT COUNT(*) FROM companies WHERE name LIKE '%$suffix'") -eq 0)
}

Write-Summary
