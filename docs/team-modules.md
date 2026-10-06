# Répartition des modules (6 membres)

| Membre | Module | Backend Spring Boot | Service IA | Frontend | Tests attendus |
|--------|--------|---------------------|------------|----------|----------------|
| **P1** | Authentification, utilisateurs, rôles, sécurité | `security/`, `auth/`, `user/`, `candidate/`, `recruiter/` (profils) | — | login, register, guards, intercepteur JWT, profil | inscription, connexion, accès refusé par rôle |
| **P2** | Entreprises, offres, candidatures | `company/`, `joboffer/`, `application/` | — | pages offres (public + recruteur), candidatures | CRUD offres, postuler, changement de statut |
| **P3** | CV Parser + NLP | `cv/`, `ai/` (client) | `services/cv_parser/`, `routes/cv.py` | page `/candidate/cv`, compétences extraites | extraction texte, compétences, sections |
| **P4** | Matching CV/offre + score | `matching/` | `services/matching/`, `routes/matching.py` | score, compétences manquantes, classement candidats | formule du score, cas limites, tri |
| **P5** | Chatbot RH + Machine Learning | `chatbot/` | `services/chatbot/`, `services/ml/`, `ml/` | widget chatbot | intentions, erreurs LLM, pipeline ML |
| **P6** | Frontend, dashboards, intégration | `dashboard/` | — | layout, navbar, sidebar, dashboards, graphiques | parcours principaux front |

## Interfaces entre modules (contrats)

Ces contrats permettent de travailler en parallèle. Toute modification doit être discutée en équipe.

- **P1 → tous** : l'utilisateur connecté et son rôle (`ADMIN`, `RECRUTEUR`, `CANDIDAT`) sont disponibles via Spring Security.
- **P2 ↔ P4** : `JobOffer` expose `requiredSkills`, `minExperienceYears`, `educationLevel` ; `Application` est liée à un `MatchingResult`.
- **P3 → P4** : le parser produit `skills[]`, `experience[]`, `education[]`, `languages[]` (JSON documenté dans `docs/ai/`).
- **P2 → P5** : le chatbot lit uniquement les offres publiées (titre, entreprise, compétences, lieu, type de contrat).
- **Backend → P6** : endpoints REST documentés (README + Swagger/OpenAPI).

## Fichiers partagés (à modifier avec précaution)

`backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration/`,
`frontend/package.json`, `frontend/src/app/app.routes.ts`, `ai-service/requirements.txt`, `README.md`.
