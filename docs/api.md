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
| GET | `/api/recruiter/jobs/{jobId}/applications` | candidats d'une offre (résumé + compétences) |
| GET | `/api/recruiter/applications/{id}` | détail avec le profil complet du candidat |
| PATCH | `/api/recruiter/applications/{id}/status` | `{status: EN_ATTENTE \| PRESELECTIONNE \| ACCEPTE \| REFUSE}` |

Le candidat voit immédiatement le nouveau statut. Le score de matching sera ajouté à l'étape du module IA.

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
