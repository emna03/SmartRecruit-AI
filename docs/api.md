# API métier — profils, entreprises, offres, candidatures

Authentification et administration des comptes : voir [security.md](security.md).
Toutes les routes (sauf `/api/jobs/**`) exigent `Authorization: Bearer <jeton>`.
Format d'erreur commun : `{timestamp, status, error, message, path, fieldErrors?}`.

**Règle d'appartenance** : une ressource appartenant à un autre utilisateur (expérience d'un autre candidat,
offre d'une autre entreprise, candidature d'autrui…) renvoie **404**, pour ne pas révéler son existence.

## Compétences (tout utilisateur connecté)

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/api/skills?query=jav&limit=20` | recherche dans le référentiel (autocomplétion) |

Les compétences sont partagées : envoyer `"java"` ou `" JAVA "` réutilise la compétence `Java` existante
(comparaison insensible à la casse, espaces normalisés, doublons ignorés).

## Profil candidat (`CANDIDAT`)

| Méthode | Chemin | Corps |
|---|---|---|
| GET | `/api/candidate/profile` | — (profil complet : compétences, formations, expériences, `hasCv`) |
| PUT | `/api/candidate/profile` | `{firstName, lastName, headline, summary, phone, location, yearsOfExperience, educationLevel}` |
| PUT | `/api/candidate/profile/skills` | `{skills: ["Java", "Angular"]}` — remplace la liste (50 max) |
| POST | `/api/candidate/profile/educations` | `{degree, fieldOfStudy, institution, startDate, endDate, description}` |
| PUT / DELETE | `/api/candidate/profile/educations/{id}` | idem / — |
| POST | `/api/candidate/profile/experiences` | `{jobTitle, companyName, location, startDate, endDate, description}` |
| PUT / DELETE | `/api/candidate/profile/experiences/{id}` | idem / — |

Toutes ces routes renvoient le profil complet mis à jour. `educationLevel` ∈ `BAC, BAC_PLUS_2, BAC_PLUS_3, BAC_PLUS_5, DOCTORAT`.
Expérience : `startDate` obligatoire et non future ; `endDate` absente = poste actuel (`current: true`) ;
fin avant début → 400.

## CV du candidat (`CANDIDAT`)

Le candidat ne gère que **son** CV (retrouvé grâce au jeton, aucun identifiant dans l'URL). Analyse par le
service IA (CV Parser) : méthode, stockage et limites dans [ai/cv-parser.md](ai/cv-parser.md).

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| GET | `/api/candidate/cv` | — | CV + analyse ; 404 si aucun CV |
| POST | `/api/candidate/cv` | multipart, champ `file` (PDF ≤ `MAX_CV_SIZE_MB`) | import ou remplacement, puis analyse |
| POST | `/api/candidate/cv/analyze` | — | relance l'analyse du CV enregistré (après une panne du service IA) |
| POST | `/api/candidate/cv/import` | `{skills, educations, experiences}` (booléens, `true` par défaut) | ajoute les éléments extraits au profil : `{skillsAdded, educationsAdded, experiencesAdded, skipped, educationLevelSet, yearsOfExperienceSet, profile}` |
| DELETE | `/api/candidate/cv` | — | 204 ; supprime aussi le fichier |

Réponse CV : `{id, originalFileName, fileSize, parsingStatus (TERMINE | ECHEC), parsingError, parsedAt, updatedAt, analysis}`
— `analysis` = résultat validé du parser (compétences, technologies, formations, expériences, langues, synthèse),
sans le texte du CV.

- Le fichier est contrôlé **par son contenu** (signature `%PDF-`) : le nom et le type MIME envoyés sont ignorés.
  Vide → 400 ; pas un PDF → 400 ; trop volumineux → 413 ; PDF endommagé, chiffré, scanné ou sans texte → 422
  (message du parser ; un CV déjà présent est conservé).
- Service IA arrêté, trop lent ou réponse invalide : le CV est enregistré avec `parsingStatus: ECHEC` et un message ;
  `POST /analyze` relance l'analyse. L'import dans le profil exige une analyse `TERMINE` (sinon 400).
- Import : formations sans établissement et expériences sans entreprise ou date de début ignorées (`skipped`),
  doublons ignorés, 50 compétences maximum ; niveau d'études et années d'expérience remplis seulement s'ils sont vides.

## Profil et entreprise du recruteur (`RECRUTEUR`)

| Méthode | Chemin | Corps |
|---|---|---|
| GET / PUT | `/api/recruiter/profile` | `{firstName, lastName, jobTitle, phone}` |
| GET | `/api/recruiter/company` | — (404 si pas encore d'entreprise) |
| POST | `/api/recruiter/company` | `{name, description, sector, website, location}` → 201, rattache le recruteur |
| PUT | `/api/recruiter/company` | idem |

Un recruteur appartient à une seule entreprise ; nom d'entreprise unique (insensible à la casse) → sinon 409.
Sans entreprise, la création d'offre renvoie 400 « Vous devez d'abord créer votre entreprise ».

## Offres d'emploi

### Recruteur (`RECRUTEUR`, offres de son entreprise uniquement)

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/api/recruiter/jobs` | offres de l'entreprise, avec `applicationCount` |
| GET | `/api/recruiter/jobs/{id}` | détail |
| POST | `/api/recruiter/jobs` | création (statut `BROUILLON` par défaut) |
| PUT | `/api/recruiter/jobs/{id}` | modification |
| PATCH | `/api/recruiter/jobs/{id}/status` | `{status: BROUILLON \| PUBLIEE \| FERMEE}` |
| DELETE | `/api/recruiter/jobs/{id}` | suppression (supprime aussi ses candidatures) |

