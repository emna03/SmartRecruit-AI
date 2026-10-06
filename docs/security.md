# Sécurité — authentification JWT et rôles

## Fonctionnement

1. `POST /api/auth/register` ou `POST /api/auth/login` renvoie un **jeton JWT** signé (HMAC-SHA256, secret `JWT_SECRET`).
2. Le frontend envoie ce jeton à chaque requête : `Authorization: Bearer <jeton>`.
3. `JwtAuthenticationFilter` vérifie la signature, l'émetteur et l'expiration, puis **relit l'utilisateur en base** :
   un compte désactivé ou supprimé perd immédiatement l'accès, même avec un jeton encore valide.
4. Les règles d'accès par rôle sont appliquées dans `SecurityConfig`.

Contenu du jeton : `sub` (id utilisateur), `email`, `role`, `iss` = `smartrecruit-ai`, `iat`, `exp` (24 h par défaut).

## Règles d'accès

| Chemin | Accès |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | public |
| `GET /api/jobs/**` | public (consultation des offres) |
| `/actuator/health` | public |
| `/api/admin/**` | `ADMIN` |
| `/api/recruiter/**` | `RECRUTEUR` |
| `/api/candidate/**` | `CANDIDAT` |
| tout le reste | utilisateur connecté |

Convention pour les modules suivants : placer les endpoints sous le préfixe du rôle concerné
(ex. `/api/recruiter/jobs`, `/api/candidate/applications`) ; la règle de rôle s'applique alors automatiquement.
Pour une règle plus fine (ex. un recruteur ne modifie que ses propres offres), vérifier l'appartenance dans le service :
une ressource d'un autre utilisateur renvoie **404** (et non 403) pour ne pas révéler son existence.
Endpoints métier : [api.md](api.md).

## Endpoints d'authentification

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| POST | `/api/auth/register` | `{email, password, firstName, lastName, role}` (`CANDIDAT` ou `RECRUTEUR`) | 201 `{accessToken, tokenType, expiresInMs, user}` |
| POST | `/api/auth/login` | `{email, password}` | 200 `{accessToken, tokenType, expiresInMs, user}` |
| GET | `/api/auth/me` | — | 200 `user` |

## Administration des utilisateurs (ADMIN)

| Méthode | Chemin | Description |
|---|---|---|
| GET | `/api/admin/users?role=CANDIDAT` | liste (filtre optionnel par rôle) |
| GET | `/api/admin/users/{id}` | détail |
| PATCH | `/api/admin/users/{id}/status` | `{enabled: true/false}` active / désactive un compte |
| DELETE | `/api/admin/users/{id}` | supprime le compte et ses données |

Un administrateur ne peut ni se désactiver ni se supprimer lui-même.

## Codes d'erreur

Toutes les erreurs ont le même format JSON :
`{timestamp, status, error, message, path, fieldErrors?}`.

| Code | Cas |
|---|---|
| 400 | données invalides (détail par champ dans `fieldErrors`), inscription en `ADMIN`, règle métier non respectée |
| 401 | jeton absent, invalide ou expiré ; email ou mot de passe incorrect (même message dans les deux cas) |
| 403 | rôle insuffisant ; compte désactivé |
| 404 | ressource introuvable ou appartenant à un autre utilisateur |
| 409 | email déjà utilisé, nom d'entreprise déjà pris, candidature déjà envoyée |

## Mesures de protection

- Mots de passe hachés avec **BCrypt** ; le hash n'est jamais renvoyé par l'API.
- Mot de passe : 8 à 72 caractères, au moins une lettre et un chiffre.
- Emails normalisés en minuscules (pas de doublon `Alice@x.com` / `alice@x.com`).
- Le rôle `ADMIN` ne peut pas être obtenu par inscription. Le premier admin est créé au démarrage
  depuis `ADMIN_EMAIL` / `ADMIN_PASSWORD` (`.env`), uniquement s'il n'existe aucun admin.
- L'application **refuse de démarrer** si `JWT_SECRET` est absent ou fait moins de 32 caractères.
- CORS limité aux origines de `CORS_ALLOWED_ORIGINS` (Angular : `http://localhost:4200`).
- API stateless : pas de session ni de cookie, donc pas de CSRF.
- Les traces d'erreur ne sont jamais renvoyées au client.

## Tests

- `JwtServiceTest` : jeton valide, expiré, falsifié, signé avec un autre secret, chaîne invalide.
- `AuthApiTest` : inscription, connexion, rôles, désactivation, suppression, CORS (15 scénarios).
- `scripts/e2e-auth.ps1` : scénario complet sur le backend lancé avec MySQL
  (`powershell -ExecutionPolicy Bypass -File scripts/e2e-auth.ps1 -BaseUrl http://localhost:8080`).
