# Frontend — Angular

Interface web de SmartRecruit AI (Angular 21, TypeScript, Bootstrap 5 + Bootstrap Icons).

Communique uniquement avec le backend Spring Boot via `/api` (aucune donnée fictive : toutes les pages
appellent les endpoints réels, voir [docs/api.md](../docs/api.md) et [docs/security.md](../docs/security.md)).

Structure : voir `docs/architecture.md` (section 3.2).

## Démarrage

Prérequis : Node 20.19+, 22.12+ ou 24+ ; MySQL et le backend démarrés.

```bash
npm install
npm start            # http://localhost:4200
```

En développement, `proxy.conf.mjs` redirige `/api` vers le backend : variable `BACKEND_URL`, sinon
`SERVER_PORT` lu dans `../.env`, sinon `http://localhost:8080`. Aucune configuration CORS n'est donc nécessaire en local.

## Tests

```bash
npm test             # tests unitaires (Vitest) : services, intercepteur, guards, formulaires
npm run e2e          # parcours complet Angular -> Spring Boot -> MySQL (Playwright + Microsoft Edge)
```

Le test e2e nécessite MySQL et le backend démarrés (le serveur Angular est lancé automatiquement s'il ne tourne pas) ;
`cv-flow.e2e.ts` (import et analyse d'un CV PDF fictif) nécessite aussi le service IA FastAPI démarré, et vérifie
les données enregistrées directement dans MySQL (conteneur `smartrecruit-mysql`) ; `matching-flow.e2e.ts` fait de même
pour le score de compatibilité (CV → offre → score → MySQL → affichage candidat → classement recruteur), et
`chatbot-flow.e2e.ts` pour l'Assistant RH (connexion candidat → question → réponse basée sur les offres publiées de MySQL).
`turnover-flow.e2e.ts` vérifie la prédiction de risque de turnover (recruteur / admin → saisie → Spring Boot → FastAPI →
modèle ML réel → résultat comparé au rapport `ai-service/ml/reports/turnover_evaluation.json` ; candidat sans accès) :
il nécessite le modèle entraîné (`python -m ml.train`) et le service IA démarré.
`dashboard-flow.e2e.ts` crée des données par l'API (CV analysé, scores, candidatures, changement de statut) puis compare
les KPI et les valeurs des graphiques des trois dashboards aux requêtes SQL sur MySQL (isolation, accès, mobile ;
service IA démarré).
`demo-journey.e2e.ts` rejoue le scénario complet de la soutenance ([docs/demo-guide.md](../docs/demo-guide.md)) dans
l'ordre : recruteur (inscription, entreprise, offre en brouillon puis publiée) → candidat (profil, CV, analyse, score,
candidature, Assistant RH) → recruteur (classement, statut) → admin (dashboard, utilisateurs, entreprises, offres) →
turnover, chaque étape étant comparée à MySQL (service IA démarré et modèle entraîné).
Il crée des comptes `e2e6-*` à `e2e13-*@test.local` et des entreprises `E2E6 *` à `E2E13 *`, puis les supprime via l'API admin
(identifiants lus dans `../.env`). `E2E_KEEP_DATA=1` conserve les données pour les inspecter dans MySQL.
Captures d'écran : `e2e/screenshots/` (non versionné).

## Organisation du code (`src/app`)

| Dossier | Contenu |
|---|---|
| `core/models` | Interfaces TypeScript calquées sur les DTO du backend, libellés français des énumérations |
| `core/auth` | `AuthService` (session JWT en signals + localStorage), `authInterceptor`, `roleGuard` / `guestGuard` |
| `core/services` | Un service par domaine d'API : offres, profil candidat, CV, recruteur/entreprise, candidatures, compétences, admin, dashboards, notifications |
| `core/http-errors.ts` | Message lisible depuis l'`ApiError` du backend, report des `fieldErrors` sur les formulaires |
| `shared` | Composants réutilisables : erreurs de champ, chargement, badge de statut, pagination, saisie de compétences, carte d'offre, notifications, validateurs, carte KPI, graphique Chart.js (`app-chart`, avec tableau accessible ; `fake-chart.testing.ts` le remplace dans les tests unitaires) |
| `layout` | Barre de navigation, pied de page, mise en page des espaces (barre latérale) |
| `features/public` | Accueil, connexion, inscription, liste et détail des offres, 404 |
| `features/candidate` | Tableau de bord, profil (infos, compétences, formations, expériences), CV (import PDF, analyse IA, ajout au profil), offres, candidatures |
| `features/recruiter` | Tableau de bord, entreprise & profil, offres (liste, création, modification), candidats d'une offre, détail d'une candidature |
| `features/admin` | Tableau de bord (statistiques globales, graphiques, état du module ML), utilisateurs, entreprises, offres |
| `features/hr` | Risque de turnover (Machine Learning) : formulaire des 24 variables, score, niveau, facteurs et avertissement ; route `turnover` des espaces recruteur et admin |

## Sécurité

- Le jeton JWT est envoyé uniquement aux appels `/api` ; un 401 avec jeton (expiré, compte désactivé) efface la session.
- Les guards ne sont qu'un confort de navigation : le backend vérifie rôles et appartenance à chaque requête.
- Le mot de passe n'est jamais stocké ; seuls le jeton, son expiration et l'utilisateur sont conservés dans le localStorage.
- Angular échappe toutes les valeurs affichées (pas de `innerHTML`), ce qui protège des injections de script.
