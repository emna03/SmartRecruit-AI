# SmartRecruit AI — Plateforme intelligente de recrutement

Mini-projet universitaire « IA for Software Engineering » (A.U. 2026-2027).

Application web de recrutement : les entreprises publient des offres, les candidats déposent leur CV,
et un module d'IA analyse les CV, calcule un score de compatibilité explicable avec les offres
et répond aux questions via un chatbot RH.

> **État du projet : ÉTAPES 1 (architecture), 2 (MySQL + Spring Boot), 3 (entités JPA), 4 (JWT + rôles), 5 (profils, entreprises, offres, candidatures), 6 (frontend Angular connecté au backend), 7 (socle du service IA FastAPI relié à Spring Boot), 8 (CV Parser + NLP : import d'un CV PDF et extraction automatique des compétences, formations, expériences et langues), 9 (matching CV ↔ offre : score de compatibilité explicable et classement des candidats) et 10 (chatbot RH : questions en langage naturel sur les offres publiées et la plateforme) terminées.** Ce README sera complété à l'étape 14.

## Démarrage rapide (provisoire)

```bash
cp .env.example .env              # remplir les mots de passe et secrets
docker compose up -d mysql        # MySQL 8.4
cd backend && mvnw.cmd spring-boot:run
# GET http://localhost:8080/actuator/health -> {"status":"UP"}

cd ai-service                     # Python 3.13, environnement virtuel dédié (voir ai-service/README.md)
py -3.13 -m venv .venv && .venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -m app   # GET http://127.0.0.1:8000/health -> {"status":"UP",...}

cd frontend && npm install && npm start
# http://localhost:4200 (le proxy redirige /api vers le backend)
```

Frontend : [frontend/README.md](frontend/README.md) (structure, tests unitaires et e2e)
— Service IA : [ai-service/README.md](ai-service/README.md) (installation, configuration, tests).

## Architecture

```
Angular (4200) ──REST/JWT──> Spring Boot (8080) ──> MySQL (3306)
                                   │
                                   └──REST interne + X-Internal-Api-Key──> FastAPI (127.0.0.1:8000)
                                                                          ├─ CV Parser / NLP
                                                                          ├─ Matching
                                                                          ├─ Machine Learning
                                                                          └─ Chatbot
```

Détails : [docs/architecture.md](docs/architecture.md) — Modèle de données : [docs/database.md](docs/database.md)
— Sécurité et endpoints d'authentification : [docs/security.md](docs/security.md)
— API métier (profils, entreprises, offres, candidatures, CV) : [docs/api.md](docs/api.md)
— Module IA CV Parser (méthode, bibliothèques, évaluation, limites) : [docs/ai/cv-parser.md](docs/ai/cv-parser.md)
— Module IA Matching (formule, pondérations, exemples, limites, version) : [docs/ai/matching.md](docs/ai/matching.md)
— Chatbot RH (architecture, NLP, intentions, exemples, sécurité, limites) : [docs/ai/chatbot.md](docs/ai/chatbot.md)

## Arborescence

```
smartrecruit-ai/
├── backend/        API Spring Boot (Java 21, Maven Wrapper)
├── frontend/       Application Angular
├── ai-service/     Service IA FastAPI (Python)
│   └── ml/         Entraînement ML hors ligne (datasets et modèles non versionnés)
├── database/       Documentation MySQL et données de démonstration (migrations : Flyway dans backend/)
├── docs/           Architecture, workflow Git, répartition, documentation IA
├── scripts/        Scripts de test de bout en bout
├── .github/        Modèle de Pull Request
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

Aucun secret n'est versionné. Copier `.env.example` en `.env` et renseigner les valeurs localement.
