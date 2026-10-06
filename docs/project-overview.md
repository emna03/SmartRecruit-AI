# SmartRecruit AI — Documentation finale

Synthèse du projet pour la soutenance. Les détails techniques sont dans les documents liés ; tous les chiffres
cités ici proviennent du code, des tests ou des rapports versionnés.

## 1. Objectif

SmartRecruit AI est une plateforme web de recrutement : les **recruteurs** publient des offres pour leur entreprise,
les **candidats** importent leur CV et postulent, et un **service d'IA** aide les deux côtés :

- il **analyse le CV** (compétences, formations, expériences, langues) ;
- il calcule un **score de compatibilité explicable** entre un CV et une offre, et classe les candidats ;
- il répond aux questions des candidats avec un **Assistant RH** (chatbot) fondé sur les offres publiées ;
- il estime un **risque de turnover** avec un modèle de Machine Learning, comme outil RH de démonstration.

L'IA est une **aide à la décision** : aucun statut de candidature n'est modifié automatiquement.

## 2. Architecture

```
Utilisateur ↓ Angular ↓ Spring Boot ↓ MySQL
                         Spring Boot ↔ FastAPI (CV Parser/NLP, Matching, Chatbot, ML turnover)
```

- Le navigateur ne parle qu'à **Spring Boot**, qui applique la sécurité, possède la base MySQL et décide de ce qui
  est envoyé au service IA.
- Le service IA **FastAPI** est sans état : il ne se connecte pas à MySQL, n'écoute que sur `127.0.0.1` et exige
  une clé interne partagée. Ses réponses sont validées par Spring Boot avant enregistrement ou affichage.

Diagramme et flux détaillés : [architecture.md](architecture.md). Modèle de données : [database.md](database.md).

## 3. Technologies

| Couche | Technologies (versions utilisées) |
|---|---|
| Frontend | Angular 21.2, TypeScript 5.9, Bootstrap 5.3, Bootstrap Icons, Chart.js 4.5 ; tests Vitest 4 et Playwright 1.63 |
| Backend | Java 21, Spring Boot 4.1.1 (Web, Security, Data JPA, Validation), JWT (jjwt), Flyway, Maven Wrapper |
| Base de données | MySQL 8.4 (image Docker `mysql:8.4`) |
| Service IA | Python 3.13, FastAPI 0.142, Uvicorn 0.54, Pydantic 2.13, pdfplumber 0.11.10, rapidfuzz 3.14.6, scikit-learn 1.7.2, pandas 3.0.6, numpy 2.5.3, joblib 1.6.0 |
| Outils | Docker Compose, Git (branches `main` / `develop` / `feature/*`), Node.js 20.19+ (24 utilisé) |

Pas de spaCy, pas de LLM, aucun service d'IA externe.

## 4. Rôles

| Rôle | Peut faire |
|---|---|
| **CANDIDAT** | profil (compétences, formations, expériences), import et analyse du CV, recherche d'offres, score de compatibilité, candidature et suivi, Assistant RH, dashboard personnel |
| **RECRUTEUR** | entreprise, offres (brouillon, publication, fermeture), candidatures reçues classées par score, changement de statut, risque de turnover, dashboard de l'entreprise |
| **ADMIN** | gestion des utilisateurs (activer, désactiver, supprimer), entreprises et offres, état du service IA, risque de turnover, dashboard global |

Le rôle ADMIN ne s'obtient pas par inscription : le premier admin est créé au démarrage depuis `.env`.

## 5. Fonctionnalités

