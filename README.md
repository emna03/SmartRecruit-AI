# SmartRecruit AI — Plateforme intelligente de recrutement

Mini-projet universitaire « IA for Software Engineering » (A.U. 2026-2027).

Application web de recrutement : les entreprises publient des offres, les candidats déposent leur CV,
et un module d'IA analyse les CV, calcule un score de compatibilité explicable avec les offres,
répond aux questions via un Assistant RH et estime un risque de turnover (Machine Learning).

> **État du projet : étapes 1 à 13 terminées** — architecture, MySQL + Spring Boot, entités JPA, JWT + rôles,
> profils / entreprises / offres / candidatures, frontend Angular, service IA FastAPI, CV Parser + NLP, matching
> CV ↔ offre, Assistant RH, Machine Learning (turnover), dashboards, puis finalisation (audit, tests de
> non-régression, sécurité, documentation, guide de démonstration).

**Pour comprendre le projet en 10 minutes : [docs/project-overview.md](docs/project-overview.md).**
**Pour la soutenance : [docs/demo-guide.md](docs/demo-guide.md).**

## Fonctionnalités

| Rôle | Fonctionnalités |
|---|---|
| Candidat | inscription, profil, import d'un CV PDF analysé automatiquement (NLP), résumé professionnel généré par LLM (Gemini), recherche d'offres, score de compatibilité expliqué, candidature et suivi, Assistant RH, dashboard |
| Recruteur | entreprise, offres (brouillon → publiée → fermée), candidats classés par score, changement de statut, risque de turnover, dashboard |
| Admin | utilisateurs (activer, désactiver, supprimer), entreprises, offres, état du service IA, risque de turnover, dashboard global |

## Versions utilisées

| Composant | Version |
|---|---|
| Java | 21 (OpenJDK 21.0.12) |
| Spring Boot | 4.1.1 (Maven Wrapper fourni : Maven n'a pas besoin d'être installé) |
| MySQL | 8.4 (image Docker `mysql:8.4`) |
| Python | 3.13 (3.12 minimum) — FastAPI 0.142, Uvicorn 0.54, Pydantic 2.13, pdfplumber 0.11.10, rapidfuzz 3.14.6, scikit-learn 1.7.2, pandas 3.0.6, numpy 2.5.3 |
| Node.js | 20.19+, 22.12+ ou 24+ (24.13 utilisé) |
| Angular | 21.2 — TypeScript 5.9, Bootstrap 5.3, Chart.js 4.5, Vitest 4, Playwright 1.63 |

Versions exactes : `backend/pom.xml`, `ai-service/requirements.txt`, `frontend/package.json`.

## Prérequis

- Git, **Docker Desktop** (pour MySQL), **JDK 21**, **Python 3.13**, **Node.js** (voir versions ci-dessus).
- Microsoft Edge (utilisé par les tests Playwright) — facultatif pour lancer l'application.
- Les commandes ci-dessous sont pour Windows (PowerShell) ; sous Linux/macOS, remplacer `mvnw.cmd` par `./mvnw` et
  `.venv\Scripts\python.exe` par `.venv/bin/python`.

## Démarrage en une commande

Une fois l'installation ci-dessous faite (une seule fois), depuis la racine du projet :

```powershell
.\demarrer      # MySQL + backend + service IA + Angular, puis ouvre l'application et la page des modèles IA
.\arreter       # arrête tout
```

Si le port 4200 est déjà pris par une autre application, SmartRecruit utilise automatiquement le port suivant
(4201…), affiché à la fin du démarrage. La page des modèles IA (Swagger, `http://127.0.0.1:8000/docs`) n'est
accessible que depuis la machine locale ; bouton « Authorize » : valeur `AI_INTERNAL_API_KEY` du `.env`.

## Installation pas à pas

### 1. Cloner le dépôt

```powershell
git clone https://github.com/emna03/SmartRecruit-AI.git smartrecruit-ai
cd smartrecruit-ai
git checkout main
```

### 2. Configurer les variables d'environnement

```powershell
Copy-Item .env.example .env
```

Puis éditer `.env` (jamais commité) :

| Variable | À renseigner |
|---|---|
| `DB_PASSWORD`, `DB_ROOT_PASSWORD` | mots de passe MySQL de votre choix |
| `JWT_SECRET` | au moins 32 caractères aléatoires |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | compte administrateur créé au premier démarrage |
| `AI_INTERNAL_API_KEY` | au moins 16 caractères aléatoires (clé partagée Spring Boot ↔ FastAPI) |

