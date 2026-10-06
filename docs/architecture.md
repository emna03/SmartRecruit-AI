# Architecture — SmartRecruit AI

## 1. Vue d'ensemble

```
                 ┌──────────────────────────────────────┐
                 │ Utilisateur (navigateur)             │
                 │ candidat / recruteur / admin         │
                 └──────────────────┬───────────────────┘
                                    ↓  HTTP
                 ┌──────────────────────────────────────┐
                 │ Frontend Angular :4200               │
                 │ Bootstrap, Chart.js, guards de rôle  │
                 └──────────────────┬───────────────────┘
                                    ↓  REST JSON + JWT (Authorization: Bearer ...)
┌──────────────────────────────────────┐  REST interne    ┌─────────────────────────────────────────┐
│ Backend Spring Boot :8080            │  X-Internal-     │ Service IA FastAPI 127.0.0.1:8000       │
│ API principale : sécurité JWT,       │ ───Api-Key────>  │ sans état, sans accès à MySQL           │
│ rôles, ownership, métier, validation │ <──JSON validé── │  ├─ CV Parser / NLP (PDF → JSON)         │
│ des réponses IA                      │                  │  ├─ Matching (CV ↔ offre, score 0-100)   │
└──────────────────┬───────────────────┘                  │  ├─ Chatbot « Assistant RH » (NLP local) │
                   ↓  JPA / Flyway                        │  └─ Machine Learning turnover            │
┌──────────────────────────────────────┐                  │     (modèle scikit-learn hors ligne)    │
│ MySQL 8.4 :3306 (Docker)             │                  └─────────────────────────────────────────┘
│ base smartrecruit, 15 tables (V1)    │
└──────────────────────────────────────┘
```

Lecture : l'utilisateur ne parle qu'à Angular, Angular ne parle qu'à Spring Boot, et Spring Boot est le seul à
accéder à MySQL et au service IA FastAPI (aller : données minimales ; retour : JSON vérifié avant enregistrement ou
affichage).

### Règles d'architecture

1. **Le frontend ne parle qu'au backend Spring Boot.** Il n'appelle jamais FastAPI ni MySQL directement.
2. **Spring Boot est la seule source de vérité** : il possède la base de données, applique la sécurité (JWT + rôles)
   et décide de ce qui est envoyé au service IA.
3. **Le service IA est sans état (stateless)** : il ne se connecte pas à MySQL. Il reçoit des données, calcule,
   et renvoie du JSON. Spring Boot persiste les résultats (`CV.extractedData`, `MatchingResult`).
4. **Appels internes protégés** par une clé partagée (`AI_INTERNAL_API_KEY`), FastAPI n'est pas exposé publiquement.
5. **Les réponses IA sont validées** deux fois : schémas Pydantic côté FastAPI, DTO + validation côté Spring Boot
   avant persistance / affichage.
6. **Résilience** : si FastAPI est indisponible (timeout, erreur), Spring Boot répond clairement sans bloquer le
   reste de l'application : 503 pour le chatbot, le turnover et le recalcul des scores côté recruteur ; CV enregistré
   avec le statut `ECHEC` (analyse relançable) ; matching candidat avec le statut `IA_INDISPONIBLE` et le dernier
   score enregistré.

## 2. Flux principaux

### 2.1 Import d'un CV

```
Candidat ──PDF──> Angular ──multipart──> Spring Boot
                                          │ 1. vérifie JWT (rôle CANDIDAT), taille, signature %PDF- du contenu
                                          │    (le type MIME et l'extension déclarés sont ignorés)
                                          │ 2. stocke le fichier dans UPLOAD_DIR/cvs (hors Git)
                                          │ 3. envoie le PDF à FastAPI /api/cv/parse
                                          v
                                       FastAPI: extraction texte → nettoyage → NLP
                                          │ renvoie {technologies, skills, educations, experiences, languages,
                                          │          totalExperienceMonths, highestEducationLevel, warnings…}
                                          v
                                       Spring Boot valide puis enregistre dans CV + Skill
```

### 2.2 Calcul du score de compatibilité

```
Spring Boot construit {compétences CV, expérience, diplôme} + {compétences requises offre, ...}
  → FastAPI /api/matching/score
  → {score, matchedSkills, missingSkills, détail du calcul}
  → enregistré dans MatchingResult, utilisé pour trier les candidats
```

