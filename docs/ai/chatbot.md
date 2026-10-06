# Module IA — Chatbot RH (étape 10)

L'assistant RH permet au candidat de poser des questions en langage naturel sur les **offres réellement publiées**
et sur le **fonctionnement de la plateforme** (CV, candidature, score de compatibilité).

Principes :

- **aucune invention** : une offre, une entreprise, une compétence ou une exigence citée vient toujours des
  données de MySQL, transmises par Spring Boot ;
- **NLP local et déterministe** : analyse de la question, extraction d'entités et classification d'intention
  sans LLM ni service externe (même question + mêmes offres = même réponse) ;
- **« je ne sais pas »** quand la question sort du domaine, au lieu d'une réponse inventée ;
- **aucune donnée personnelle** envoyée au service IA ni écrite dans les journaux.

Version : `CHATBOT_VERSION = "1.0.0"` (moteur) — base de connaissances `chatbot_knowledge.json` version `1.0`.

## 1. Architecture

```
Angular (Assistant RH)                     Spring Boot                              FastAPI (127.0.0.1)
POST /api/candidate/chatbot/messages  ──>  ChatbotController (rôle CANDIDAT)
{message, jobOfferId?}                     ChatbotService
                                             1. lit dans MySQL les 200 offres publiées
                                                les plus récentes (+ l'offre de contexte
                                                si elle est publiée)
                                             2. POST /api/chatbot/ask  ───────────────>  route chatbot (clé X-Internal-Api-Key)
                                                {message, contextOfferId, offers[]}       nlp.analyze  -> entités
                                                                                          IntentClassifier -> intention
                                                                                          engine -> réponse + ids d'offres
                                             3. valide la réponse (Bean Validation)  <──  {intent, answer, offerIds, ...}
                                             4. vérifie que chaque offre citée fait
                                                partie du catalogue envoyé (sinon 503)
                                             5. reconstruit les cartes d'offres
                                                depuis MySQL
Affichage : bulles, cartes d'offres  <───  ChatbotReply
(lien « Voir l'offre »), actions
```

- **FastAPI n'accède pas à MySQL** : Spring Boot possède les données métier et ne transmet que le contexte utile.
- Le catalogue envoyé ne contient que des **données publiques** d'offres publiées : identifiant, titre, entreprise,
  ville, contrat, expérience minimale, niveau d'études, salaire, compétences requises. Les brouillons, les offres
  fermées et la description ne sont jamais envoyés.
- **Rien sur le candidat** n'est transmis : ni identité, ni CV, ni candidatures. La question est le seul texte libre.
- Communication interne protégée par la clé partagée `X-Internal-Api-Key` (comme le CV Parser et le matching).
- La conversation n'est **pas enregistrée** : ni en base, ni dans le navigateur (mémoire de la page uniquement,
  vidée au rechargement ou au changement d'utilisateur). Aucune migration n'a donc été nécessaire.

## 2. Déroulement d'une question

1. **Normalisation** : espaces réduits, minuscules sans accents (`fold`, qui conserve la longueur du texte pour
   garder la position de chaque entité).
2. **Extraction d'entités** (`services/chatbot/nlp.py`), chacune avec sa position :

   | Entité | Méthode | Exemples reconnus |
   |---|---|---|
   | Compétences / technologies | lexique du CV Parser (`skills.json`, alias compris) + compétences des offres reçues absentes du lexique | « java », « springboot » → Spring Boot, « k8s » → Kubernetes |
   | Titre d'offre cité | titre exact d'une offre reçue, ou approché à 90 % (titres de 10 caractères et plus) | « l'offre Data Scientist », « l'offre Developeur Front-end Angular » (sans accent) |
   | Titre inexistant | texte entre guillemets ou après « l'offre / le poste / l'annonce » | « l'offre « Astronaute lunaire » » |
   | Expérience | nombre d'années en chiffres ou en lettres ; « junior », « débutant », « sans expérience » = 0 | « 3 ans », « deux années » |
   | Niveau d'études | motifs du plus élevé au plus bas | « master », « bac+5 », « licence », « BTS » |
   | Type de contrat | motifs | « CDI », « stage », « PFE », « alternance », « freelance » |
   | Ville | uniquement une ville présente dans au moins une offre reçue | « Tunis », « Sousse » |
   | Mots-clés | mots restants hors mots outils et vocabulaire de recherche | « data scientist », « pilote avion » |

   Une lettre suivie d'une apostrophe (« c'est », « d'abord ») n'est pas le langage C ; une compétence comprise
   dans un titre cité n'est pas comptée comme critère séparé.