Générer des valeurs aléatoires en PowerShell :
`[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))`.
Si le port 3306 ou 8080 est déjà occupé sur votre machine, changer `DB_PORT` ou `SERVER_PORT` dans `.env`
(le reste de la configuration suit automatiquement).

### 3. Démarrer MySQL (Docker)

```powershell
docker compose up -d mysql
docker compose ps          # attendre l'état "healthy"
```

La base `smartrecruit` et son utilisateur sont créés par le conteneur ; les tables sont créées par Flyway au premier
démarrage du backend (migration `V1`). MySQL n'est exposé que sur `127.0.0.1`.

### 4. Démarrer le backend Spring Boot

```powershell
cd backend
.\mvnw.cmd spring-boot:run
# Vérifier : http://localhost:8080/actuator/health -> {"status":"UP"}
```

### 5. Installer et démarrer le service IA FastAPI

Dans un deuxième terminal :

```powershell
cd ai-service
py -3.13 -m venv .venv                                         # environnement virtuel dédié
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m ml.download_dataset             # dataset IBM fictif (SHA-256 vérifié)
.\.venv\Scripts\python.exe -m ml.train                        # entraîne le modèle de turnover + rapport
.\.venv\Scripts\python.exe -m app
# Vérifier : http://127.0.0.1:8000/health -> {"status":"UP",...}
```

Le dataset et le modèle ne sont pas versionnés (fichiers locaux) ; sans modèle, seul le module turnover est
indisponible (503). Détails : [ai-service/README.md](ai-service/README.md).

### 6. Démarrer le frontend Angular

Dans un troisième terminal :

```powershell
cd frontend
npm install
npm start
```

### 7. Utiliser l'application

Ouvrir **http://localhost:4200** (le serveur Angular redirige `/api` vers le backend).

- Se connecter en **admin** avec `ADMIN_EMAIL` / `ADMIN_PASSWORD` ; le dashboard admin indique si le module ML est chargé.
- Créer un compte **recruteur** puis un compte **candidat** depuis « Inscription ».
- Un CV fictif prêt à importer : `ai-service/tests/fixtures/cv/cv_dev_fullstack_fr.pdf`.
- Suivre le scénario de [docs/demo-guide.md](docs/demo-guide.md).

## Tests

| Suite | Commande | Prérequis | Résultat à l'étape 13 |
|---|---|---|---|
| Service IA (pytest) | `cd ai-service; .\.venv\Scripts\python.exe -m pytest` | aucun | 324 réussis |
| Backend (JUnit) | `cd backend; .\mvnw.cmd test` | aucun | 153 réussis |
| Frontend (Vitest) | `cd frontend; npx ng test --watch=false` | aucun | 122 réussis |
| Build Angular | `cd frontend; npx ng build` | aucun | réussi |
| Parcours navigateur (Playwright) | `cd frontend; npx playwright test` | MySQL, backend et service IA démarrés, modèle entraîné | 34 réussis |
| Scripts de bout en bout | voir ci-dessous | MySQL et backend démarrés, dataset téléchargé, **service IA arrêté** (chaque script le lance lui-même) | 400 vérifications réussies |

```powershell
# depuis la racine ; remplacer 8080 si SERVER_PORT est différent
foreach ($s in "e2e-auth","e2e-step5","e2e-step7","e2e-step8","e2e-step9","e2e-step10","e2e-step11","e2e-step12","e2e-step13") {
  powershell -ExecutionPolicy Bypass -File "scripts\$s.ps1" -BaseUrl http://localhost:8080
}
```

| Script | Ce qu'il vérifie (sur l'application réelle, avec contrôle dans MySQL) |
|---|---|
| `e2e-auth.ps1` | inscription, connexion, JWT, rôles, administration des comptes |
| `e2e-step5.ps1` | profils, entreprises, offres, candidatures, ownership |
| `e2e-step7.ps1` | liaison Spring Boot → FastAPI, clé interne, panne du service IA |
| `e2e-step8.ps1` | import et analyse de CV, refus, journaux sans données personnelles |
| `e2e-step9.ps1` | matching, classement, invalidation des scores |
| `e2e-step10.ps1` | Assistant RH, réponses comparées aux offres publiées |
| `e2e-step11.ps1` | réentraînement reproductible et prédictions de turnover |
| `e2e-step12.ps1` | dashboards des trois rôles comparés à MySQL |
| `e2e-step13.ps1` | audit de sécurité final (JWT, rôles, ownership, validation, upload, clé interne, CORS, journaux, secrets Git) |

Tous les tests créent leurs propres comptes (préfixes `e2e*`, domaine `test.local`) et les suppriment à la fin.

## Architecture

