# Workflow Git de l'équipe

La note individuelle peut être pondérée par les commits : chacun commite **son propre travail, sous son propre nom**.

## 1. Branches

```
main        ← version stable, démontrable (soutenance). Protégée : merge uniquement via Pull Request.
develop     ← intégration continue de l'équipe. Protégée : merge via Pull Request.
feature/*   ← une branche par fonctionnalité, créée depuis develop.
fix/*       ← correction de bug.
```

Convention de nommage : `feature/<module>-<description>`

| Membre | Exemples de branches |
|--------|----------------------|
| P1 | `feature/auth-jwt`, `feature/user-roles` |
| P2 | `feature/joboffer-crud`, `feature/application-status` |
| P3 | `feature/cv-parser-pdf`, `feature/cv-skill-extraction` |
| P4 | `feature/matching-score`, `feature/matching-ranking` |
| P5 | `feature/chatbot-faq`, `feature/ml-turnover` |
| P6 | `feature/front-dashboard`, `feature/front-integration` |

## 2. Cycle de travail

```bash
git checkout develop
git pull origin develop
git checkout -b feature/joboffer-crud

# ... travail + commits réguliers ...
git add .
git commit -m "feat(joboffer): add CRUD endpoints for job offers"

git pull --rebase origin develop      # se mettre à jour avant de pousser
git push -u origin feature/joboffer-crud
# puis ouvrir une Pull Request vers develop
```

## 3. Messages de commit (Conventional Commits)

```
<type>(<module>): <description courte>
```

Types : `feat`, `fix`, `test`, `docs`, `refactor`, `chore`, `style`.
Modules : `auth`, `user`, `company`, `joboffer`, `application`, `cv`, `matching`, `chatbot`, `ml`, `front`, `db`, `docs`.

Exemples :
- `feat(auth): add JWT login endpoint`
- `test(matching): add unit tests for score formula`
- `fix(front): show error when AI service is unavailable`

## 4. Pull Requests

- Une PR = une fonctionnalité, de taille raisonnable.
- Au moins **1 relecture** par un autre membre avant merge.
- Le build et les tests doivent passer localement (voir le modèle de PR).
- Ne jamais commiter : `.env`, mots de passe, clés API, secret JWT, CV réels, datasets personnels.

## 5. Éviter les conflits

- Chaque membre travaille principalement dans **son package / dossier** (voir `docs/team-modules.md`).
- Fichiers partagés (`pom.xml`, `package.json`, `app.routes.ts`, `requirements.txt`, scripts SQL) :
  modifications courtes, annoncées à l'équipe, mergées rapidement.
- Migrations SQL : un nouveau fichier numéroté par changement, ne jamais modifier une migration déjà mergée.
