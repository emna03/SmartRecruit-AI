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


