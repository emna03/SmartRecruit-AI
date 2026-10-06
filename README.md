# SmartRecruit AI — Plateforme intelligente de recrutement

Mini-projet universitaire « IA for Software Engineering » (A.U. 2026-2027).

Application web de recrutement : les entreprises publient des offres, les candidats déposent leur CV,
et un module d'IA analyse les CV, calcule un score de compatibilité explicable avec les offres
et répond aux questions via un chatbot RH.

> **État du projet : ÉTAPE 1 (architecture) terminée.** Ce README sera complété à l'étape 14.

## Architecture

```
Angular (4200) ──REST/JWT──> Spring Boot (8080) ──> MySQL (3306)
                                   │
                                   └──REST interne──> FastAPI (8000)
                                                       ├─ CV Parser / NLP
                                                       ├─ Matching
                                                       ├─ Machine Learning
                                                       └─ Chatbot
```

Détails : [docs/architecture.md](docs/architecture.md)

## Arborescence

```
smartrecruit-ai/
├── backend/        API Spring Boot (Java 21, Maven Wrapper)
├── frontend/       Application Angular
├── ai-service/     Service IA FastAPI (Python)
│   └── ml/         Entraînement ML hors ligne (datasets et modèles non versionnés)
├── database/       Migrations et données de démonstration MySQL
├── docs/           Architecture, workflow Git, répartition, documentation IA
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
