# Service IA — FastAPI

Service Python sans état, appelé **uniquement par le backend Spring Boot** (jamais par le navigateur).
Il ne se connecte pas à MySQL : il reçoit des données, calcule et renvoie du JSON.

Modules prévus : CV Parser / NLP (étape 8), Matching CV ↔ offre (étape 9), Chatbot RH (étape 10),
Machine Learning (étape 11). Disponibles : le socle (configuration, sécurité, erreurs, `/health`), le
**CV Parser** (méthode, pipeline, évaluation et limites : [docs/ai/cv-parser.md](../docs/ai/cv-parser.md)) et le
**Matching** CV ↔ offre (formule, pondérations, limites, version : [docs/ai/matching.md](../docs/ai/matching.md)).

Versions : **Python 3.13** (3.12 minimum), FastAPI 0.142, Starlette 1.7, Pydantic 2.13, Uvicorn 0.54,
pdfplumber 0.11.10, rapidfuzz 3.14.6.

## Installation (une seule fois)

Sous Windows, installer Python pour l'utilisateur courant (sans droits administrateur) :

```powershell
winget install --id Python.Python.3.13 --exact --scope user
```

Puis, dans `ai-service/`, créer l'environnement virtuel du projet (les dépendances ne sont jamais installées globalement) :

```powershell
py -3.13 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt   # requirements.txt seul en production
```

Sous Linux/macOS : `python3.13 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt`.

## Configuration

Lue depuis les variables d'environnement, sinon `ai-service/.env`, sinon le `.env` à la racine du dépôt
(le même que Spring Boot, voir `.env.example`). Aucun secret n'est versionné.

| Variable | Défaut | Rôle |
|---|---|---|
| `AI_INTERNAL_API_KEY` | — (obligatoire) | clé partagée avec Spring Boot, 16 caractères minimum ; le service refuse de démarrer sans clé ou avec la valeur d'exemple |
| `AI_SERVICE_URL` | `http://127.0.0.1:8000` | URL utilisée par Spring Boot ; le port d'écoute en est déduit |
| `AI_SERVICE_PORT` | port de `AI_SERVICE_URL` | force un autre port d'écoute |
| `AI_SERVICE_HOST` | `127.0.0.1` | interface d'écoute : le service n'est pas exposé sur le réseau (`0.0.0.0` uniquement dans un conteneur) |
| `AI_LOG_LEVEL` | `info` | `debug`, `info`, `warning`, `error`, `critical` |
| `AI_DOCS_ENABLED` | `false` | `true` pour activer Swagger UI (`/docs`) en développement |
| `MAX_CV_SIZE_MB` | `5` | taille maximale d'un CV reçu (1 à 20 Mo, même valeur que Spring Boot) |
| `AI_CV_MAX_PAGES` | `10` | nombre maximal de pages analysées (1 à 50) |

## Lancement

```powershell
.\.venv\Scripts\python.exe -m app
# GET http://127.0.0.1:8000/health -> {"status":"UP","service":"smartrecruit-ai","version":"0.1.0"}
```

## API

| Méthode | Chemin | Accès | Réponse |
|---|---|---|---|
| GET | `/health` | public | `{status, service, version}` (supervision, aucune donnée sensible) |
| GET | `/api/status` | clé interne | `{status, service, version, modules}` : disponibilité de chaque module IA |
| POST | `/api/cv/parse` | clé interne | corps `application/pdf` brut → résultat structuré `CvParseResponse` (compétences, technologies, formations, expériences, langues) ; 413 trop volumineux, 415 pas `application/pdf`, 400 vide, 422 PDF invalide / chiffré / sans texte / trop de pages |
| POST | `/api/matching/score` | clé interne | `{candidate: {skills, experienceMonths, educationLevel}, offer: {requiredSkills, minExperienceYears, educationLevel}}` → `MatchingResponse` (score 0–100, sous-scores, poids appliqués, compétences trouvées / manquantes, explication, `algorithmVersion`) ; 400 si données invalides ou champ non prévu (ex. `email`) |

Toutes les routes `/api/**` exigent l'en-tête `X-Internal-Api-Key` (sinon **401**). Les erreurs ont le même format
que celles du backend : `{timestamp, status, error, message, path, fieldErrors?}` (400 données invalides,
401 clé absente ou invalide, 404, 405, 500 sans détail interne).

