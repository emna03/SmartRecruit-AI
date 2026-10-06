# Scénario complet de l'étape 5 sur le backend lancé (MySQL) : profils, entreprise, offres, candidatures.
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step5.ps1 [-BaseUrl http://localhost:8080]
# Les comptes (e2e5-*@test.local) et entreprises (E2E5 *) de test sont supprimés au début et à la fin.
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\e2e-common.ps1"

$password = "E2eTest123"
$r = Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }
$adminToken = $r.Json.accessToken
if (-not $adminToken) { throw "Connexion admin impossible (code $($r.Code))" }

function Remove-TestData {
    foreach ($company in @((Invoke-Api GET /api/admin/companies $null $adminToken).Json) | Where-Object { $_.name -like "E2E5 *" }) {
        Invoke-Api DELETE "/api/admin/companies/$($company.id)" $null $adminToken | Out-Null
    }
    foreach ($user in @((Invoke-Api GET /api/admin/users $null $adminToken).Json) | Where-Object { $_.email -like "e2e5-*@test.local" }) {
        Invoke-Api DELETE "/api/admin/users/$($user.id)" $null $adminToken | Out-Null
    }
}
Remove-TestData

function Register([string]$email, [string]$role) {
    (Invoke-Api POST /api/auth/register @{ email = $email; password = $password; firstName = "Test"; lastName = "E2E"; role = $role }).Json.accessToken
}

# ---------- Recruteur : profil, entreprise, offres ----------
$recruiter = Register "e2e5-recruteur@test.local" "RECRUTEUR"
$otherRecruiter = Register "e2e5-autre@test.local" "RECRUTEUR"
$candidate = Register "e2e5-candidat@test.local" "CANDIDAT"
Assert-Check "S01 inscription recruteur, 2e recruteur et candidat" ([bool]$recruiter -and [bool]$otherRecruiter -and [bool]$candidate)

$r = Invoke-Api PUT /api/recruiter/profile @{ firstName = "Bob"; lastName = "Durand"; jobTitle = "Responsable RH" } $recruiter
Assert-Check "S02 recruteur met à jour son profil -> 200" ($r.Code -eq 200 -and $r.Json.jobTitle -eq "Responsable RH")

$offer = @{ title = "Développeur Full Stack Java/Angular"; description = "Conception d'applications Spring Boot et Angular."
    location = "Tunis"; contractType = "CDI"; minExperienceYears = 2; educationLevel = "BAC_PLUS_5"
    salaryMin = 2500; salaryMax = 4000; requiredSkills = @("Java", "Spring Boot", "Angular", "MySQL") }
$r = Invoke-Api POST /api/recruiter/jobs $offer $recruiter
Assert-Check "S03 offre sans entreprise -> 400" ($r.Code -eq 400)

$r = Invoke-Api POST /api/recruiter/company @{ name = "E2E5 Acme"; sector = "Logiciel"; website = "https://acme.example"; location = "Tunis" } $recruiter
Assert-Check "S04 création de l'entreprise -> 201" ($r.Code -eq 201 -and $r.Json.recruiterCount -eq 1)

$r = Invoke-Api POST /api/recruiter/company @{ name = "e2e5 ACME" } $otherRecruiter
Assert-Check "S05 nom d'entreprise déjà pris (casse différente) -> 409" ($r.Code -eq 409)
$r = Invoke-Api POST /api/recruiter/company @{ name = "E2E5 Globex"; location = "Sfax" } $otherRecruiter
Assert-Check "S06 2e recruteur crée son entreprise -> 201" ($r.Code -eq 201)

$r = Invoke-Api POST /api/recruiter/jobs $offer $recruiter
$offerId = $r.Json.id
Assert-Check "S07 création d'offre -> 201, BROUILLON, 4 compétences, accents ok" ($r.Code -eq 201 -and $r.Json.status -eq "BROUILLON" -and @($r.Json.requiredSkills).Count -eq 4 -and $r.Json.title -eq "Développeur Full Stack Java/Angular")

$bad = $offer.Clone(); $bad.salaryMin = 5000; $bad.salaryMax = 1000
$r = Invoke-Api POST /api/recruiter/jobs $bad $recruiter
Assert-Check "S08 salaire max < min -> 400" ($r.Code -eq 400)
$r = Invoke-Api POST /api/recruiter/jobs @{ title = ""; contractType = "CDI"; minExperienceYears = 0; requiredSkills = @() } $recruiter
Assert-Check "S09 offre invalide -> 400 avec fieldErrors" ($r.Code -eq 400 -and [bool]$r.Json.fieldErrors.title -and [bool]$r.Json.fieldErrors.requiredSkills)

$r = Invoke-Api GET "/api/jobs/$offerId"
Assert-Check "S10 brouillon invisible publiquement -> 404" ($r.Code -eq 404)

