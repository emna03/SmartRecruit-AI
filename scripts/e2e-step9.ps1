# Vérification réelle de l'ÉTAPE 9 : CV analysé + offre -> Spring Boot -> FastAPI (matching) -> MySQL -> API candidat
# et classement recruteur, avec de vrais CV PDF fictifs analysés par le CV Parser.
#
# Prérequis : MySQL (conteneur smartrecruit-mysql) et le backend démarrés, ai-service/.venv installé,
# service IA ARRÊTÉ (le script le démarre et l'arrête lui-même pour tester la panne).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step9.ps1 [-BaseUrl http://localhost:8080]
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
$keyHeader = @{ "X-Internal-Api-Key" = $aiKey }

function Fixture([string]$name) { return [IO.File]::ReadAllBytes((Join-Path $fixtures $name)) }
function Names($items) { return @($items | ForEach-Object { $_.name }) }
function SameList($actual, [string[]]$expected) { return (@($actual) -join "|") -eq ($expected -join "|") }
$script:aiLogs = ""
function Stop-AiKeepLog($process) {
    Stop-Ai $process
    if (Test-Path $aiLog) { $script:aiLogs += Get-Content $aiLog -Raw -Encoding UTF8 }
}

$adminToken = (Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }).Json.accessToken
$suffix = -join ((97..122) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
$users = @{}
function Register([string]$key, [string]$firstName, [string]$role) {
    $email = "e2e9-$key-$suffix@test.local"
    $r = Invoke-Api POST /api/auth/register @{ email = $email; password = "Passw0rd!"; firstName = $firstName; lastName = "E2E"; role = $role }
    $script:users[$key] = [pscustomobject]@{ Email = $email; Token = $r.Json.accessToken; Id = $r.Json.user.id }
    return $script:users[$key]
}
function NewOffer([string]$title, [string[]]$skills, [int]$years, $level) {
    $body = @{ title = $title; description = "Offre de test E2E étape 9"; location = "Tunis"; contractType = "CDI";
        minExperienceYears = $years; educationLevel = $level; requiredSkills = $skills; status = "PUBLIEE" }
    return (Invoke-Api POST /api/recruiter/jobs $body -Token $rec.Token).Json.id
}
function Matching($user, $offer) { return Invoke-Api GET "/api/candidate/jobs/$offer/matching" -Token $user.Token }
function MatchRow([string]$email, $offer, [string]$columns) {
    return Invoke-Sql ("SELECT $columns FROM matching_results m JOIN candidate_profiles p ON p.id = m.candidate_id " +
        "JOIN users u ON u.id = p.user_id WHERE u.email = '$email' AND m.job_offer_id = $offer")
}

$rec = Register "rec" "Rania" "RECRUTEUR"
Invoke-Api POST /api/recruiter/company @{ name = "E2E9 Corp $suffix"; sector = "IT"; location = "Tunis" } -Token $rec.Token | Out-Null
$fr = Register "fr" "Camille" "CANDIDAT"
$en = Register "en" "Alex" "CANDIDAT"
$min = Register "min" "Sami" "CANDIDAT"
$nocv = Register "nocv" "Nora" "CANDIDAT"
$unparsed = Register "unparsed" "Fares" "CANDIDAT"
$javaSkills = @("Java", "Spring Boot", "Angular", "MySQL", "Docker", "Kubernetes")
$offer = NewOffer "Développeur Full Stack Java" $javaSkills 3 "BAC_PLUS_5"
$devops = NewOffer "Ingénieur DevOps" @("Kubernetes", "Terraform", "Ansible") 0 $null
$senior = NewOffer "Architecte Java" @("Java", "Spring Boot") 10 "DOCTORAT"
$ai = $null

try {
    "--- Service IA FastAPI : POST /api/matching/score (appel direct) ---"
    $ai = Start-Ai
    $direct = @{ candidate = @{ skills = @("Java", "PostgreSQL"); experienceMonths = 24; educationLevel = "BAC_PLUS_5" };
        offer = @{ requiredSkills = @("Java", "MySQL", "Docker"); minExperienceYears = 2; educationLevel = "BAC_PLUS_3" } }
    Assert-Check "Sans clé interne -> 401" ((Invoke-Api POST "$aiUrl/api/matching/score" $direct).Code -eq 401)
    $r = Invoke-Api POST "$aiUrl/api/matching/score" $direct -Headers $keyHeader
    Assert-Check "Avec la clé -> 200, score 70 (Java exact, MySQL proche de PostgreSQL)" ($r.Code -eq 200 -and $r.Json.score -eq 70 -and
        $r.Json.skillsScore -eq 50 -and $r.Json.matchedSkills[1].matchType -eq "RELATED" -and (SameList $r.Json.missingSkills @("Docker")))
    $r2 = Invoke-Api POST "$aiUrl/api/matching/score" $direct -Headers $keyHeader
    Assert-Check "Même entrée -> même résultat (aucun aléatoire)" (($r.Json | ConvertTo-Json -Depth 5) -eq ($r2.Json | ConvertTo-Json -Depth 5))
    $withEmail = @{ candidate = @{ skills = @("Java"); email = "x@y.z" }; offer = @{ requiredSkills = @("Java") } }
    Assert-Check "Champ personnel non prévu (email) -> 400" ((Invoke-Api POST "$aiUrl/api/matching/score" $withEmail -Headers $keyHeader).Code -eq 400)
    $noSkill = @{ candidate = @{ skills = @() }; offer = @{ requiredSkills = @() } }
    Assert-Check "Offre sans compétence requise -> 400" ((Invoke-Api POST "$aiUrl/api/matching/score" $noSkill -Headers $keyHeader).Code -eq 400)

    "--- Sécurité et cas sans score ---"
    Assert-Check "Anonyme -> 401" ((Invoke-Api GET "/api/candidate/jobs/$offer/matching").Code -eq 401)
    Assert-Check "Recruteur sur l'API candidat -> 403" ((Matching $rec $offer).Code -eq 403)
    Assert-Check "Candidat sur le recalcul recruteur -> 403" ((Invoke-Api POST "/api/recruiter/jobs/$offer/matching" -Token $nocv.Token).Code -eq 403)
    Assert-Check "Offre inexistante -> 404" ((Matching $nocv 999999999).Code -eq 404)
    $r = Matching $nocv $offer
    Assert-Check "Candidat sans CV -> CV_ABSENT, message clair, aucun score" ($r.Code -eq 200 -and $r.Json.status -eq "CV_ABSENT" -and
        $r.Json.message -like "*Importez votre CV*" -and $null -eq $r.Json.result)

    "--- Vrais CV analysés (CV Parser) ---"
    foreach ($c in @(@($fr, "cv_dev_fullstack_fr.pdf"), @($en, "cv_data_scientist_en.pdf"), @($min, "cv_minimal_fr.pdf"))) {
        $r = Invoke-Upload /api/candidate/cv (Fixture $c[1]) $c[1] "application/pdf" $c[0].Token
        Assert-Check "Upload $($c[1]) -> TERMINE" ($r.Code -eq 200 -and $r.Json.parsingStatus -eq "TERMINE")
    }

    "--- Candidat très compatible (CV full stack FR) ---"
    $r = Matching $fr $offer
    $m = $r.Json.result
    Assert-Check "CALCULE : score 90 (compétences 83,3 / expérience 100 / formation 100)" ($r.Json.status -eq "CALCULE" -and
        $m.score -eq 90 -and $m.skillsScore -eq 83.3 -and $m.experienceScore -eq 100 -and $m.educationScore -eq 100)
    # Les compétences de l'offre sont envoyées par ordre alphabétique : les listes du résultat suivent cet ordre.
    Assert-Check "Trouvées : Angular, Docker, Java, MySQL, Spring Boot ; manquante : Kubernetes" (
        (SameList (Names $m.matchedSkills) @("Angular", "Docker", "Java", "MySQL", "Spring Boot")) -and (SameList $m.missingSkills @("Kubernetes")))
    Assert-Check "Explication lisible + rappel « pas une décision », version 1.0.0" ($m.explanation -like "Score global : 90 %*compatibilité élevée*" -and
        $m.explanation -like "*ne constitue pas une décision de recrutement*" -and $m.algorithmVersion -eq "1.0.0" -and -not $m.stale)

    "--- Candidat partiellement compatible (CV data scientist EN) ---"
    $m = (Matching $en $offer).Json.result
    Assert-Check "Score 55 (compétences 25 : Docker exact + MySQL proche, crédit 50 %)" ($m.score -eq 55 -and $m.skillsScore -eq 25 -and
        (SameList (Names $m.matchedSkills) @("Docker", "MySQL")) -and $m.matchedSkills[0].matchType -eq "EXACT" -and
        $m.matchedSkills[1].matchType -eq "RELATED" -and $m.matchedSkills[1].matchedBy -eq "PostgreSQL" -and $m.matchedSkills[1].credit -eq 0.5)
    Assert-Check "Manquantes : Angular, Java, Kubernetes, Spring Boot" (SameList $m.missingSkills @("Angular", "Java", "Kubernetes", "Spring Boot"))

    "--- Candidat peu compatible, expérience et niveau insuffisants (CV minimal FR) ---"
    $m = (Matching $min $offer).Json.result
    Assert-Check "Score 19 (compétences 16,7 / expérience 0 / formation 60 : Bac+3 pour Bac+5)" ($m.score -eq 19 -and $m.skillsScore -eq 16.7 -and
        $m.experienceScore -eq 0 -and $m.educationScore -eq 60)
    Assert-Check "Explication : niveau insuffisant et expérience non datée" ($m.explanation -like "*compatibilité faible*" -and
        $m.explanation -like "*Formation (poids 15 %) : 60 %*Bac+3 détecté pour Bac+5 demandé (1 niveau(x) en dessous)*" -and
        $m.explanation -like "*Expérience (poids 25 %) : 0 %*aucune expérience datée détectée*")

    "--- Expérience insuffisante malgré toutes les compétences (offre senior, 10 ans, doctorat) ---"
    $m = (Matching $fr $senior).Json.result
    Assert-Check "Compétences 100, expérience 41,7 (50 mois / 120), formation 60 -> 79,4" ($m.skillsScore -eq 100 -and
        $m.experienceScore -eq 41.7 -and $m.educationScore -eq 60 -and $m.score -eq 79.4)

    "--- Aucune compétence commune (offre DevOps, sans exigence d'expérience ni de niveau) ---"
    $m = (Matching $min $devops).Json.result
    Assert-Check "Score 0, critères non exigés absents (null), 3 manquantes" ($m.score -eq 0 -and $m.skillsScore -eq 0 -and
        $null -eq $m.experienceScore -and $null -eq $m.educationScore -and $m.matchedSkills.Count -eq 0 -and $m.missingSkills.Count -eq 3)

    "--- Candidatures puis classement recruteur ---"
    foreach ($u in @($min, $nocv, $en, $fr)) {
        $r = Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offer } -Token $u.Token
        if ($r.Code -ne 201) { throw "Candidature refusée ($($u.Email)) : $($r.Code)" }
    }
    $r = Invoke-Api GET "/api/recruiter/jobs/$offer/applications" -Token $rec.Token
    $list = @($r.Json)
    Assert-Check "Classement : FR (90) > EN (55) > minimal (19) > sans CV" ($list.Count -eq 4 -and
        (SameList ($list | ForEach-Object { $_.candidate.email }) @($fr.Email, $en.Email, $min.Email, $nocv.Email)))
    Assert-Check "Scores, sous-scores et compétences manquantes visibles par le recruteur" ($list[0].matching.score -eq 90 -and
        $list[1].matching.skillsScore -eq 25 -and (SameList $list[0].matching.missingSkills @("Kubernetes")) -and $list[2].matching.educationScore -eq 60)
    Assert-Check "Candidat sans CV : pas de score, cvAnalyzed=false" ($null -eq $list[3].matching -and -not $list[3].candidate.cvAnalyzed)
    $detail = Invoke-Api GET "/api/recruiter/applications/$($list[0].id)" -Token $rec.Token
    Assert-Check "Détail de candidature : score et explication" ($detail.Json.matching.score -eq 90 -and $detail.Json.matching.explanation -like "Score global*")
    $other = Register "other" "Omar" "RECRUTEUR"
    Invoke-Api POST /api/recruiter/company @{ name = "E2E9 Autre $suffix"; sector = "IT"; location = "Sfax" } -Token $other.Token | Out-Null
    Assert-Check "Autre recruteur : recalcul -> 404" ((Invoke-Api POST "/api/recruiter/jobs/$offer/matching" -Token $other.Token).Code -eq 404)

    "--- Recalcul par le recruteur ---"
    $r = Invoke-Api POST "/api/recruiter/jobs/$offer/matching" -Token $rec.Token
    Assert-Check "POST /matching -> 3 scores recalculés, 1 candidat sans CV analysé" ($r.Code -eq 200 -and $r.Json.computed -eq 3 -and
        $r.Json.withoutAnalyzedCv -eq 1 -and $r.Json.applications[0].matching.score -eq 90)

    "--- MySQL : table matching_results ---"
    $row = (MatchRow $fr.Email $offer "m.score, m.skills_score, m.experience_score, m.education_score, m.algorithm_version, JSON_VALID(m.matched_skills), JSON_LENGTH(m.matched_skills), m.missing_skills, LEFT(m.explanation, 30), m.computed_at IS NOT NULL")[0]
    Assert-Check "Ligne FR : 90.00 / 83.30 / 100.00 / 100.00, version 1.0.0" ($row[0] -eq "90.00" -and $row[1] -eq "83.30" -and
        $row[2] -eq "100.00" -and $row[3] -eq "100.00" -and $row[4] -eq "1.0.0")
    Assert-Check "matched_skills JSON (5 objets), missing_skills [`"Kubernetes`"], explication, date" ($row[5] -eq "1" -and $row[6] -eq "5" -and
        $row[7] -eq '["Kubernetes"]' -and $row[8] -like "Score global : 90 %*" -and $row[9] -eq "1")
    $related = (MatchRow $en.Email $offer "JSON_UNQUOTE(JSON_EXTRACT(m.matched_skills, '$[1].matchType')), JSON_UNQUOTE(JSON_EXTRACT(m.matched_skills, '$[1].matchedBy')), JSON_EXTRACT(m.matched_skills, '$[1].credit')")[0]
    Assert-Check "Ligne EN : MySQL couvert par PostgreSQL (RELATED, crédit 0.5)" ($related[0] -eq "RELATED" -and $related[1] -eq "PostgreSQL" -and $related[2] -eq "0.5")
    $row = (MatchRow $min.Email $devops "m.score, COALESCE(m.experience_score, 'NULL'), COALESCE(m.education_score, 'NULL'), m.matched_skills")[0]
    Assert-Check "Ligne DevOps : 0.00, expérience et formation NULL (non exigées)" ($row[0] -eq "0.00" -and $row[1] -eq "NULL" -and $row[2] -eq "NULL" -and $row[3] -eq "[]")
    $ranking = Invoke-Sql "SELECT u.email, m.score FROM matching_results m JOIN candidate_profiles p ON p.id = m.candidate_id JOIN users u ON u.id = p.user_id WHERE m.job_offer_id = $offer ORDER BY m.score DESC"
    Assert-Check "Offre Java : 3 lignes (une par candidat), ordre 90 / 55 / 19" ($ranking.Count -eq 3 -and $ranking[0][0] -eq $fr.Email -and
        $ranking[1][1] -eq "55.00" -and $ranking[2][1] -eq "19.00")

    "--- CV non analysé (service IA arrêté pendant l'upload) ---"
    Stop-AiKeepLog $ai
    $ai = $null
    $r = Invoke-Upload /api/candidate/cv (Fixture "cv_dev_fullstack_fr.pdf") "cv.pdf" "application/pdf" $unparsed.Token
    Assert-Check "Upload pendant la panne -> CV en ECHEC" ($r.Code -eq 200 -and $r.Json.parsingStatus -eq "ECHEC")

    "--- Service IA indisponible ---"
    $start = Get-Date
    $r = Matching $fr $offer
    $elapsed = ((Get-Date) - $start).TotalSeconds
    Assert-Check ("Candidat : IA_INDISPONIBLE + dernier score enregistré (90) ({0:N1} s)" -f $elapsed) ($r.Code -eq 200 -and
        $r.Json.status -eq "IA_INDISPONIBLE" -and $r.Json.message -like "*indisponible*" -and $r.Json.result.score -eq 90 -and $elapsed -lt 5)
    $r = Matching $en $devops
    Assert-Check "Sans score enregistré : IA_INDISPONIBLE sans résultat" ($r.Json.status -eq "IA_INDISPONIBLE" -and $null -eq $r.Json.result)
    $r = Matching $unparsed $offer
    Assert-Check "CV en échec -> CV_NON_ANALYSE (aucun appel IA nécessaire)" ($r.Json.status -eq "CV_NON_ANALYSE" -and $r.Json.message -like "*analys*")
    $r = Invoke-Api POST "/api/recruiter/jobs/$offer/matching" -Token $rec.Token
    Assert-Check "Recruteur : recalcul -> 503 message clair" ($r.Code -eq 503 -and $r.Json.message -like "*indisponible*")
    $r = Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offer } -Token $unparsed.Token
    Assert-Check "La candidature reste possible pendant la panne -> 201" ($r.Code -eq 201)
    $list = @((Invoke-Api GET "/api/recruiter/jobs/$offer/applications" -Token $rec.Token).Json)
    $pending = $list | Where-Object { $_.candidate.email -eq $unparsed.Email }
    Assert-Check "Classement toujours consultable (scores lus dans MySQL)" ($list.Count -eq 5 -and $list[0].matching.score -eq 90 -and
        $null -eq $list[3].matching -and $null -eq $list[4].matching -and $null -eq $pending.matching -and
        $pending.candidate.hasCv -and -not $pending.candidate.cvAnalyzed)

    "--- Invalidation ---"
    $ai = Start-Ai
    Assert-Check "Suppression du CV EN -> 204" ((Invoke-Api DELETE /api/candidate/cv -Token $en.Token).Code -eq 204)
    Assert-Check "MySQL : scores du candidat EN supprimés" ((MatchRow $en.Email $offer "m.id").Count -eq 0)
    $edit = @{ title = "Développeur Full Stack Java"; description = "Offre de test E2E étape 9"; location = "Tunis"; contractType = "CDI";
        minExperienceYears = 5; educationLevel = "BAC_PLUS_5"; requiredSkills = $javaSkills }
    Assert-Check "Offre modifiée (5 ans au lieu de 3) -> 200" ((Invoke-Api PUT "/api/recruiter/jobs/$offer" $edit -Token $rec.Token).Code -eq 200)
    Assert-Check "MySQL : scores de l'offre supprimés (critères changés)" ((Invoke-Sql "SELECT COUNT(*) FROM matching_results WHERE job_offer_id = $offer")[0][0] -eq "0")
    $m = (Matching $fr $offer).Json.result
    Assert-Check "Nouveau calcul : expérience 83,3 (50 mois / 60) -> score 85,8" ($m.experienceScore -eq 83.3 -and $m.score -eq 85.8)

    "--- Journaux : aucune donnée personnelle ---"
    Stop-AiKeepLog $ai
    $ai = $null
    $log = $script:aiLogs
    Assert-Check "Journal FastAPI : « Matching calculé : score 90.0, 5/6 compétences, version 1.0.0 »" ($log -match "Matching calculé : score 90\.0, 5/6 compétences, version 1\.0\.0")
    $leaks = @("Camille", "Alex", "Sami", $fr.Email, "Kubernetes", "PostgreSQL", "Spring Boot") | Where-Object { $log.Contains($_) }
    Assert-Check "Journal FastAPI : ni nom, ni email, ni compétence" ($leaks.Count -eq 0)
    $backendLog = Join-Path $env:TEMP "sr-backend.log"
    if (Test-Path $backendLog) {
        $b = Get-Content $backendLog -Raw -Encoding UTF8
        $leaks = @($fr.Email, $en.Email, "Camille Exemple", "Kubernetes") | Where-Object { $b.Contains($_) }
        Assert-Check "Journal Spring Boot : ni email, ni nom, ni compétence" ($leaks.Count -eq 0 -and $b -match "Matching enregistré \(candidat \d+, offre $offer\)")
    }
} finally {
    Stop-Ai $ai
    Remove-Item $aiLog, "$aiLog.out" -ErrorAction SilentlyContinue
    "--- Nettoyage (suppression des comptes par l'admin) ---"
    foreach ($u in $users.Values) { if ($u.Id) { Invoke-Api DELETE "/api/admin/users/$($u.Id)" -Token $adminToken | Out-Null } }
    $left = (Invoke-Sql "SELECT COUNT(*) FROM matching_results WHERE job_offer_id IN ($offer, $devops, $senior)")[0][0]
    Assert-Check "Comptes supprimés : plus aucun score lié (cascade MySQL)" ($left -eq "0")
}

Write-Summary
