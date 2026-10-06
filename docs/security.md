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
| `/api/hr/**` | `ADMIN` ou `RECRUTEUR` (outils RH : risque de turnover) |
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
| 413 | fichier trop volumineux (CV > `MAX_CV_SIZE_MB`) |
| 415 | type de contenu non accepté |
| 422 | données refusées par le service IA (message du service IA, ex. PDF sans texte exploitable) |
| 503 | service IA arrêté, trop lent (`AI_TIMEOUT_SECONDS`), clé interne refusée, erreur ou réponse invalide du service IA |

## Communication interne Spring Boot → FastAPI

- Le navigateur n'appelle jamais FastAPI : seul Spring Boot le fait, après avoir appliqué JWT et rôles.
- Chaque appel porte la clé partagée `AI_INTERNAL_API_KEY` (en-tête `X-Internal-Api-Key`) ; FastAPI refuse toute
  route `/api/**` sans clé valide (**401**, comparaison en temps constant). `/health` reste public et ne révèle rien.
- FastAPI écoute sur `127.0.0.1` par défaut (pas d'exposition réseau) et refuse de démarrer sans clé,
  avec une clé de moins de 16 caractères ou avec la valeur d'exemple. Côté Spring Boot, une clé vide désactive
  les fonctionnalités IA (503) sans empêcher le reste de l'application de fonctionner.
- Délais : connexion 5 s maximum, réponse `AI_TIMEOUT_SECONDS` (20 s par défaut). Une panne du service IA donne
  un **503** clair et ne bloque pas le reste de l'API.
- Toute réponse du service IA est **validée** (Bean Validation sur le DTO) avant d'être utilisée ou affichée ;
  une réponse incomplète ou incohérente est rejetée (503).
- Les journaux des deux côtés contiennent la méthode, le chemin, le statut et un identifiant de corrélation
  (`X-Request-Id`), jamais la clé ni le contenu échangé (données personnelles des CV).
- Vérification : `GET /api/admin/ai/status` (ADMIN) ; tests `AiServiceClientTest`, `AiAdminApiTest`,
  `ai-service/tests/` et `scripts/e2e-step7.ps1`.

## CV et données personnelles

- Upload réservé au rôle `CANDIDAT`, sur son propre CV uniquement (pas d'identifiant de CV dans l'URL).
- **Aucune confiance dans le navigateur** : le nom et le type MIME envoyés sont ignorés ; le fichier doit être
  non vide, ≤ `MAX_CV_SIZE_MB` et commencer par la signature `%PDF-`. Il est stocké sous un nom aléatoire
  (32 caractères hexadécimaux + `.pdf`) dans `UPLOAD_DIR/cvs` (ignoré par Git), hors de toute URL publique ; le nom d'origine,
  nettoyé (chemins et caractères spéciaux retirés), sert seulement à l'affichage.
- Le CV n'est transmis qu'au service IA interne (clé `X-Internal-Api-Key`), jamais à un LLM ni à un tiers.
- Le résultat du parser est validé par Pydantic puis par Bean Validation avant tout enregistrement ;
  le texte conservé en base est masqué (emails, liens, téléphones) et n'est jamais renvoyé au navigateur.
- Journaux : identifiant utilisateur, tailles, compteurs et codes d'erreur uniquement — jamais le contenu,
  le nom du fichier ni une donnée personnelle du CV.
- Suppression du CV ou du compte (admin) : la ligne et le fichier disque sont supprimés.

## Score de compatibilité (matching)

- Le service IA ne reçoit **aucune donnée d'identité** : noms de compétences extraits du CV, durée d'expérience en mois
  et niveau d'études, plus les critères de l'offre. Tout champ non prévu (ex. `email`) est refusé par FastAPI (400).
- Le résultat est validé (Pydantic, puis Bean Validation dans Spring Boot : scores 0–100, listes bornées et cohérentes)
  avant enregistrement et affichage ; une réponse invalide n'est jamais enregistrée.
- Le candidat ne voit que **son** score, pour une offre publiée ou à laquelle il a postulé. Le recruteur ne voit que les
  scores des candidats ayant postulé aux offres de **son** entreprise (autre entreprise → 404).
- Le score est présenté comme une aide à la décision : aucun statut de candidature n'est modifié automatiquement.
- Journaux : identifiants, score et compteurs uniquement (jamais de nom, d'email ni de compétence).
- Les scores sont supprimés avec le CV, avec le compte ou l'offre (cascade MySQL), et quand les critères de l'offre changent.

## Chatbot RH

- `POST /api/candidate/chatbot/messages` réservé au rôle `CANDIDAT` (anonyme → 401, recruteur / admin → 403).
- Le service IA reçoit la question et des **données publiques** des offres publiées (titre, entreprise, ville,
  contrat, expérience, niveau, salaire, compétences) : aucune donnée sur le candidat (identité, CV, candidatures).
  Aucun LLM ni service externe n'est appelé.
- La réponse est validée (Pydantic puis Bean Validation, listes fermées d'intentions et d'actions) ; si elle cite une
  offre absente du catalogue envoyé, elle est rejetée (503). Les cartes d'offres affichées sont relues dans MySQL.
- Les questions ne sont ni enregistrées (ni en base, ni dans le navigateur) ni journalisées : les journaux ne
  contiennent que l'intention et des compteurs.
- Détails : [ai/chatbot.md](ai/chatbot.md#8-sécurité-et-données-personnelles).

## Risque de turnover (Machine Learning)

- `POST /api/hr/turnover/predictions` réservé aux rôles `ADMIN` et `RECRUTEUR` (anonyme → 401, candidat → 403).
- Minimisation : 24 variables professionnelles seulement, sans identité ; âge, genre et situation familiale ne sont
  ni demandés ni utilisés par le modèle. Spring Boot transmet uniquement ces champs (un champ inconnu envoyé par le
  navigateur est ignoré) et FastAPI refuse tout champ supplémentaire (400).
- Validation stricte des deux côtés (types, bornes du dataset, cohérence) ; la réponse du modèle est validée par
  Bean Validation avant affichage (niveau cohérent avec le score, facteurs connus, au plus 5) — sinon 503.
- Rien n'est enregistré ; journaux : niveau, score et modèle uniquement, jamais les valeurs saisies.
- Le modèle est un fichier local non versionné : FastAPI refuse de le charger si son empreinte SHA-256 ou la version
  de scikit-learn ne correspond pas à ses métadonnées (pas de prédiction de secours).
- La prédiction est une estimation de démonstration, affichée avec un avertissement, jamais une décision RH.
  Détails : [ai/turnover-ml.md](ai/turnover-ml.md#13-aspects-éthiques).

## Dashboards et statistiques

- `GET /api/candidate/dashboard`, `/api/recruiter/dashboard`, `/api/admin/dashboard` : protégés par les règles de
  préfixe ci-dessus (anonyme → 401, autre rôle → 403, y compris l'administrateur sur les dashboards candidat et
  recruteur).
- Aucun identifiant dans l'URL ni en paramètre : le candidat et l'entreprise sont déduits du jeton. Un recruteur ne
  peut donc pas lire les statistiques d'une autre entreprise (vérifié par les tests avec deux recruteurs).
- Le recruteur ne voit que les scores des candidats ayant postulé à ses offres ; le nom du candidat n'apparaît que
  dans « meilleurs candidats », comme dans la liste des candidatures déjà accessible. Aucun email, CV ni texte extrait
  n'est renvoyé.
- Les prédictions de turnover n'étant pas enregistrées, aucune statistique n'en est tirée. Détails :
  [dashboards.md](dashboards.md).

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

## Audit de sécurité final (étape 13)

`scripts/e2e-step13.ps1` rejoue en une fois les contrôles transverses sur l'application réellement démarrée
(backend et MySQL démarrés, service IA arrêté : le script le lance puis l'arrête lui-même) :

| Domaine | Vérifications |
|---|---|
| JWT | en-tête HS*, aucun mot de passe ni nom dans la charge utile ; sans jeton, jeton mal formé, signé avec une autre clé, `alg: none`, charge utile modifiée (rôle ADMIN) → 401 ; jeton d'un compte supprimé → 401 |
| Rôles | inscription en ADMIN → 400 ; candidat sur les API recruteur / admin / RH, recruteur sur les API candidat / admin, admin sur les API candidat / recruteur → 403 |
| Ownership | un recruteur sur l'offre, les candidatures, le changement de statut ou le recalcul des scores d'une autre entreprise → 404 (MySQL inchangé) ; candidature d'un tiers → 404 ; brouillon invisible (404, absent de la recherche) |
| Entrées | champs invalides → 400 avec `fieldErrors` ; email en double (casse différente), candidature en double → 409 ; salaire incohérent, énumération inconnue, identifiant non numérique, question vide ou > 500 caractères → 400 ; aucune trace technique dans les erreurs |
| Upload PDF | nom `..\..\..\evil.pdf` → stockage sous un nom aléatoire dans `uploads/cvs` ; recruteur → 403 ; vraie image PNG ou texte renommé `.pdf` → 400 ; type de contenu non pris en charge → 415 ; 6 Mo → 413 ; PDF endommagé → 422 |
| Clé interne | `/health` public sans détail sensible ; `/api/status` sans clé ou avec une mauvaise clé → 401 ; FastAPI n'écoute que sur 127.0.0.1 ; MySQL publié sur 127.0.0.1 uniquement |
| CORS | origine Angular autorisée ; origine inconnue refusée sans en-tête `Access-Control-Allow-Origin` |
| Panne IA | chatbot, recalcul des scores, statut IA → 503 lisible ; offres, candidatures et dashboards restent disponibles |
| Journaux | ni email (aucune adresse, quel que soit le compte), ni contenu du CV, ni lettre de motivation, ni mot de passe, ni jeton, ni secret |
| Git | aucune valeur secrète de `.env` dans tout l'historique (`git log -p --all`) ; `.env` ignoré et jamais commité ; uploads, modèle et dataset ignorés ; aucune clé privée ni jeton JWT commité |

Les comptes, entreprises et fichiers créés sont supprimés à la fin (vérifié dans MySQL et sur le disque).