$r = Invoke-Api PATCH "/api/recruiter/jobs/$offerId/status" @{ status = "PUBLIEE" } $recruiter
Assert-Check "S11 publication de l'offre -> 200 PUBLIEE" ($r.Code -eq 200 -and $r.Json.status -eq "PUBLIEE")

$r = Invoke-Api POST /api/recruiter/jobs @{ title = "Data Analyst Python"; description = "Analyse de données"; location = "Sfax"
    contractType = "STAGE"; minExperienceYears = 0; requiredSkills = @("Python", "SQL"); status = "PUBLIEE" } $otherRecruiter
$otherOfferId = $r.Json.id
Assert-Check "S12 2e recruteur publie une offre de stage -> 201" ($r.Code -eq 201 -and $r.Json.status -eq "PUBLIEE")

# ---------- Recherche publique ----------
$r = Invoke-Api GET "/api/jobs?keyword=full%20stack"
Assert-Check "S13 recherche publique par mot-clé (sans jeton) -> 200" ($r.Code -eq 200 -and @(@($r.Json.content) | Where-Object id -eq $offerId).Count -eq 1 -and $null -eq $r.Json.content[0].applicationCount)
$r = Invoke-Api GET "/api/jobs?skill=python&contractType=STAGE&location=sfax"
Assert-Check "S14 filtres compétence + contrat + lieu" ($r.Code -eq 200 -and @($r.Json.content).Count -ge 1 -and @(@($r.Json.content) | Where-Object { $_.contractType -ne "STAGE" }).Count -eq 0)
$r = Invoke-Api GET "/api/jobs?size=1&page=0"
Assert-Check "S15 pagination (size=1) -> 1 élément, $($r.Json.totalElements) au total" ($r.Code -eq 200 -and @($r.Json.content).Count -eq 1 -and $r.Json.totalElements -ge 2)
$r = Invoke-Api GET "/api/jobs/$offerId"
Assert-Check "S16 détail public de l'offre publiée -> 200" ($r.Code -eq 200 -and $r.Json.company.name -eq "E2E5 Acme")

# ---------- Candidat : profil complet ----------
$r = Invoke-Api PUT /api/candidate/profile @{ firstName = "Élodie"; lastName = "Martin"; headline = "Développeuse Java"
    phone = "+216 20 123 456"; location = "Tunis"; yearsOfExperience = 3; educationLevel = "BAC_PLUS_5" } $candidate
Assert-Check "S17 candidat met à jour son profil -> 200" ($r.Code -eq 200 -and $r.Json.firstName -eq "Élodie" -and $r.Json.yearsOfExperience -eq 3)
$r = Invoke-Api PUT /api/candidate/profile/skills @{ skills = @("java", "Spring Boot", "Angular", "Docker", "JAVA") } $candidate
Assert-Check "S18 compétences dédoublonnées et réutilisées -> 4" ($r.Code -eq 200 -and @($r.Json.skills).Count -eq 4 -and @($r.Json.skills) -contains "Java")
$r = Invoke-Api POST /api/candidate/profile/educations @{ degree = "Diplôme d'ingénieur"; fieldOfStudy = "Génie logiciel"; institution = "ESPRIT"; startDate = "2021-09-01"; endDate = "2026-06-30" } $candidate
Assert-Check "S19 ajout d'une formation -> 201" ($r.Code -eq 201 -and @($r.Json.educations).Count -eq 1 -and $r.Json.educations[0].id -gt 0)
$r = Invoke-Api POST /api/candidate/profile/experiences @{ jobTitle = "Stagiaire développeur"; companyName = "Acme"; startDate = "2025-02-01"; endDate = "2025-01-01" } $candidate
Assert-Check "S20 expérience avec dates incohérentes -> 400" ($r.Code -eq 400)
$r = Invoke-Api POST /api/candidate/profile/experiences @{ jobTitle = "Stagiaire développeur"; companyName = "Acme"; startDate = "2025-02-01"; endDate = "2025-07-31" } $candidate
$experienceId = $r.Json.experiences[0].id
Assert-Check "S21 ajout d'une expérience -> 201" ($r.Code -eq 201 -and $experienceId -gt 0)
$r = Invoke-Api PUT "/api/candidate/profile/experiences/$experienceId" @{ jobTitle = "Développeur stagiaire"; companyName = "Acme"; startDate = "2025-02-01" } $candidate
Assert-Check "S22 modification de l'expérience (en cours) -> 200" ($r.Code -eq 200 -and $r.Json.experiences[0].current -eq $true)

# ---------- Candidatures ----------
$r = Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offerId; coverLetter = "Très motivée par ce poste." } $candidate
$applicationId = $r.Json.id
Assert-Check "S23 candidature -> 201 EN_ATTENTE" ($r.Code -eq 201 -and $r.Json.status -eq "EN_ATTENTE" -and $r.Json.job.companyName -eq "E2E5 Acme")
$r = Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offerId } $candidate
Assert-Check "S24 double candidature -> 409" ($r.Code -eq 409)
$r = Invoke-Api POST /api/candidate/applications @{ jobOfferId = $offerId } $recruiter
Assert-Check "S25 recruteur ne peut pas postuler -> 403" ($r.Code -eq 403)