3. **Masquage** : chaque entité est remplacée par un marqueur (`xskill`, `xnombre`, `xdiplome`, `xcontrat`,
   `xville`, `xtitre`). « Quelles offres demandent Java ? » et « Quelles offres demandent Python ? » deviennent
   la même phrase : l'intention ne dépend pas de la technologie citée.
4. **Classification d'intention** (`services/chatbot/intents.py`), voir § 3.
5. **Réponse** (`services/chatbot/engine.py`) : un modèle de phrase propre à l'intention, rempli **uniquement**
   avec les offres reçues (recherche, détail d'une offre) ou avec la base de connaissances (fonctionnement de la
   plateforme). Les offres sont renvoyées par **identifiant** ; Spring Boot affiche ses propres données.

## 3. Traitement NLP : classification d'intention

Chaque intention est décrite par des **phrases d'exemple** (190 au total, dans
`resources/chatbot_knowledge.json`). Il n'y a pas de modèle entraîné à stocker : le « modèle » est recalculé au
démarrage à partir de ces exemples.

- **Représentation d'une phrase** : ses mots (sans mots outils, pluriel simple retiré) et les **n-grammes de 3 à 5
  caractères** de chaque mot. « candidater », « candidature » et « candidat » partagent ainsi des traits, et une
  faute de frappe ne fait perdre que quelques n-grammes.
- **Pondération TF-IDF** : un trait présent dans peu d'exemples compte plus qu'un trait fréquent.
  \( w(t) = (1 + \ln \mathrm{tf}) \times \mathrm{idf}(t) \), avec \( \mathrm{idf}(t) = \ln\frac{1 + N}{1 + \mathrm{df}(t)} + 1 \).
- **Similarité cosinus** entre la question et chaque exemple ; score d'une intention = meilleure similarité avec un
  de ses exemples (entre 0 et 1, renvoyé comme `confidence`).
- **Question non reconnue (`UNKNOWN`)** si :
  - le meilleur score est inférieur au seuil `THRESHOLD = 0.30`, ou
  - moins de la moitié des mots de la question (`MIN_COVERAGE = 0.5`) sont connus du vocabulaire des exemples
    (contrôle hors domaine : « Combien coûte un abonnement ? »). Un mot proche à 88 % d'un mot connu (faute de
    frappe) compte comme connu.
- **Règles de priorité** :
  - une question sans intention claire (ou une simple salutation) mais citant une compétence, un contrat ou une
    ville est traitée comme une **recherche d'offres** (« Angular à Tunis ? ») ;
  - une question non reconnue citant le titre exact d'une offre donne le **détail de cette offre**.
- À égalité de score, l'ordre alphabétique départage : le résultat est toujours le même.

Pourquoi cette approche plutôt qu'un modèle entraîné ou un LLM : le domaine est fermé (offres + fonctionnement
de la plateforme), les intentions sont peu nombreuses, et l'approche est **explicable** (on peut montrer
l'exemple le plus proche), **testable** de façon déterministe et sans coût ni dépendance externe. Ajouter une
formulation se fait en ajoutant un exemple au fichier JSON.

## 4. Intentions reconnues