Corps : `{title, description, location, contractType, minExperienceYears, educationLevel, salaryMin, salaryMax, requiredSkills[], status?}`
— `contractType` ∈ `CDI, CDD, STAGE, ALTERNANCE, FREELANCE` ; `requiredSkills` : 1 à 30 ;
`salaryMax ≥ salaryMin` sinon 400.

### Public (sans jeton)

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/api/jobs` | offres **publiées** uniquement, paginées, plus récentes d'abord |
| GET | `/api/jobs/{id}` | détail d'une offre publiée (404 si brouillon ou fermée) |

Filtres combinables : `keyword` (titre ou description), `location`, `contractType`, `skill`, `educationLevel`,
`maxExperienceYears` (offres accessibles avec N années), `companyId` ; pagination `page` (dès 0) et `size` (10 par défaut, 50 max).
Réponse : `{content[], page, size, totalElements, totalPages}`.

## Candidatures

### Candidat (`CANDIDAT`)

| Méthode | Chemin | Description |
|---|---|---|
| POST | `/api/candidate/applications` | `{jobOfferId, coverLetter?}` → 201 `EN_ATTENTE` |
| GET | `/api/candidate/applications` | mes candidatures (avec l'offre et l'entreprise) |
| GET | `/api/candidate/applications/{id}` | détail |

Offre inexistante → 404 ; offre non publiée → 400 ; déjà postulé → 409.

### Recruteur (`RECRUTEUR`)

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/api/recruiter/jobs/{jobId}/applications` | candidats d'une offre **classés par score de compatibilité décroissant** (résumé, compétences, `matching`) |
| GET | `/api/recruiter/applications/{id}` | détail avec le profil complet du candidat et son score (`matching`) |
| PATCH | `/api/recruiter/applications/{id}/status` | `{status: EN_ATTENTE \| PRESELECTIONNE \| ACCEPTE \| REFUSE}` |

Le candidat voit immédiatement le nouveau statut. Les candidats sans score (pas de CV analysé) suivent les candidats
classés, du plus récent au plus ancien ; `candidate.cvAnalyzed` indique si un score est calculable.

## Score de compatibilité (matching CV ↔ offre)

Formule, pondérations, limites et version de l'algorithme : [ai/matching.md](ai/matching.md).
Le score est une aide pour le recruteur, jamais une décision automatique.

| Rôle | Méthode | Chemin | Description |
|---|---|---|---|
| `CANDIDAT` | GET | `/api/candidate/jobs/{jobId}/matching` | score du candidat connecté, recalculé à partir de son CV analysé et enregistré |
| `RECRUTEUR` | POST | `/api/recruiter/jobs/{jobId}/matching` | recalcule les scores des candidats de l'offre ayant un CV analysé → `{computed, withoutAnalyzedCv, applications[]}` (classées) |