$r = Invoke-Api GET "/api/recruiter/jobs/$offerId/applications" $null $recruiter
$list = @($r.Json)
Assert-Check "S26 recruteur liste les candidatures de son offre -> 1" ($r.Code -eq 200 -and $list.Count -eq 1 -and $list[0].candidate.email -eq "e2e5-candidat@test.local")
$r = Invoke-Api GET "/api/recruiter/applications/$applicationId" $null $recruiter
Assert-Check "S27 détail : profil complet du candidat (formations, expériences)" ($r.Code -eq 200 -and @($r.Json.candidate.educations).Count -eq 1 -and @($r.Json.candidate.experiences).Count -eq 1)
$r = Invoke-Api GET "/api/recruiter/jobs/$offerId" $null $recruiter
Assert-Check "S28 l'offre du recruteur compte 1 candidature" ($r.Code -eq 200 -and $r.Json.applicationCount -eq 1)

$r = Invoke-Api GET "/api/recruiter/jobs/$offerId/applications" $null $otherRecruiter
Assert-Check "S29 autre recruteur : candidatures d'une offre d'autrui -> 404" ($r.Code -eq 404)
$r = Invoke-Api PATCH "/api/recruiter/applications/$applicationId/status" @{ status = "REFUSE" } $otherRecruiter
Assert-Check "S30 autre recruteur : changer le statut -> 404" ($r.Code -eq 404)
$r = Invoke-Api PUT "/api/recruiter/jobs/$offerId" $offer $otherRecruiter
Assert-Check "S31 autre recruteur : modifier l'offre d'autrui -> 404" ($r.Code -eq 404)

$r = Invoke-Api PATCH "/api/recruiter/applications/$applicationId/status" @{ status = "PRESELECTIONNE" } $recruiter
Assert-Check "S32 recruteur présélectionne le candidat -> 200" ($r.Code -eq 200 -and $r.Json.status -eq "PRESELECTIONNE")
$r = Invoke-Api GET /api/candidate/applications $null $candidate
Assert-Check "S33 le candidat voit le nouveau statut" ($r.Code -eq 200 -and @($r.Json)[0].status -eq "PRESELECTIONNE")

$r = Invoke-Api PATCH "/api/recruiter/jobs/$offerId/status" @{ status = "FERMEE" } $recruiter
Assert-Check "S34 fermeture de l'offre -> 200" ($r.Code -eq 200 -and $r.Json.status -eq "FERMEE")
$r = Invoke-Api GET "/api/jobs/$offerId"
Assert-Check "S35 offre fermée : absente du public (404)" ($r.Code -eq 404)
$r = Invoke-Api POST /api/candidate/applications @{ jobOfferId = $otherOfferId } $candidate
Assert-Check "S36 candidature à l'offre de stage -> 201" ($r.Code -eq 201)

# ---------- Administration ----------
$r = Invoke-Api GET /api/admin/companies $null $adminToken
$acme = @($r.Json) | Where-Object name -eq "E2E5 Acme"
Assert-Check "S37 admin liste les entreprises (Acme : 1 offre, 1 recruteur)" ($r.Code -eq 200 -and $acme.jobOfferCount -eq 1 -and $acme.recruiterCount -eq 1)
$r = Invoke-Api GET "/api/admin/jobs?status=PUBLIEE" $null $adminToken
Assert-Check "S38 admin filtre les offres publiées" ($r.Code -eq 200 -and @(@($r.Json) | Where-Object { $_.status -ne "PUBLIEE" }).Count -eq 0)
$r = Invoke-Api DELETE "/api/admin/jobs/$otherOfferId" $null $adminToken
$r2 = Invoke-Api GET /api/candidate/applications $null $candidate
Assert-Check "S39 admin supprime une offre -> 204, sa candidature disparaît" ($r.Code -eq 204 -and @($r2.Json).Count -eq 1)
$r = Invoke-Api DELETE "/api/admin/companies/$($acme.id)" $null $adminToken
$r2 = Invoke-Api GET /api/candidate/applications $null $candidate
$r3 = Invoke-Api GET /api/recruiter/profile $null $recruiter
Assert-Check "S40 admin supprime l'entreprise -> offres et candidatures supprimées, recruteur détaché" ($r.Code -eq 204 -and @($r2.Json).Count -eq 0 -and $null -eq $r3.Json.company)

Remove-TestData
$left = @((Invoke-Api GET /api/admin/users $null $adminToken).Json) | Where-Object { $_.email -like "e2e5-*@test.local" }
Assert-Check "S41 nettoyage des données de test" (@($left).Count -eq 0)

Write-Summary
