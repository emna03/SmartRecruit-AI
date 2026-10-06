# Service IA — FastAPI

Service Python sans état, appelé **uniquement par le backend Spring Boot** (jamais par le navigateur).
Il ne se connecte pas à MySQL : il reçoit des données, calcule et renvoie du JSON.

Modules prévus : CV Parser / NLP (étape 8), Matching CV ↔ offre (étape 9), Chatbot RH (étape 10),
Machine Learning (étape 11). Disponibles : le socle (configuration, sécurité, erreurs, `/health`), le
**CV Parser** (méthode, pipeline, évaluation et limites : [docs/ai/cv-parser.md](../docs/ai/cv-parser.md)), le
**Matching** CV ↔ offre (formule, pondérations, limites, version : [docs/ai/matching.md](../docs/ai/matching.md)), le
**Chatbot RH** (NLP, intentions, exemples, sécurité, limites : [docs/ai/chatbot.md](../docs/ai/chatbot.md)) et le
**Machine Learning** : risque de turnover (dataset IBM fictif, modèles comparés, métriques réelles, limites :
[docs/ai/turnover-ml.md](../docs/ai/turnover-ml.md)).

Versions : **Python 3.13** (3.12 minimum), FastAPI 0.142, Starlette 1.7, Pydantic 2.13, Uvicorn 0.54,
pdfplumber 0.11.10, rapidfuzz 3.14.6, scikit-learn 1.7.2, numpy 2.5.3, pandas 3.0.6, scipy 1.16.3, joblib 1.6.0.

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
| `AI_ML_MODEL_DIR` | `ml/artifacts` | dossier du modèle de turnover entraîné (`turnover_model.joblib` + `.json`) |

## Modèle de turnover (entraînement hors ligne)

```powershell
.\.venv\Scripts\python.exe -m ml.download_dataset   # dataset IBM fictif, empreinte SHA-256 vérifiée
.\.venv\Scripts\python.exe -m ml.train              # compare, évalue, sauvegarde le modèle et le rapport
```

Le modèle (`ml/artifacts/`) et le dataset (`ml/data/`) ne sont pas versionnés ; le rapport des métriques réellement
obtenues l'est (`ml/reports/turnover_evaluation.json`, identique à chaque entraînement). Sans modèle valide (absent,
empreinte SHA-256 ou version de scikit-learn différente), le module `ml` est annoncé indisponible et la prédiction
renvoie 503.

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
| POST | `/api/chatbot/ask` | clé interne | `{message, contextOfferId?, offers: [offres publiées : id, title, company, location, contractType, minExperienceYears, educationLevel, salaryMin, salaryMax, requiredSkills]}` → `ChatbotResponse` (intention, confiance, réponse, `offerIds` pris dans le catalogue reçu, entités extraites, suggestions, actions, `chatbotVersion`) ; 400 si question vide ou > 500 caractères, offre de contexte hors catalogue, doublon ou champ non prévu |
| POST | `/api/ml/turnover/predict` | clé interne | 24 variables d'un employé (entiers bornés, booléen `overTime`, codes de département, poste, domaine, déplacements) → `TurnoverPredictionResponse` (score 0–1, niveau FAIBLE / MOYEN / ÉLEVÉ, seuils, 5 facteurs au plus, modèle et métriques de test) ; 400 si champ manquant, invalide, incohérent ou non prévu (ex. `age`, `gender`) ; 503 si aucun modèle n'est chargé |

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
powershell -ExecutionPolicy Bypass -File ..\scripts\e2e-step10.ps1 -BaseUrl http://localhost:8080
powershell -ExecutionPolicy Bypass -File ..\scripts\e2e-step11.ps1 -BaseUrl http://localhost:8080
```

Le script `e2e-step7.ps1` vérifie la chaîne réelle Spring Boot → FastAPI (backend et MySQL démarrés, service IA arrêté :
le script le lance lui-même), y compris les pannes : service arrêté, clés différentes.
`e2e-step8.ps1` vérifie le parcours réel d'un CV (upload → Spring Boot → FastAPI → MySQL), les refus, la panne du
service IA, l'import dans le profil et l'absence de données personnelles dans les journaux (mêmes prérequis).
`e2e-step9.ps1` vérifie le matching réel (vrais CV analysés → score → MySQL → classement recruteur), les cas sans
score, la panne du service IA, l'invalidation des scores et les journaux (mêmes prérequis).
`e2e-step10.ps1` vérifie le chatbot réel (questions → Spring Boot → FastAPI → réponses comparées aux offres publiées
de MySQL), les accès, les validations, la panne du service IA et les journaux (mêmes prérequis).
`e2e-step11.ps1` réentraîne le modèle (rapport identique au rapport versionné), puis vérifie les vraies prédictions
via Spring Boot, les accès, les entrées invalides, la panne du service IA et les journaux (dataset téléchargé).

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
│   └── routes/        health.py, status.py, cv.py, matching.py, chatbot.py, ml.py
├── schemas/           modèles Pydantic d'entrée/sortie (cv.py, matching.py, chatbot.py, ml.py)
├── resources/         skills.json : lexique (nom, alias, catégorie) ; matching_rules.json : implications et familles ;
│                      chatbot_knowledge.json : exemples par intention, réponses de la FAQ, suggestions
└── services/          MODULES = disponibilité (ml : seulement si le modèle est chargé)
    ├── cv_parser/     pdf_text → text → sections → lexicon, education, experience, dates, languages → pipeline
    ├── matching/      skills (correspondances) → scoring (formule) → explanation → engine (version 1.0.0)
    ├── chatbot/       nlp (entités) → intents (TF-IDF) → offers (recherche) → engine (version 1.0.0)
    └── ml/            features → dataset (vérifications) → training (comparaison, évaluation) → explain → model
scripts/               evaluate_cv_parser.py (évaluation sur la vérité terrain)
tests/                 pytest : configuration, sécurité, erreurs, /health, CV Parser, matching, chatbot, ML ; fixtures/cv/ (CV fictifs)
ml/                    download_dataset.py, train.py, reports/ (versionné) ; data/ et artifacts/ ignorés par Git
```

Ajouter un module : créer `app/api/routes/<module>.py`, l'inclure dans `internal_router` (protection automatique ;
le test `test_every_internal_route_requires_the_key` le vérifie), passer son entrée de `MODULES` à `True`.

## Données personnelles

Le service ne stocke rien : les CV reçus restent en mémoire le temps du traitement. Les journaux ne contiennent
ni la clé, ni les en-têtes, ni le contenu des requêtes : pour un CV, seulement des compteurs (pages, caractères,
nombre de technologies…) ou le code de refus. Emails, liens et téléphones sont masqués dans le texte renvoyé.
Le matching ne reçoit que des noms de compétences, une durée et un niveau d'études (aucune identité) et ne journalise
que le score et des compteurs. Le chatbot reçoit la question et des données publiques d'offres publiées (rien sur
le candidat) et ne journalise que l'intention et des compteurs, jamais la question. Le module ML reçoit 24 variables
professionnelles sans identité ni variable sensible (âge, genre, situation familiale refusés) et ne journalise que le
score, le niveau et le modèle. Aucun LLM ni service externe n'est appelé.