- **Authentification** : inscription candidat / recruteur, connexion, jeton JWT de 24 h, mots de passe BCrypt.
- **Profils et entreprises** : profil candidat complet ; un recruteur rattaché à une entreprise.
- **Offres** : création, brouillon / publiée / fermée, recherche publique (mot-clé, ville, contrat, compétence,
  niveau d'études, expérience maximale, entreprise).
- **Candidatures** : une par offre, statuts En attente, Présélectionné, Accepté, Refusé (changés par le recruteur).
- **CV PDF** et **IA** : voir les sections 6 à 9.
- **Dashboards** : section 10. Documentation des endpoints : [api.md](api.md).

## 6. CV Parser (NLP)

Le PDF est vérifié par Spring Boot (≤ 5 Mo, signature `%PDF-`), stocké sous un nom aléatoire, puis envoyé au service IA.
Le parser extrait le texte avec **pdfplumber**, découpe les sections (titres reconnus même avec une faute grâce à
**rapidfuzz**), puis détecte technologies et compétences avec un **lexique**, et formations, expériences, dates et
langues avec des **expressions régulières**. Emails, liens et téléphones sont masqués dans le texte conservé.

Sur la vérité terrain annotée (3 CV fictifs + 5 fichiers à rejeter), précision et rappel valent 1,00 sauf le rappel
des langues (0,88) ; ces scores sont **optimistes** car les CV de test ont été conçus avec le parser.
Détails : [ai/cv-parser.md](ai/cv-parser.md).

## 7. Matching CV ↔ offre

Score de 0 à 100 = **60 % compétences + 25 % expérience + 15 % formation** (poids redistribués si l'offre n'exige
pas un critère). Une compétence exacte ou impliquée (MySQL couvre SQL) compte 1, une compétence proche (PostgreSQL
pour MySQL) compte 0,5. Le résultat liste les compétences trouvées et manquantes et le détail du calcul ; il est
enregistré dans `matching_results` avec la version de l'algorithme (`1.0.0`) et sert à classer les candidats.
Exemple réel : le CV fictif « développeuse full stack » obtient 90,0 sur l'offre Java / Spring Boot / Angular /
MySQL / Docker / Kubernetes. Les pondérations sont un choix d'expert, pas un apprentissage.
Détails : [ai/matching.md](ai/matching.md).

## 8. Assistant RH (chatbot)

NLP local et déterministe : extraction d'entités (compétences, contrat, ville, années, niveau) et classification
d'intention par **similarité TF-IDF** avec des phrases d'exemple (recherche d'offres, questions sur une offre, FAQ de
la plateforme, salutations, question inconnue). Les réponses sont construites **uniquement** à partir des offres
publiées envoyées par Spring Boot ; hors domaine, l'assistant répond qu'il ne sait pas. Aucune donnée du candidat
n'est envoyée au service IA et les questions ne sont pas enregistrées. Détails : [ai/chatbot.md](ai/chatbot.md).

## 9. Machine Learning : risque de turnover

Modèle entraîné hors ligne (`python -m ml.train`) sur le dataset **public et fictif IBM HR Analytics** (1 470 employés,
16 % de départs). 24 variables professionnelles sont utilisées ; âge, genre et situation familiale sont **exclus**.
Régression logistique et forêt aléatoire sont comparées en validation croisée sur le F1 ; la **forêt aléatoire** est
retenue. Sur le jeu de test (294 employés) : accuracy 0,8197, precision 0,4483, recall 0,5532, F1 0,4952,
ROC-AUC 0,7844 — des performances **modestes**. Chaque prédiction donne un score, un niveau (FAIBLE < 0,30 ≤ MOYEN
< 0,50 ≤ ÉLEVÉ) et au plus 5 facteurs les plus influents ; rien n'est enregistré. Le dataset et le modèle ne sont pas
versionnés, le rapport des métriques l'est (`ai-service/ml/reports/turnover_evaluation.json`).
Détails : [ai/turnover-ml.md](ai/turnover-ml.md).

## 10. Dashboards

Un dashboard par rôle (`/api/{candidate|recruiter|admin}/dashboard`) : cartes KPI et graphiques Chart.js calculés
**en direct** par des requêtes d'agrégation sur MySQL (aucun chiffre simulé). Le périmètre est déduit du jeton :
un recruteur ne voit que son entreprise, un candidat que ses données. Détails : [dashboards.md](dashboards.md).

## 11. Sécurité

- JWT signé (HMAC-SHA256, secret ≥ 32 caractères obligatoire), compte relu en base à chaque requête.
- Règles par rôle (`/api/candidate/**`, `/api/recruiter/**`, `/api/admin/**`, `/api/hr/**`) et **ownership** : une
  ressource d'un autre utilisateur renvoie 404.
- Validation des entrées et format d'erreur unique (400, 401, 403, 404, 409, 413, 415, 422, 503), sans trace technique.
- Upload : contenu vérifié (pas le type déclaré), nom aléatoire, dossier ignoré par Git.
- Spring Boot ↔ FastAPI : clé interne, FastAPI sur 127.0.0.1, réponses IA validées ; panne IA → 503 lisible (chatbot,
  turnover, recalcul des scores) ou CV en échec relançable, sans bloquer le reste.
- CORS limité à l'origine Angular ; aucun secret dans Git (historique vérifié) ; aucune donnée personnelle dans les journaux.

Détails et audit final : [security.md](security.md).

## 12. Tests

| Suite | Commande | Résultat (étape 13) |
|---|---|---|
| Service IA (pytest) | `cd ai-service && .venv\Scripts\python.exe -m pytest` | 324 tests réussis |
| Backend (JUnit, Maven) | `cd backend && mvnw.cmd test` | 153 tests réussis |
| Frontend (Vitest) | `cd frontend && npx ng test --watch=false` | 122 tests réussis |
| Build Angular | `cd frontend && npx ng build` | réussi |
| Parcours navigateur (Playwright) | `cd frontend && npx playwright test` | 34 tests réussis |
| Scripts de bout en bout (PowerShell) | `scripts/e2e-*.ps1` (9 scripts) | 400 vérifications réussies |

Les scripts et les tests Playwright utilisent l'application réellement démarrée et comparent les résultats à MySQL ;
ils suppriment ensuite leurs données (préfixes `e2e*`). Commandes complètes : [README](../README.md#tests).

## 13. Limites

- **CV Parser** : pas d'OCR (CV scannés refusés), mises en page complexes mal ordonnées, lexique fermé, français et
  anglais seulement, évaluation sur 3 CV fictifs seulement.
- **Matching** : pondérations fixées par l'équipe, pas de compréhension sémantique au-delà du lexique et des familles
  de technologies ; la durée d'expérience ne dit pas si elle est dans le bon domaine.
- **Chatbot** : intentions et réponses limitées au domaine de la plateforme ; pas de dialogue libre ni de mémoire
  longue ; ne lit pas le CV.
- **ML turnover** : dataset fictif et petit (47 départs dans le test), F1 ≈ 0,50, aucun audit d'équité par groupe :
  outil de démonstration, jamais une décision RH.
- **Exploitation** : environnement de développement local (pas de HTTPS, de déploiement ni de supervision) ; les
  fichiers CV sont stockés sur le disque local.

## 14. Démonstration

L'ordre exact des actions à montrer, avec les phrases à dire, est dans **[demo-guide.md](demo-guide.md)** :
connexion recruteur → offre publiée → connexion candidat → import du CV → analyse NLP → matching → candidature →
Assistant RH → classement recruteur → dashboards → risque de turnover. Le test Playwright
`frontend/e2e/demo-journey.e2e.ts` rejoue ce scénario et vérifie chaque étape dans MySQL.