Formule (compétences 60 %, expérience 25 %, formation 15 %), exemples et limites : [ai/matching.md](ai/matching.md).

### 2.3 Chatbot RH

```
Candidat → Angular (Assistant RH) → Spring Boot POST /api/candidate/chatbot/messages
  → Spring Boot ajoute un contexte NON personnel (offres publiées : titre, entreprise, ville, contrat,
    expérience, niveau, salaire, compétences)
  → FastAPI /api/chatbot/ask : entités + intention (NLP local déterministe) → réponse construite sur ces offres
       └─ question hors domaine → « Je ne sais pas répondre » (jamais de réponse inventée)
  → Spring Boot vérifie que les offres citées font partie du catalogue envoyé → Angular
```

Aucun LLM n'est intégré (choix argumenté dans [ai/chatbot.md](ai/chatbot.md#pourquoi-pas-de-llm-externe)) ;
aucune donnée sur le candidat (identité, CV, candidatures) n'est envoyée au service IA.

### 2.4 Risque de turnover (Machine Learning)

```
Hors ligne : python -m ml.download_dataset (IBM HR Analytics, fictif, SHA-256 vérifié)
             python -m ml.train → comparaison LR / forêt aléatoire → turnover_model.joblib + rapport d'évaluation

Admin / recruteur → Angular (Risque de turnover, 24 variables) → Spring Boot POST /api/hr/turnover/predictions
  → validation stricte, seules les 24 variables sont transmises (aucune identité, ni âge, genre, situation familiale)
  → FastAPI /api/ml/turnover/predict : modèle chargé au démarrage → score, niveau, au plus 5 facteurs (occlusion)
  → Spring Boot valide la réponse → Angular (avec l'avertissement « estimation… ne doit pas être utilisée seule »)
```

Rien n'est enregistré dans MySQL (aucune migration). Dataset, métriques et limites : [ai/turnover-ml.md](ai/turnover-ml.md).

### 2.5 Dashboards

```
Angular (dashboard du rôle) → GET /api/{candidate|recruiter|admin}/dashboard (périmètre tiré du JWT)
  → DashboardService → DashboardQueryRepository : requêtes JPQL agrégées (COUNT / AVG / MAX / GROUP BY)
    sur les tables V1, nombre de requêtes constant (pas de N+1)
  → DTO de statistiques → cartes KPI + graphiques Chart.js (composant partagé app-chart, chunk paresseux)
Admin : GET /api/admin/ai/status chargé à part pour l'état du module ML (aucune statistique de prédiction)
```

Aucune table ni migration supplémentaire. KPI, graphiques et limites : [dashboards.md](dashboards.md).

## 3. Structure des modules

### 3.1 Backend Spring Boot (`backend/`)

Organisation **par fonctionnalité** (package-by-feature) pour que chaque membre travaille dans son package :

```
backend/src/main/java/com/smartrecruit/
├── SmartRecruitApplication.java
├── config/          # CORS, configuration générale, client HTTP vers FastAPI
├── common/          # exceptions globales, réponses d'erreur, utilitaires   (partagé)
├── security/        # JWT, filtres, UserDetailsService                       (P1)
├── auth/            # inscription, connexion                                (P1)
├── user/            # User, Role, gestion des utilisateurs                  (P1)
├── candidate/       # CandidateProfile, Education, Experience               (P1/P2)
├── skill/           # Skill : référentiel de compétences partagé             (partagé)
├── recruiter/       # RecruiterProfile                                      (P1/P2)
├── company/         # Company                                               (P2)
├── joboffer/        # JobOffer, recherche/filtrage                          (P2)
├── application/     # Application (candidature) + statuts                   (P2)
├── cv/              # CV : upload, stockage, appel au parser IA             (P3)
├── matching/        # MatchingResult, appel au matching IA, classement      (P4)
├── chatbot/         # proxy vers le chatbot IA                              (P5)
├── turnover/        # prédiction du risque de turnover (ML), sans persistance (P5)
├── ai/              # AiServiceClient commun (timeouts, erreurs)            (P3/P4/P5)
└── dashboard/       # statistiques candidat / recruteur / admin             (P6)
```

Chaque package suit la même structure interne :
`entity/`, `repository/`, `dto/`, `service/`, `controller/` (+ `mapper/` si nécessaire).

### 3.2 Frontend Angular (`frontend/`)

