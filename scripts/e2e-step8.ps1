# Vérification réelle de l'ÉTAPE 8 : CV PDF -> Spring Boot -> FastAPI (CV Parser) -> MySQL, au niveau API.
#
# Prérequis : MySQL (conteneur smartrecruit-mysql) et le backend démarrés, ai-service/.venv installé,
# service IA ARRÊTÉ (le script le démarre et l'arrête lui-même pour tester la panne).
# Usage : powershell -ExecutionPolicy Bypass -File scripts/e2e-step8.ps1 [-BaseUrl http://localhost:8080] [-UploadDir backend/uploads]
param([string]$BaseUrl = "http://localhost:8080", [string]$UploadDir = "")

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\e2e-common.ps1"

$root = Split-Path -Parent $PSScriptRoot
$aiDir = Join-Path $root "ai-service"
$python = Join-Path $aiDir ".venv\Scripts\python.exe"
$aiLog = Join-Path $aiDir "ai-e2e.log"
$fixtures = Join-Path $aiDir "tests\fixtures\cv"
if (-not $UploadDir) { $UploadDir = Join-Path $root "backend\uploads" }
$cvDir = Join-Path $UploadDir "cvs"
if (-not (Test-Path $python)) { throw "Environnement virtuel absent : voir ai-service/README.md" }

$aiUrl = Get-EnvValue "AI_SERVICE_URL"
$aiKey = Get-EnvValue "AI_INTERNAL_API_KEY"
$keyHeader = "X-Internal-Api-Key"
$cvUrl = "/api/candidate/cv"

function Fixture([string]$name) { return [IO.File]::ReadAllBytes((Join-Path $fixtures $name)) }
function Upload([string]$name, [string]$token, [string]$fileName = $name, [string]$mime = "application/pdf") {
    return Invoke-Upload $cvUrl (Fixture $name) $fileName $mime $token
}
function CvRow([string]$email, [string]$columns) {
    return Invoke-Sql ("SELECT $columns FROM cvs c JOIN candidate_profiles p ON p.id = c.candidate_id " +
        "JOIN users u ON u.id = p.user_id WHERE u.email = '$email'")
}
function Names($items) { return @($items | ForEach-Object { $_.name }) }
# Start-Ai réécrit le journal à chaque démarrage : on conserve celui de chaque session.
$script:aiLogs = ""
function Stop-AiKeepLog($process) {
    Stop-Ai $process
    if (Test-Path $aiLog) { $script:aiLogs += Get-Content $aiLog -Raw -Encoding UTF8 }
}

$adminToken = (Invoke-Api POST /api/auth/login @{ email = (Get-EnvValue "ADMIN_EMAIL"); password = (Get-EnvValue "ADMIN_PASSWORD") }).Json.accessToken
$suffix = -join ((97..122) | Get-Random -Count 8 | ForEach-Object { [char]$_ })
$email = "e2e8-cand-$suffix@test.local"
$candidate = Invoke-Api POST /api/auth/register @{ email = $email; password = "Passw0rd!"; firstName = "Camille"; lastName = "Exemple"; role = "CANDIDAT" }
$token = $candidate.Json.accessToken
$recruiter = Invoke-Api POST /api/auth/register @{ email = "e2e8-rec-$suffix@test.local"; password = "Passw0rd!"; firstName = "E2E8"; lastName = "Recruteur"; role = "RECRUTEUR" }
$ai = $null