Côté Spring Boot, `AiServiceClient` (package `com.smartrecruit.ai`) envoie la clé, applique le délai
`AI_TIMEOUT_SECONDS` et valide chaque réponse ; `GET /api/admin/ai/status` (ADMIN) vérifie la liaison complète.

## Tests

```powershell
.\.venv\Scripts\python.exe -m pytest           # tests unitaires (sans .env ni réseau)
.\.venv\Scripts\python.exe scripts\evaluate_cv_parser.py   # précision / rappel du CV Parser sur la vérité terrain
.\.venv\Scripts\python.exe tests\fixtures\cv\generate_fixtures.py   # régénère les CV PDF fictifs de test
powershell -ExecutionPolicy Bypass -File ..\scripts\e2e-step7.ps1 -BaseUrl http://localhost:8080
powershell -ExecutionPolicy Bypass -File ..\scripts\e2e-step8.ps1 -BaseUrl http://localhost:8080
powershell -ExecutionPolicy Bypass -File ..\scripts\e2e-step9.ps1 -BaseUrl http://localhost:8080
```

Le script `e2e-step7.ps1` vérifie la chaîne réelle Spring Boot → FastAPI (backend et MySQL démarrés, service IA arrêté :
le script le lance lui-même), y compris les pannes : service arrêté, clés différentes.
`e2e-step8.ps1` vérifie le parcours réel d'un CV (upload → Spring Boot → FastAPI → MySQL), les refus, la panne du
service IA, l'import dans le profil et l'absence de données personnelles dans les journaux (mêmes prérequis).
`e2e-step9.ps1` vérifie le matching réel (vrais CV analysés → score → MySQL → classement recruteur), les cas sans
score, la panne du service IA, l'invalidation des scores et les journaux (mêmes prérequis).

Les CV de `tests/fixtures/cv/` sont **fictifs** (aucune donnée personnelle réelle) : CV développeur FR, data
scientist EN, CV minimal, PDF scanné (image seule), page blanche, PDF chiffré, PDF endommagé, faux PDF.

## Organisation du code

```
app/
├── __main__.py        lancement (python -m app) : lit la configuration puis démarre Uvicorn
├── main.py            create_app() : assemble configuration, erreurs, journalisation et routes
├── core/
│   ├── config.py      paramètres (pydantic-settings), validation de la clé
│   ├── security.py    dépendance require_internal_key (comparaison en temps constant)
│   ├── errors.py      format d'erreur commun avec Spring Boot
│   └── logs.py        une ligne par requête + X-Request-Id ; jamais d'en-tête ni de contenu
├── api/
│   ├── router.py      public_router (/health) et internal_router (/api/**, clé obligatoire)
│   └── routes/        health.py, status.py, cv.py, matching.py (+ chatbot, ml aux étapes suivantes)
├── schemas/           modèles Pydantic d'entrée/sortie (cv.py : CvParseResponse ; matching.py : MatchingRequest/Response)
├── resources/         skills.json : lexique (nom, alias, catégorie) ; matching_rules.json : implications et familles
└── services/          MODULES = disponibilité ; chatbot/, ml/ vides à ce stade
    ├── cv_parser/     pdf_text → text → sections → lexicon, education, experience, dates, languages → pipeline
    └── matching/      skills (correspondances) → scoring (formule) → explanation → engine (version 1.0.0)
scripts/               evaluate_cv_parser.py (évaluation sur la vérité terrain)
tests/                 pytest : configuration, sécurité, erreurs, /health, CV Parser, matching ; fixtures/cv/ (CV fictifs)
ml/                    entraînement hors ligne (data/ et artifacts/ ignorés par Git)
```

Ajouter un module : créer `app/api/routes/<module>.py`, l'inclure dans `internal_router` (protection automatique ;
le test `test_every_internal_route_requires_the_key` le vérifie), passer son entrée de `MODULES` à `True`.

## Données personnelles

Le service ne stocke rien : les CV reçus restent en mémoire le temps du traitement. Les journaux ne contiennent
ni la clé, ni les en-têtes, ni le contenu des requêtes : pour un CV, seulement des compteurs (pages, caractères,
nombre de technologies…) ou le code de refus. Emails, liens et téléphones sont masqués dans le texte renvoyé.
Le matching ne reçoit que des noms de compétences, une durée et un niveau d'études (aucune identité) et ne journalise
que le score et des compteurs. Aucun LLM ni service externe n'est appelé.
