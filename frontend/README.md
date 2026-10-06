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
pour le score de compatibilité (CV → offre → score → MySQL → affichage candidat → classement recruteur).
Il crée des comptes `e2e6-*` / `e2e8-*` / `e2e9-*@test.local` et des entreprises `E2E6 *` / `E2E9 *`, puis les supprime via l'API admin
(identifiants lus dans `../.env`). `E2E_KEEP_DATA=1` conserve les données pour les inspecter dans MySQL.
Captures d'écran : `e2e/screenshots/` (non versionné).

## Organisation du code (`src/app`)

| Dossier | Contenu |
|---|---|
| `core/models` | Interfaces TypeScript calquées sur les DTO du backend, libellés français des énumérations |
| `core/auth` | `AuthService` (session JWT en signals + localStorage), `authInterceptor`, `roleGuard` / `guestGuard` |
| `core/services` | Un service par domaine d'API : offres, profil candidat, CV, recruteur/entreprise, candidatures, compétences, admin, notifications |
| `core/http-errors.ts` | Message lisible depuis l'`ApiError` du backend, report des `fieldErrors` sur les formulaires |
| `shared` | Composants réutilisables : erreurs de champ, chargement, badge de statut, pagination, saisie de compétences, carte d'offre, notifications, validateurs |
| `layout` | Barre de navigation, pied de page, mise en page des espaces (barre latérale) |
| `features/public` | Accueil, connexion, inscription, liste et détail des offres, 404 |
| `features/candidate` | Tableau de bord, profil (infos, compétences, formations, expériences), CV (import PDF, analyse IA, ajout au profil), offres, candidatures |
| `features/recruiter` | Tableau de bord, entreprise & profil, offres (liste, création, modification), candidats d'une offre, détail d'une candidature |
| `features/admin` | Espace minimal : statistiques, utilisateurs, entreprises, offres |

## Sécurité

- Le jeton JWT est envoyé uniquement aux appels `/api` ; un 401 avec jeton (expiré, compte désactivé) efface la session.
- Les guards ne sont qu'un confort de navigation : le backend vérifie rôles et appartenance à chaque requête.
- Le mot de passe n'est jamais stocké ; seuls le jeton, son expiration et l'utilisateur sont conservés dans le localStorage.
- Angular échappe toutes les valeurs affichées (pas de `innerHTML`), ce qui protège des injections de script.