try {
    "--- Service IA FastAPI : POST /api/cv/parse (appel direct) ---"
    $ai = Start-Ai
    $r = Invoke-Binary "$aiUrl/api/cv/parse" (Fixture "cv_dev_fullstack_fr.pdf") "application/pdf"
    Assert-Check "Sans clé interne -> 401" ($r.Code -eq 401)
    $r = Invoke-Binary "$aiUrl/api/cv/parse" (Fixture "cv_dev_fullstack_fr.pdf") "application/pdf" @{ $keyHeader = $aiKey }
    Assert-Check "Avec la clé -> 200, Java / Spring Boot / Angular / MySQL détectés" ($r.Code -eq 200 -and
        -not (@("Java", "Spring Boot", "Angular", "MySQL") | Where-Object { (Names $r.Json.technologies) -notcontains $_ }))
    $r = Invoke-Binary "$aiUrl/api/cv/parse" (Fixture "cv_dev_fullstack_fr.pdf") "text/plain" @{ $keyHeader = $aiKey }
    Assert-Check "Type de contenu autre que application/pdf -> 415" ($r.Code -eq 415)

    "--- Sécurité de /api/candidate/cv ---"
    Assert-Check "Upload anonyme -> 401" ((Upload "cv_dev_fullstack_fr.pdf" $null).Code -eq 401)
    Assert-Check "Upload par un recruteur -> 403" ((Upload "cv_dev_fullstack_fr.pdf" $recruiter.Json.accessToken).Code -eq 403)
    Assert-Check "Candidat sans CV : GET -> 404" ((Invoke-Api GET $cvUrl -Token $token).Code -eq 404)

    "--- Upload réel : Spring Boot -> FastAPI -> MySQL ---"
    $start = Get-Date
    $r = Upload "cv_dev_fullstack_fr.pdf" $token
    $elapsed = ((Get-Date) - $start).TotalMilliseconds
    $a = $r.Json.analysis
    Assert-Check ("Upload du CV fictif FR -> 200 TERMINE ({0:N0} ms)" -f $elapsed) ($r.Code -eq 200 -and $r.Json.parsingStatus -eq "TERMINE")
    Assert-Check "Technologies : Java, Spring Boot, Angular, MySQL, TypeScript, Docker (24 au total)" ($a.technologies.Count -eq 24 -and
        -not (@("Java", "Spring Boot", "Angular", "MySQL", "TypeScript", "Docker") | Where-Object { (Names $a.technologies) -notcontains $_ }))
    Assert-Check "Compétences : API REST, Scrum, Tests unitaires (9 au total)" ($a.skills.Count -eq 9 -and
        -not (@("API REST", "Scrum", "Tests unitaires") | Where-Object { (Names $a.skills) -notcontains $_ }))
    Assert-Check "Formations : ingénieur (BAC_PLUS_5) puis licence (BAC_PLUS_3)" ($a.educations.Count -eq 2 -and
        $a.educations[0].level -eq "BAC_PLUS_5" -and $a.educations[1].level -eq "BAC_PLUS_3" -and $a.educations[0].fieldOfStudy -eq "Génie Logiciel")
    Assert-Check "Expériences : poste actuel depuis 2023-03 + stage 2022-02 / 2022-07" ($a.experiences.Count -eq 2 -and
        $a.experiences[0].current -and $a.experiences[0].startDate -eq "2023-03" -and $a.experiences[1].endDate -eq "2022-07")
    Assert-Check "Langues : Français natif, Anglais courant B2 (TOEIC 850), Allemand notions" ($a.languages.Count -eq 3 -and
        $a.languages[0].level -eq "NATIF" -and $a.languages[1].cefr -eq "B2" -and $a.languages[1].certification -eq "TOEIC 850")
    Assert-Check "Le texte du CV n'est pas renvoyé au navigateur" ($null -eq $a.text)

    $row = (CvRow $email "c.parsing_status, c.content_type, c.file_size, c.stored_file_name, JSON_LENGTH(c.extracted_data, '$.technologies'), JSON_CONTAINS_PATH(c.extracted_data, 'one', '$.text'), INSTR(c.extracted_text, 'example.com'), INSTR(c.extracted_text, '+216'), INSTR(c.extracted_text, '[email]') > 0")[0]
    Assert-Check "MySQL cvs : TERMINE, application/pdf, taille exacte, nom aléatoire" ($row[0] -eq "TERMINE" -and $row[1] -eq "application/pdf" -and
        [int]$row[2] -eq (Fixture "cv_dev_fullstack_fr.pdf").Length -and $row[3] -match '^[0-9a-f]{32}\.pdf$')
    Assert-Check "MySQL extracted_data : JSON validé (24 technologies), sans le texte" ($row[4] -eq "24" -and $row[5] -eq "0")
    Assert-Check "MySQL extracted_text : email et téléphone masqués" ($row[6] -eq "0" -and $row[7] -eq "0" -and $row[8] -eq "1")
    $skills = Invoke-Sql ("SELECT s.name, s.category FROM cvs c JOIN cv_skills cs ON cs.cv_id = c.id JOIN skills s ON s.id = cs.skill_id " +
        "JOIN candidate_profiles p ON p.id = c.candidate_id JOIN users u ON u.id = p.user_id WHERE u.email = '$email'")
    $categories = @{}; $skills | ForEach-Object { $categories[$_[0]] = $_[1] }
    Assert-Check "MySQL cv_skills : 33 compétences liées, avec catégorie" ($skills.Count -eq 33 -and $categories["Java"] -eq "Langage" -and
        $categories["Spring Boot"] -eq "Framework back-end" -and $categories["MySQL"] -eq "Base de données")
    $storedFile = Join-Path $cvDir $row[3]
    Assert-Check "Fichier stocké sur disque, identique à l'original" ((Test-Path $storedFile) -and
        (Get-FileHash $storedFile).Hash -eq (Get-FileHash (Join-Path $fixtures "cv_dev_fullstack_fr.pdf")).Hash)
    $firstFile = $row[3]
    $filesBefore = @(Get-ChildItem $cvDir -Filter *.pdf).Count

    "--- Fichiers refusés (le CV précédent est conservé) ---"
    $r = Invoke-Upload $cvUrl ([byte[]]@()) "vide.pdf" "application/pdf" $token
    Assert-Check "Fichier vide -> 400 « Le fichier est vide. »" ($r.Code -eq 400 -and $r.Json.message -eq "Le fichier est vide.")
    $r = Upload "not_a_pdf.pdf" $token
    Assert-Check "Fichier texte renommé .pdf (type application/pdf) -> 400" ($r.Code -eq 400 -and $r.Json.message -like "*pas un PDF valide*")
    $r = Upload "cv_corrupted.pdf" $token
    Assert-Check "PDF endommagé -> 422 (refusé par le parser)" ($r.Code -eq 422 -and $r.Json.message -like "*endommagé*")
    $r = Upload "cv_blank.pdf" $token
    Assert-Check "PDF vide (page blanche) -> 422 sans texte exploitable" ($r.Code -eq 422 -and $r.Json.message -like "*texte exploitable*")
    $r = Upload "cv_image_only.pdf" $token
    Assert-Check "PDF scanné (images seulement) -> 422 sans texte exploitable" ($r.Code -eq 422 -and $r.Json.message -like "*texte exploitable*")
    $r = Upload "cv_encrypted.pdf" $token
    Assert-Check "PDF protégé par mot de passe -> 422" ($r.Code -eq 422 -and $r.Json.message -like "*mot de passe*")
    # curl.exe : le HttpClient de .NET Framework abandonne quand le serveur répond avant la fin de l'envoi.
    $big = [byte[]]::new(6MB); [Text.Encoding]::ASCII.GetBytes("%PDF-1.4`n").CopyTo($big, 0)
    $bigFile = Join-Path $env:TEMP "e2e8-gros-$suffix.pdf"
    [IO.File]::WriteAllBytes($bigFile, $big)
    try {
        $previous = [Console]::OutputEncoding
        [Console]::OutputEncoding = [Text.Encoding]::UTF8
        $out = @(curl.exe -s -o - -w "`n%{http_code}" -H "Authorization: Bearer $token" -F "file=@$bigFile;type=application/pdf" "$BaseUrl$cvUrl")
    } finally {
        [Console]::OutputEncoding = $previous
        Remove-Item $bigFile -ErrorAction SilentlyContinue
    }
    $bigJson = ($out[0..($out.Count - 2)] -join "`n") | ConvertFrom-Json
    Assert-Check "Fichier de 6 Mo -> 413 « taille maximale de 5 Mo »" ($out[-1] -eq "413" -and $bigJson.message -like "*5 Mo*")
    $row = (CvRow $email "c.stored_file_name, c.parsing_status, c.original_file_name")[0]
    Assert-Check "Après les refus : CV initial intact (même fichier, TERMINE)" ($row[0] -eq $firstFile -and $row[1] -eq "TERMINE" -and
        $row[2] -eq "cv_dev_fullstack_fr.pdf" -and (Test-Path $storedFile))
    Assert-Check "Aucun fichier refusé conservé sur disque" (@(Get-ChildItem $cvDir -Filter *.pdf).Count -eq $filesBefore)

    "--- Nom et type MIME du navigateur ignorés ---"
    $r = Upload "cv_data_scientist_en.pdf" $token "..\..\evil<script>.txt" "text/plain"
    $row = (CvRow $email "c.content_type, c.original_file_name, c.stored_file_name")[0]
    Assert-Check "Vrai PDF envoyé comme text/plain -> accepté, stocké en application/pdf" ($r.Code -eq 200 -and $row[0] -eq "application/pdf")
    Assert-Check "Nom affiché nettoyé (evil_script_.txt.pdf), ancien fichier supprimé" ($row[1] -eq "evil_script_.txt.pdf" -and
        -not (Test-Path (Join-Path $cvDir $firstFile)) -and (Test-Path (Join-Path $cvDir $row[2])))

    "--- Service IA arrêté puis relance de l'analyse ---"
    Stop-AiKeepLog $ai
    $ai = $null
    $start = Get-Date
    $r = Upload "cv_dev_fullstack_fr.pdf" $token
    $elapsed = ((Get-Date) - $start).TotalSeconds
    Assert-Check ("IA arrêtée : CV enregistré en ECHEC avec message clair ({0:N1} s)" -f $elapsed) ($r.Code -eq 200 -and
        $r.Json.parsingStatus -eq "ECHEC" -and $r.Json.parsingError -like "*indisponible*" -and $null -eq $r.Json.analysis -and $elapsed -lt 5)
    $row = (CvRow $email "c.parsing_status, c.parsing_error IS NOT NULL, c.extracted_data IS NULL, (SELECT COUNT(*) FROM cv_skills cs WHERE cs.cv_id = c.id)")[0]
    Assert-Check "MySQL : statut ECHEC, aucune donnée extraite conservée" ($row[0] -eq "ECHEC" -and $row[1] -eq "1" -and $row[2] -eq "1" -and $row[3] -eq "0")
    Assert-Check "Import dans le profil impossible sans analyse -> 400" ((Invoke-Api POST "$cvUrl/import" @{ skills = $true } -Token $token).Code -eq 400)
    $ai = Start-Ai
    $r = Invoke-Api POST "$cvUrl/analyze" -Token $token
    Assert-Check "IA relancée : POST /analyze -> TERMINE" ($r.Code -eq 200 -and $r.Json.parsingStatus -eq "TERMINE" -and $r.Json.analysis.technologies.Count -eq 24)

    "--- Ajout au profil (formations, expériences, compétences) ---"
    $r = Invoke-Api POST "$cvUrl/import" @{ skills = $true; educations = $true; experiences = $true } -Token $token
    Assert-Check "Import -> 33 compétences, 2 formations, 2 expériences" ($r.Code -eq 200 -and $r.Json.skillsAdded -eq 33 -and
        $r.Json.educationsAdded -eq 2 -and $r.Json.experiencesAdded -eq 2)
    $edu = Invoke-Sql ("SELECT e.degree, e.institution, e.start_date, e.end_date FROM educations e JOIN candidate_profiles p ON p.id = e.candidate_id " +
        "JOIN users u ON u.id = p.user_id WHERE u.email = '$email' ORDER BY e.start_date DESC")
    Assert-Check "MySQL educations : ingénieur 2019-2022, licence 2016-2019" ($edu.Count -eq 2 -and $edu[0][1] -eq "École Supérieure Fictive d'Ingénieurs" -and
        $edu[0][2] -eq "2019-01-01" -and $edu[1][0] -eq "Licence en Informatique")
    $exp = Invoke-Sql ("SELECT x.job_title, x.company_name, x.location, x.start_date, COALESCE(x.end_date, 'NULL') FROM experiences x " +
        "JOIN candidate_profiles p ON p.id = x.candidate_id JOIN users u ON u.id = p.user_id WHERE u.email = '$email' ORDER BY x.start_date DESC")
    Assert-Check "MySQL experiences : poste actuel (fin NULL) et stage" ($exp.Count -eq 2 -and $exp[0][0] -eq "Développeuse Full Stack" -and
        $exp[0][2] -eq "Tunis" -and $exp[0][4] -eq "NULL" -and $exp[1][3] -eq "2022-02-01" -and $exp[1][4] -eq "2022-07-01")
    $profile = (Invoke-Sql "SELECT p.education_level, p.years_of_experience, (SELECT COUNT(*) FROM candidate_skills cs WHERE cs.candidate_id = p.id) FROM candidate_profiles p JOIN users u ON u.id = p.user_id WHERE u.email = '$email'")[0]
    Assert-Check "MySQL candidate_profiles : BAC_PLUS_5, 4 ans, 33 compétences" ($profile[0] -eq "BAC_PLUS_5" -and $profile[1] -eq "4" -and $profile[2] -eq "33")
    $r = Invoke-Api POST "$cvUrl/import" @{ skills = $true; educations = $true; experiences = $true } -Token $token
    Assert-Check "Second import : aucun doublon" ($r.Json.skillsAdded -eq 0 -and $r.Json.educationsAdded -eq 0 -and $r.Json.experiencesAdded -eq 0)

    "--- Données personnelles dans les journaux FastAPI ---"
    Stop-AiKeepLog $ai
    $ai = $null
    $log = $script:aiLogs
    Assert-Check "Journal : compteurs uniquement (« CV analysé : 1 page(s) ... »)" ($log -match "CV analysé : 1 page\(s\), \d+ caractères, 24 technologies")
    Assert-Check "Journal : refus tracés par code (NO_TEXT, ENCRYPTED_PDF, INVALID_PDF)" ($log -match "CV refusé : NO_TEXT" -and $log -match "CV refusé : ENCRYPTED_PDF" -and $log -match "CV refusé : INVALID_PDF")
    $leaks = @("Camille", "camille.exemple", "+216", "Société Exemple", "Génie Logiciel", "Alex Sample") | Where-Object { $log.Contains($_) }
    Assert-Check "Journal : aucun contenu de CV ni donnée personnelle" ($leaks.Count -eq 0)
    Assert-Check "Journal : la clé interne n'apparaît pas" (-not $log.Contains($aiKey))

    "--- Suppression ---"
    $current = (CvRow $email "c.stored_file_name")[0][0]
    Assert-Check "DELETE /api/candidate/cv -> 204" ((Invoke-Api DELETE $cvUrl -Token $token).Code -eq 204)
    Assert-Check "MySQL : ligne cvs supprimée (et cv_skills)" ((CvRow $email "c.id").Count -eq 0)
    Assert-Check "Fichier supprimé du disque" (-not (Test-Path (Join-Path $cvDir $current)))
    Assert-Check "Les formations importées restent dans le profil" ((Invoke-Api GET /api/candidate/profile -Token $token).Json.educations.Count -eq 2)
} finally {
    Stop-Ai $ai
    $ai = Start-Ai
    try {
        # Suppression du compte par l'admin : le fichier du CV doit aussi disparaître du disque.
        $r = Upload "cv_minimal_fr.pdf" $token
        $last = (CvRow $email "c.stored_file_name")[0][0]
        if ($candidate.Json.user.id) { Invoke-Api DELETE "/api/admin/users/$($candidate.Json.user.id)" -Token $adminToken | Out-Null }
        if ($recruiter.Json.user.id) { Invoke-Api DELETE "/api/admin/users/$($recruiter.Json.user.id)" -Token $adminToken | Out-Null }
        Assert-Check "Compte supprimé par l'admin : fichier du CV effacé" ($r.Code -eq 200 -and -not (Test-Path (Join-Path $cvDir $last)))
    } finally {
        Stop-Ai $ai
        Remove-Item $aiLog, "$aiLog.out" -ErrorAction SilentlyContinue
    }
}

Write-Summary