```
frontend/src/app/
├── core/            # services API, AuthService, intercepteur JWT, guards de rôle, modèles
├── shared/          # composants réutilisables (loader, alertes, cartes, badge de statut, score, KPI, graphiques)
├── layout/          # navbar, sidebar, layouts par rôle
├── features/
│   ├── public/      # accueil, login, register, jobs, jobs/:id
│   ├── candidate/   # dashboard, profile, cv, jobs, applications, chatbot (Assistant RH)
│   ├── recruiter/   # dashboard, company, jobs, jobs/create, jobs/:id/candidates
│   ├── admin/       # dashboard, users, companies, jobs
│   └── hr/          # risque de turnover (ML), partagé par les espaces admin et recruteur
└── app.routes.ts
```

### 3.3 Service IA FastAPI (`ai-service/`)

```
ai-service/
├── app/
│   ├── __main__.py    # lancement : python -m app
│   ├── main.py        # create_app() : configuration, erreurs, journalisation, routes
│   ├── core/          # config.py (.env), security.py (clé interne), errors.py, logs.py
│   ├── api/
│   │   ├── router.py  # /health public ; /api/** protégé par la clé interne
│   │   └── routes/    # health, status (+ cv, matching, chatbot, ml)
│   ├── schemas/       # modèles Pydantic d'entrée/sortie (validation)
│   ├── services/
│   │   ├── cv_parser/ # extraction PDF, nettoyage, extraction NLP       (P3, étape 8)
│   │   ├── matching/  # calcul du score explicable                      (P4, étape 9)
│   │   ├── chatbot/   # entités, intentions, recherche d'offres, réponses (P5, étape 10)
│   │   └── ml/        # dataset, entraînement, explication, chargement du modèle (P5, étape 11)
│   └── resources/     # référentiel de compétences, synonymes, FAQ      (à partir de l'étape 8)
├── ml/                # download_dataset.py, train.py, reports/ (versionné) ; data/ et artifacts/ ignorés
└── tests/             # pytest
```

Côté Spring Boot, le package `ai/` contient `AiServiceClient` (clé interne, délais, traduction des erreurs en 503/422,
validation des réponses) et `config/AiServiceConfig` le client HTTP. Détails : [security.md](security.md#communication-interne-spring-boot--fastapi).

## 4. Ports et configuration

| Service      | Port | Configuration                          |
|--------------|------|----------------------------------------|
| Angular      | 4200 | `frontend/src/environments/`           |
| Spring Boot  | 8080 | `backend/src/main/resources/application.yml` + variables d'environnement (`SERVER_PORT`) |
| FastAPI      | 8000 | `.env` racine (ou `ai-service/.env`), écoute sur 127.0.0.1 (port tiré de `AI_SERVICE_URL`) |
| MySQL        | 3306 | `.env` (jamais commité), publié sur 127.0.0.1 uniquement (`DB_PORT`) |

Ce sont les ports par défaut : si l'un d'eux est déjà occupé, il suffit de changer `SERVER_PORT` ou `DB_PORT` dans
`.env` (le proxy Angular lit `SERVER_PORT`, la configuration Spring Boot lit `DB_PORT`).

Toutes les valeurs sensibles sont lues depuis des variables d'environnement. Le modèle est dans `.env.example`.

## 5. Choix techniques

| Domaine        | Choix                                   | Justification |
|----------------|-----------------------------------------|---------------|
| Frontend       | Angular + TypeScript + Bootstrap        | imposé ; Bootstrap simple et responsive |
| Backend        | Spring Boot 4.1, Java 21, Maven Wrapper | imposé ; Java 21 déjà installé, wrapper = pas besoin d'installer Maven |
| Sécurité       | Spring Security + JWT stateless         | séparation front/back, gestion des rôles |
| Base           | MySQL 8.4 dans Docker (`docker-compose.yml`) | imposé ; schéma géré par Flyway (migration V1, jamais modifiée) |
| IA             | Python 3.13 + FastAPI, environnement virtuel `ai-service/.venv` | écosystème NLP/ML (pdfplumber, expressions régulières, rapidfuzz, scikit-learn) ; pas de spaCy ni de LLM |
| Matching       | score pondéré explicable                | chaque point du score est justifiable (voir `docs/ai/`) |
| Chatbot        | NLP local (entités + intentions TF-IDF) + base de connaissances, sans LLM | réponses fondées sur les vraies offres, sans clé API ni envoi de données à un tiers (voir `docs/ai/chatbot.md`) |