| Intention | Exemple de question | Réponse construite à partir de |
|---|---|---|
| `SEARCH_OFFERS` | « Quelles offres correspondent à Java ? », « Je cherche un poste Angular. », « Je cherche un stage à Tunis » | offres publiées filtrées (§ 5) |
| `OFFER_SKILLS` | « Quelles sont les compétences demandées pour cette offre ? », « Cette offre demande-t-elle Docker ? » | compétences de l'offre ciblée |
| `OFFER_EXPERIENCE` | « Combien d'années d'expérience sont nécessaires ? » | expérience minimale de l'offre (comparée aux années citées) |
| `OFFER_EDUCATION` | « Quel niveau d'étude est demandé ? » | niveau de l'offre (comparé au niveau cité) |
| `OFFER_SALARY` | « Quel est le salaire ? » | salaire de l'offre, ou « non précisé » |
| `OFFER_DETAILS` | « Parle-moi de cette offre », « Où se trouve ce poste ? » | contrat, ville, expérience, niveau, compétences, salaire |
| `FAQ_APPLY` | « Comment postuler à une offre ? » | base de connaissances + liens « Voir les offres », « Mes candidatures » |
| `FAQ_CV` | « Comment importer mon CV ? » | base de connaissances + lien « Mon CV » |
| `FAQ_MATCHING` | « Comment fonctionne le score de compatibilité ? » | base de connaissances (formule 60/25/15 de l'étape 9) |
| `FAQ_APPLICATIONS` | « Où voir mes candidatures ? » | base de connaissances (sans accès aux candidatures) |
| `FAQ_PROFILE` | « Comment modifier mon profil ? » | base de connaissances + liens |
| `FAQ_PRIVACY` | « Mes questions sont-elles enregistrées ? » | base de connaissances |
| `FAQ_RECOMMENDATION` | « Quelles offres me correspondent ? » | renvoie vers le score de compatibilité de chaque offre (le chatbot ne lit pas le CV) |
| `GREETING`, `THANKS`, `HELP` | « Bonjour », « Merci », « Que peux-tu faire ? » | base de connaissances |
| `UNKNOWN` | « Quelle est la recette de la pâte à crêpes ? » | « Je ne sais pas répondre à cette question… » + suggestions |

**Offre ciblée** par une question `OFFER_*`, dans cet ordre :

1. un titre d'offre publiée cité dans la question ; plusieurs offres de même titre → le chatbot les liste et
   demande de choisir ;
2. un titre cité qui ne correspond à aucune offre publiée → « Je ne trouve aucune offre publiée intitulée … » ;
3. l'offre de contexte : celle ouverte depuis le bouton « Poser une question à l'assistant » de la page de l'offre,
   choisie dans une réponse, ou unique résultat de la recherche précédente ;
4. sinon : « De quelle offre parlez-vous ? ».

## 5. Recherche d'offres

Critères facultatifs, combinés par « et » (`services/chatbot/offers.py`) :

- **compétences** : l'offre doit demander toutes les compétences citées (« springboot » = « Spring Boot »). Si aucune
  offre ne les demande toutes, les offres demandant au moins l'une d'elles sont proposées **en le disant** ;
- **contrat, ville** ;
- **expérience** : offres accessibles avec les années indiquées (expérience minimale ≤ années) ;
- **niveau d'études** : offres sans exigence ou exigeant un niveau inférieur ou égal ;
- **mots-clés** (sans compétence citée) : chaque mot doit apparaître dans le titre, l'entreprise, la ville ou les
  compétences. Un mot absent de toutes les offres → aucune offre, avec le terme cité dans la réponse.

Ordre : nombre de compétences trouvées, puis offres les plus récentes. Au plus **5 offres** sont affichées, avec le
nombre total et un lien vers la liste complète. Aucune offre publiée → le chatbot le dit.

## 6. Exemples (moteur réel, catalogue fictif des tests)

Catalogue : « Développeur Java Spring Boot » (Acme, CDI, Tunis, 2 ans, Bac+5), « Développeur Front-end Angular »
(WebCo, CDD, Sousse), « Data Scientist » (DataLab, stage, Tunis), « Ingénieur Full Stack Java Angular » (Acme, CDI).

| Question | Intention (confiance) | Réponse (début) |
|---|---|---|
| Quelles offres correspondent à Java ? | SEARCH_OFFERS (1.0) | « J'ai trouvé 2 offres publiées qui demandent Java. » + 2 cartes |
| Je cherche un stage à Tunis | SEARCH_OFFERS (0.84) | « J'ai trouvé 1 offre publiée en stage, à Tunis. » |
| J'ai 2 ans d'expérience et un master, quelles offres en Java ? | SEARCH_OFFERS (0.73) | « J'ai trouvé 1 offre publiée qui demande Java, accessible avec 2 ans d'expérience, accessible avec un niveau Bac+5 … » |
| Quelles offres demandent Java et Python ? | SEARCH_OFFERS (0.97) | « Aucune offre publiée ne demande à la fois Java et Python. Voici 3 offres qui demandent au moins l'une de ces compétences. » |
| Je cherche un poste de pilote d'avion | SEARCH_OFFERS (0.45) | « Je n'ai trouvé aucune offre publiée correspondant à « pilote avion ». » |
| Quelles sont les compétences demandées pour cette offre ? (contexte : offre Java) | OFFER_SKILLS (0.59) | « L'offre « Développeur Java Spring Boot » (Acme) demande 4 compétences : Java, Spring Boot, MySQL et Docker. » |
| Combien d'années d'expérience sont nécessaires ? J'ai 1 an (même contexte) | OFFER_EXPERIENCE (0.54) | « … demande au moins 2 ans d'expérience. Avec 1 an d'expérience, vous êtes en dessous du minimum demandé : vous pouvez tout de même postuler, c'est le recruteur qui décide. » |
| Quel niveau d'étude est demandé ? J'ai une licence (même contexte) | OFFER_EDUCATION (0.83) | « … demande un niveau Bac+5 (Master / Ingénieur). Un niveau Bac+3 (Licence) est inférieur au niveau demandé … » |
| Quelles compétences pour l'offre « Astronaute lunaire » ? | OFFER_SKILLS (0.42) | « Je ne trouve aucune offre publiée intitulée « Astronaute lunaire ». » (aucune offre citée) |
| Quel est le salaire ? (sans contexte) | OFFER_SALARY (1.0) | « De quelle offre parlez-vous ? … » |
| Comment postuler à une offre ? | FAQ_APPLY (1.0) | procédure + liens « Voir les offres », « Mes candidatures » |
| Comment fonctionne le score de compatibilité ? | FAQ_MATCHING (0.92) | formule 60 / 25 / 15, « jamais une décision automatique » |
| Quelle est la recette de la pâte à crêpes ? | UNKNOWN (0.21) | « Je ne sais pas répondre à cette question. … » |
| Combien coûte un abonnement ? | UNKNOWN (0.35, hors vocabulaire) | idem |

Sous chaque réponse, l'interface affiche ce que l'assistant a compris (« Compris : Java · Stage · Tunis »).

## 7. Interface Angular

Page **Assistant RH** (`/candidate/assistant`, menu de l'espace candidat), également ouverte depuis le bouton
« Poser une question à l'assistant » d'une offre (`/candidate/assistant?offre=ID`) :

- zone de saisie (500 caractères, compteur), envoi par le bouton ou Entrée (Maj+Entrée = retour à la ligne) ;
  le bouton est désactivé pour une question vide ;
- bulles distinctes candidat (à droite, en couleur) / assistant (à gauche, avec avatar) ;
- indicateur « L'assistant prépare sa réponse… » pendant l'appel ;
- cartes d'offres citées avec **« Voir l'offre »** (page de l'offre, avec le score de compatibilité) et « Poser une
  question sur cette offre » ; liens d'action (Mon CV, Mes candidatures…) ;
- offre en cours de discussion affichée au-dessus de la saisie (et retirable), suggestions de questions ;
- erreur (service IA indisponible, réseau) dans une bulle rouge avec **« Réessayer »** ;
- rappel permanent : réponses basées uniquement sur les offres publiées, aucune décision de recrutement ;
- mise en page adaptée au mobile.

## 8. Sécurité et données personnelles

| Mesure | Détail |
|---|---|
| Accès | `POST /api/candidate/chatbot/messages` réservé au rôle `CANDIDAT` : anonyme ou jeton invalide → 401, recruteur ou admin → 403 |
| Validation de la question | Spring Boot (`@NotBlank`, 500 caractères) puis FastAPI (Pydantic) : vide, blanche ou trop longue → 400 sans appel au service IA |
| Données envoyées au service IA | question + offres publiées (données publiques). Aucun identifiant, nom, email, CV ou candidature du candidat. Tout champ non prévu est refusé par FastAPI (400) |
| Réponse du service IA | validée par Pydantic puis Bean Validation (intention et actions dans une liste fermée, tailles bornées) ; une offre citée absente du catalogue envoyé → réponse rejetée (503) ; cartes reconstruites depuis MySQL |
| Communication interne | clé `X-Internal-Api-Key`, service IA écoutant sur 127.0.0.1 |
| Journaux | intention, confiance, nombre d'offres reçues et citées, version — **jamais le texte de la question** |
| Conservation | aucune : questions et réponses ne sont ni stockées en base ni dans le navigateur |
| Panne du service IA | 503 « momentanément indisponible » ; le reste de la plateforme (offres, candidatures) continue de fonctionner |

### Pourquoi pas de LLM externe

Un LLM (API externe) n'a **pas** été intégré. Il n'est pas nécessaire pour cette étape : les questions attendues
portent sur un domaine fermé et la réponse doit reprendre exactement les données des offres. Les inconvénients
seraient :

- **risque d'hallucination** : un LLM peut citer une offre, une entreprise ou une exigence inexistante, ce qui est
  précisément interdit ;
- **données envoyées à un tiers** : la question (qui peut contenir des informations personnelles) et le catalogue
  des offres partiraient vers un fournisseur externe ; il faudrait l'information des utilisateurs, un contrat de
  traitement de données et une politique de conservation ;
- **coût et dépendance** : facturation à l'usage (par jeton), clé API à protéger, latence de plusieurs secondes,
  panne ou changement de modèle hors de notre contrôle ;
- **tests non reproductibles** : la même question peut donner des réponses différentes.

Les variables `LLM_*` de `.env.example` (prévues à l'étape 1) ne sont lues par aucun code. Une évolution possible
serait d'utiliser un LLM uniquement pour **reformuler** une réponse déjà construite à partir des données, sans lui
transmettre la question brute, après accord de l'équipe et avec les garde-fous ci-dessus.

## 9. Limites (à présenter en soutenance)

- **Vocabulaire limité aux exemples** : une formulation très éloignée des 190 exemples peut être classée `UNKNOWN`
  (le chatbot le dit, il ne répond pas au hasard). Remède : ajouter des exemples dans le JSON.
- **Français principalement** : quelques mots anglais sont reconnus (« internship », « years »), mais les exemples
  et réponses sont en français.
- **Compétences** : seules celles du lexique du CV Parser et celles des offres publiées sont reconnues ; une
  technologie absente des deux est traitée comme un mot-clé.
- **Pas de mémoire longue** : seule l'offre en cours de discussion est retenue ; « et celle d'avant ? » n'est pas
  compris. La conversation disparaît au rechargement de la page.
- **Catalogue limité aux 200 offres publiées les plus récentes** (suffisant pour le projet ; au-delà, il faudrait
  une recherche côté Spring Boot avant l'appel au service IA).
- **Recherche par la description non prise en compte** (seuls titre, entreprise, ville et compétences le sont).
- **Pas d'accès au profil** : le chatbot ne recommande pas d'offre personnalisée ; il renvoie vers le score de
  compatibilité (étape 9), calculé sur la page de chaque offre.
- Le chatbot n'est pas proposé aux recruteurs dans cette version.

## 10. Tests

| Niveau | Fichier | Ce qui est vérifié |
|---|---|---|
| FastAPI | `tests/test_chatbot_nlp.py` (12) | compétences et alias, élision ≠ langage C, années, niveaux, contrats, villes issues des offres, titres exacts et approchés, titre inexistant, mots-clés |
| FastAPI | `tests/test_chatbot_intents.py` (4, paramétrés) | intentions reconnues, questions hors domaine refusées, déterminisme |
| FastAPI | `tests/test_chatbot_engine.py` (19) | recherche par compétence / contrat / ville / expérience / niveau, résultats partiels annoncés, aucune offre inventée (technologie inconnue, catalogue vide, titre inexistant), compétences / expérience / formation / salaire d'une offre, FAQ, `UNKNOWN`, même question = même réponse |
| FastAPI | `tests/test_chatbot_route.py` (8) | clé interne obligatoire, JSON camelCase, question vide / trop longue, offre de contexte hors catalogue, doublons, champ personnel refusé, journaux sans la question |
| Spring Boot | `ChatbotApiTest` (9) | réponse basée sur les seules offres publiées, offre de contexte (brouillon, fermée, inexistante ignorées), FAQ, offre hors catalogue rejetée, réponse invalide jamais affichée, service IA arrêté → 503, validations, rôles (401 / 403) |
| Angular | `chatbot-page.spec.ts` (11), `chatbot.service.spec.ts` (3), `job-detail-page.spec.ts` | accueil, envoi, chargement, réponse avec offres, sélection d'une offre, offre passée par l'URL, erreur et « Réessayer », Entrée / Maj+Entrée, suggestions, conversation propre à chaque utilisateur, bouton depuis l'offre |
| E2E API | `scripts/e2e-step10.ps1` (42) | vraie pile Spring Boot → FastAPI → MySQL : accès, validations, recherches comparées à une requête SQL, brouillon jamais cité, questions sur une offre, offre inexistante, FAQ, `UNKNOWN`, panne du service IA, journaux sans données personnelles, nettoyage |
| E2E navigateur | `frontend/e2e/chatbot-flow.e2e.ts` (2) | connexion candidat → Assistant RH → question → réponse basée sur l'offre réelle (vérifiée dans MySQL) → « Voir l'offre » ; depuis une offre : formation, matching, offre inexistante, question inconnue |

## 11. Fichiers

- FastAPI : `app/api/routes/chatbot.py`, `app/schemas/chatbot.py`, `app/services/chatbot/` (`nlp.py`,
  `intents.py`, `offers.py`, `engine.py`), `app/resources/chatbot_knowledge.json`.
- Spring Boot : `chatbot/controller/ChatbotController.java`, `chatbot/service/ChatbotService.java`,
  `chatbot/dto/*`, `joboffer/repository/JobOfferRepository.java` (`findIdsByStatus`, `findWithDetailsByIdIn`).
- Angular : `features/candidate/chatbot/` (page), `core/services/chatbot.service.ts`,
  `core/models/chatbot.models.ts`, bouton dans `features/public/job-detail-page.html`.