Réponse candidat : `{status, message, result}` avec `status` ∈ `CALCULE`, `CV_ABSENT`, `CV_NON_ANALYSE`,
`IA_INDISPONIBLE` (dans ce dernier cas, `result` = dernier score enregistré s'il existe).

Résultat (`result` / `matching`) : `{score, skillsScore, experienceScore, educationScore, matchedSkills[{name, matchedBy,
matchType: EXACT|IMPLIED|RELATED, credit}], missingSkills[], explanation, algorithmVersion, computedAt, stale}` —
`experienceScore` / `educationScore` valent `null` quand l'offre n'exige pas ce critère ; `stale` signale un score
calculé avant la dernière mise à jour du CV.

- Offre non publiée sans candidature, ou inexistante → 404 ; offre d'une autre entreprise (recruteur) → 404.
- Service IA indisponible ou réponse invalide : candidat → `IA_INDISPONIBLE` (200) ; recalcul recruteur → 503.
- Le score est aussi calculé automatiquement après une candidature (sans faire échouer la candidature).
- Scores supprimés quand le CV est supprimé ou quand les compétences, l'expérience ou le niveau de l'offre changent.

## Chatbot RH (`CANDIDAT`)

Fonctionnement, intentions, sécurité et limites : [ai/chatbot.md](ai/chatbot.md).

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| POST | `/api/candidate/chatbot/messages` | `{message, jobOfferId?}` — question (1 à 500 caractères) et offre en cours de discussion (facultative) | `{intent, answer, confidence, offers[], totalMatches, contextOffer, entities, suggestions[], actions[], chatbotVersion}` |

- `offers` / `contextOffer` : `{id, title, companyName, location, contractType, minExperienceYears, educationLevel, requiredSkills}`,
  relus dans MySQL ; uniquement des offres **publiées** (au plus 5 par réponse).
- `entities` : ce que l'assistant a compris (`skills`, `experienceYears`, `educationLevel`, `contractType`, `location`, `keywords`) ;
  `actions` ∈ `OPEN_JOBS`, `OPEN_CV`, `OPEN_APPLICATIONS`, `OPEN_PROFILE` (liens vers les pages de l'application).
- Question vide, blanche ou trop longue → 400 ; anonyme → 401 ; recruteur ou admin → 403. `jobOfferId` d'une offre
  non publiée ou inexistante : ignoré.
- Service IA indisponible, ou réponse invalide ou citant une offre non envoyée → 503. Les questions ne sont ni
  enregistrées ni journalisées.

## Risque de turnover — Machine Learning (`ADMIN`, `RECRUTEUR`)

Dataset, modèles comparés, métriques réelles, limites et éthique : [ai/turnover-ml.md](ai/turnover-ml.md).
Estimation de démonstration, jamais une décision : rien n'est enregistré et aucune action n'est déclenchée.

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| POST | `/api/hr/turnover/predictions` | 24 variables d'un employé (voir ci-dessous), sans identité ni âge, genre ou situation familiale | `{probability, riskLevel, mediumThreshold, highThreshold, factors[], model}` |

- Corps : entiers `distanceFromHome` (1–29), `education` (1–5), `environmentSatisfaction`, `jobInvolvement`,
  `jobSatisfaction`, `relationshipSatisfaction`, `workLifeBalance` (1–4), `jobLevel` (1–5), `monthlyIncome`
  (1 009–19 999), `numCompaniesWorked` (0–9), `percentSalaryHike` (11–25), `performanceRating` (3–4),
  `stockOptionLevel` (0–3), `totalWorkingYears` (0–40), `trainingTimesLastYear` (0–6), `yearsAtCompany` (0–40),
  `yearsInCurrentRole` (0–18), `yearsSinceLastPromotion` (0–15), `yearsWithCurrManager` (0–17) ; booléen `overTime` ;
  `businessTravel` ∈ `NON_TRAVEL, TRAVEL_RARELY, TRAVEL_FREQUENTLY` ; `department` ∈ `SALES, RESEARCH_DEVELOPMENT,
  HUMAN_RESOURCES` ; `jobRole` (9 postes, compatible avec le département) ; `educationField` (6 domaines).
- Réponse : `probability` entre 0 et 1 (score du modèle, pas une certitude) ; `riskLevel` ∈ `FAIBLE` (< 0,30),
  `MOYEN` (< 0,50), `ELEVE` ; `factors` : au plus 5 `{feature, label, impact, direction: INCREASES_RISK |
  DECREASES_RISK, referenceValue}` ; `model` : `{name, version, dataset, explanationMethod, testAccuracy,
  testPrecision, testRecall, testF1, testRocAuc}`.
- Champ manquant, hors bornes, de mauvais type ou incohérent (ancienneté > expérience totale, poste hors
  département…) → 400 ; anonyme → 401 ; candidat → 403 ; service IA indisponible, modèle non entraîné ou réponse
  invalide → 503.

## Dashboards et statistiques

KPI, origine des données, graphiques et limites : [dashboards.md](dashboards.md). Statistiques calculées à chaque
appel depuis MySQL ; aucun paramètre : le périmètre vient du jeton (paramètre ajouté ignoré).

| Méthode | Chemin | Rôle | Réponse |
|---|---|---|---|
| GET | `/api/candidate/dashboard` | `CANDIDAT` | `{totalApplications, applicationsByStatus, publishedOffers, publishedOffersNotApplied, cv: {state: ABSENT \| IMPORTE \| ANALYSE, parsingStatus, updatedAt, parsedAt, extractedSkills}, scoredOffers, topMatches[]: {jobOfferId, title, companyName, offerStatus, score, computedAt, stale, applied}}` |
| GET | `/api/recruiter/dashboard` | `RECRUTEUR` | `{hasCompany, companyName, totalOffers, offersByStatus, totalApplications, applicationsByStatus, applicationsByMonth[]: {month, count}, scoredApplications, averageMatchingScore, offers[]: {id, title, status, createdAt, applications, applicationsByStatus, scoredApplications, averageScore, bestScore}, topCandidates[]: {applicationId, candidateName, jobOfferId, jobTitle, score, status, stale}}` |
| GET | `/api/admin/dashboard` | `ADMIN` | `{totalUsers, usersByRole, disabledUsers, companies, recruitersWithoutCompany, totalOffers, offersByStatus, publishedOffersByContract, totalApplications, applicationsByStatus, candidatesWithCv, cvsByParsingStatus, storedMatchingScores, averageMatchingScore, topRequestedSkills[]: {skill, offers}, activityByMonth[]: {month, newUsers, newOffers, applications}}` |

- Les répartitions (`…ByStatus`, `usersByRole`, `publishedOffersByContract`) contiennent toutes les valeurs de
  l'énumération, à 0 si aucune ligne. Mois au format `AAAA-MM`, 6 derniers mois UTC, mois en cours compris.
- Moyennes arrondies à 0,1 ; `null` quand aucun score n'existe. `topMatches` / `topCandidates` : 5 au plus.
- Recruteur sans entreprise → 200 avec `hasCompany: false` et des compteurs à 0. Anonyme → 401 ; autre rôle → 403.

## Administration (`ADMIN`)

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/api/admin/companies` | toutes les entreprises (nombre d'offres et de recruteurs) |
| GET / PUT / DELETE | `/api/admin/companies/{id}` | détail / modification / suppression (offres et candidatures supprimées, recruteurs détachés) |
| GET | `/api/admin/jobs?status=PUBLIEE` | toutes les offres (filtre optionnel) |
| PATCH | `/api/admin/jobs/{id}/status` | modération (ex. fermer une offre) |
| DELETE | `/api/admin/jobs/{id}` | suppression |
| GET | `/api/admin/ai/status` | état du service IA vu par Spring Boot : `{status, service, version, modules, responseTimeMs}` ; 503 si le service IA ne répond pas (voir [security.md](security.md#communication-interne-spring-boot--fastapi)) |

## Tests

- `CandidateProfileApiTest` (7), `RecruiterCompanyApiTest` (5), `JobOfferApiTest` (7), `ApplicationApiTest` (5) :
  validations, rôles, appartenance (404), recherche/filtres/pagination, cascades de suppression.
- `scripts/e2e-step5.ps1` : scénario complet sur le backend lancé avec MySQL (41 vérifications)
  — `powershell -ExecutionPolicy Bypass -File scripts/e2e-step5.ps1 -BaseUrl http://localhost:8080`.
- CV : `CandidateCvApiTest` (12), `CvFileValidatorTest` (13) ; `scripts/e2e-step8.ps1` : parcours réel
  Spring Boot → FastAPI → MySQL (47 vérifications, service IA arrêté au départ : le script le lance lui-même).
- Matching : `MatchingApiTest` (11) ; `scripts/e2e-step9.ps1` : vrais CV → FastAPI → MySQL, classement recruteur,
  panne du service IA, invalidation (50 vérifications, service IA arrêté au départ).
- Chatbot : `ChatbotApiTest` (9) ; `scripts/e2e-step10.ps1` : questions réelles → Spring Boot → FastAPI, réponses
  comparées aux offres publiées de MySQL, panne du service IA, journaux (42 vérifications, service IA arrêté au départ).
- Turnover : `TurnoverPredictionApiTest` (11), `TurnoverAiUnreachableTest` (1) ; `scripts/e2e-step11.ps1` :
  réentraînement reproductible, vraies prédictions Spring Boot → FastAPI → modèle, entrées invalides, accès, panne du
  service IA, journaux (36 vérifications, service IA arrêté au départ, dataset téléchargé).
- Dashboards : `DashboardApiTest` (8), `DashboardQueryCountTest` (1) ; `scripts/e2e-step12.ps1` : chaque chiffre des
  trois dashboards comparé à une requête SQL sur MySQL, avant/après candidatures et changements de statut, isolation
  entre recruteurs, accès (102 vérifications, service IA arrêté au départ).