```
Utilisateur (navigateur)
        ↓
Angular (4200) ──REST/JWT──> Spring Boot (8080) ──JPA──> MySQL (3306)
                                   ↕
                    REST interne + X-Internal-Api-Key
                                   ↕
                     FastAPI (127.0.0.1:8000)
                       ├─ CV Parser / NLP
                       ├─ Matching
                       ├─ Chatbot (Assistant RH)
                       ├─ Machine Learning (turnover)
                       └─ LLM résumé professionnel (Gemini)
```

## Documentation

| Document | Contenu |
|---|---|
| [docs/project-overview.md](docs/project-overview.md) | **documentation finale** : objectif, architecture, technologies, rôles, fonctionnalités, IA, sécurité, tests, limites |
| [docs/demo-guide.md](docs/demo-guide.md) | **scénario de démonstration** pour la soutenance, avec les explications orales |
| [docs/architecture.md](docs/architecture.md) | diagramme, flux principaux, structure des modules, ports, choix techniques |
| [docs/database.md](docs/database.md) | modèle de données MySQL |
| [docs/security.md](docs/security.md) | JWT, rôles, codes d'erreur, communication interne, données personnelles, audit final |
| [docs/api.md](docs/api.md) | endpoints métier (profils, entreprises, offres, candidatures, CV) |
| [docs/ai/cv-parser.md](docs/ai/cv-parser.md) | CV Parser : méthode, bibliothèques, évaluation, limites |
| [docs/ai/matching.md](docs/ai/matching.md) | matching : formule, pondérations, exemples, limites, version |
| [docs/ai/chatbot.md](docs/ai/chatbot.md) | Assistant RH : NLP, intentions, exemples, sécurité, limites |
| [docs/ai/turnover-ml.md](docs/ai/turnover-ml.md) | Machine Learning : dataset, modèles comparés, métriques réelles, limites, éthique |
| [docs/ai/llm-professional-summary.md](docs/ai/llm-professional-summary.md) | LLM Gemini : résumé professionnel, prompt engineering, protections |
| [docs/ai/models.md](docs/ai/models.md) | Les 6 modèles IA (LR, RF, XGBoost, Sentence Transformer, K-Means, Gemini) |
| [docs/testing/tools-status.md](docs/testing/tools-status.md) | Outils de test du cours : Selenium VALIDÉ ; Diffblue, Tabnine, Mabl, Testim, Applitools à faire |
| [PROMPTS.md](PROMPTS.md) | prompts de développement (Cursor) vs prompt exécuté par SmartRecruit |
| [docs/dashboards.md](docs/dashboards.md) | dashboards : KPI, origine des données, autorisations, limites |
| [docs/git-workflow.md](docs/git-workflow.md), [docs/team-modules.md](docs/team-modules.md) | workflow Git, répartition des modules |
| [frontend/README.md](frontend/README.md), [ai-service/README.md](ai-service/README.md) | détails du frontend et du service IA |

## Arborescence

```
smartrecruit-ai/
├── backend/        API Spring Boot (Java 21, Maven Wrapper, migrations Flyway)
├── frontend/       Application Angular (tests Vitest et Playwright)
├── ai-service/     Service IA FastAPI (Python)
│   └── ml/         Entraînement ML hors ligne (dataset et modèle non versionnés, rapport d'évaluation versionné)
├── database/       Documentation MySQL et scripts SQL de vérification du schéma
├── docs/           Documentation finale, architecture, sécurité, API, IA, guide de démonstration
├── tests-tools/    Preuves d'outils de test du cours (Selenium VALIDÉ ; autres à faire)
├── scripts/        Scripts de test de bout en bout (PowerShell)
├── .github/        Modèle de Pull Request
├── docker-compose.yml
└── .env.example    Modèle des variables d'environnement
```

## Équipe

| Membre | Module |
|--------|--------|
| P1 | Authentification, utilisateurs, rôles, sécurité |
| P2 | Entreprises, offres, candidatures |
| P3 | CV Parser + NLP |
| P4 | Matching CV/offre + score IA |
| P5 | Chatbot RH + Machine Learning |
| P6 | Frontend, dashboards, intégration |

Détails : [docs/team-modules.md](docs/team-modules.md) — Workflow Git : [docs/git-workflow.md](docs/git-workflow.md)

## Sécurité

Aucun secret n'est versionné : copier `.env.example` en `.env` et renseigner les valeurs localement. Les CV importés
(`backend/uploads/`), le dataset et le modèle ML sont ignorés par Git. Les CV de test sont entièrement fictifs.
Détails : [docs/security.md](docs/security.md).
