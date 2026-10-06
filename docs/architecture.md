# Architecture — SmartRecruit AI

## 1. Vue d'ensemble

```
┌──────────────────────────┐
│  Frontend Angular :4200  │   Navigateur (candidat / recruteur / admin)
└────────────┬─────────────┘
             │ REST JSON + JWT (Authorization: Bearer ...)
             v
┌──────────────────────────┐        ┌──────────────────┐
│ Backend Spring Boot :8080│───────>│  MySQL :3306     │
│  (API principale,        │  JPA   │  base smartrecruit│
│   sécurité, métier)      │        └──────────────────┘
└────────────┬─────────────┘
             │ REST interne + header X-Internal-Api-Key
             v
┌──────────────────────────┐
│ Service IA FastAPI :8000 │
│  ├─ cv_parser   (PDF → texte → NLP → JSON)
│  ├─ matching    (CV ↔ offre → score 0-100 explicable)
│  ├─ chatbot     (FAQ + offres, LLM optionnel)
│  └─ ml          (modèle prédictif, si dataset disponible)
└──────────────────────────┘
```

### Règles d'architecture

1. **Le frontend ne parle qu'au backend Spring Boot.** Il n'appelle jamais FastAPI ni MySQL directement.
2. **Spring Boot est la seule source de vérité** : il possède la base de données, applique la sécurité (JWT + rôles)
   et décide de ce qui est envoyé au service IA.
3. **Le service IA est sans état (stateless)** : il ne se connecte pas à MySQL. Il reçoit des données, calcule,
   et renvoie du JSON. Spring Boot persiste les résultats (`CV.extractedData`, `MatchingResult`).
4. **Appels internes protégés** par une clé partagée (`AI_INTERNAL_API_KEY`), FastAPI n'est pas exposé publiquement.
5. **Les réponses IA sont validées** deux fois : schémas Pydantic côté FastAPI, DTO + validation côté Spring Boot
   avant persistance / affichage.
6. **Résilience** : si FastAPI est indisponible (timeout, erreur), Spring Boot renvoie une erreur claire
   (HTTP 503) sans bloquer le reste de l'application.

## 2. Flux principaux

### 2.1 Import d'un CV

```
Candidat ──PDF──> Angular ──multipart──> Spring Boot
                                          │ 1. vérifie JWT (rôle CANDIDAT), type MIME, taille
                                          │ 2. stocke le fichier dans UPLOAD_DIR (hors Git)
                                          │ 3. envoie le PDF à FastAPI /api/cv/parse
                                          v
                                       FastAPI: extraction texte → nettoyage → NLP
                                          │ renvoie {skills, education, experience, languages}
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

### 2.3 Chatbot RH

```
Utilisateur → Angular (widget) → Spring Boot /api/chatbot
  → Spring Boot ajoute un contexte NON personnel (offres publiées : titre, compétences, entreprise)
  → FastAPI /api/chatbot/ask
       ├─ intention reconnue (FAQ / recherche d'offres) → réponse déterministe
       └─ sinon, si LLM configuré → LLM avec timeout, réponse validée
            └─ sinon / erreur → réponse de repli
```

Aucune donnée personnelle brute (nom, email, téléphone, contenu de CV) n'est envoyée au LLM.

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
├── ai/              # AiServiceClient commun (timeouts, erreurs)            (P3/P4/P5)
└── dashboard/       # statistiques candidat / recruteur / admin             (P6)
```

Chaque package suit la même structure interne :
`entity/`, `repository/`, `dto/`, `service/`, `controller/` (+ `mapper/` si nécessaire).

### 3.2 Frontend Angular (`frontend/`)

```
frontend/src/app/
├── core/            # services API, AuthService, intercepteur JWT, guards de rôle, modèles
├── shared/          # composants réutilisables (loader, alertes, cartes, badge de statut, score)
├── layout/          # navbar, sidebar, layouts par rôle
├── features/
│   ├── public/      # accueil, login, register, jobs, jobs/:id
│   ├── candidate/   # dashboard, profile, cv, jobs, applications
│   ├── recruiter/   # dashboard, company, jobs, jobs/create, jobs/:id/candidates
│   ├── admin/       # dashboard, users, companies, jobs
│   └── chatbot/     # widget de chat
└── app.routes.ts
```

### 3.3 Service IA FastAPI (`ai-service/`)

```
ai-service/
├── app/
│   ├── main.py
│   ├── core/          # configuration (.env), sécurité clé interne, gestion d'erreurs
│   ├── api/routes/    # health, cv, matching, chatbot, ml
│   ├── schemas/       # modèles Pydantic d'entrée/sortie (validation)
│   ├── services/
│   │   ├── cv_parser/ # extraction PDF, nettoyage, extraction NLP       (P3)
│   │   ├── matching/  # calcul du score explicable                      (P4)
│   │   ├── chatbot/   # intentions, base de connaissances, client LLM   (P5)
│   │   └── ml/        # chargement du modèle et prédiction              (P5)
│   └── resources/     # référentiel de compétences, synonymes, FAQ
├── ml/                # entraînement hors ligne : data/ (ignoré), artifacts/ (ignoré), train.py
└── tests/
```

## 4. Ports et configuration

| Service      | Port | Configuration                          |
|--------------|------|----------------------------------------|
| Angular      | 4200 | `frontend/src/environments/`           |
| Spring Boot  | 8080 | `backend/src/main/resources/application.yml` + variables d'environnement |
| FastAPI      | 8000 | `ai-service/.env`                      |
| MySQL        | 3306 | `.env` (jamais commité)                |

Toutes les valeurs sensibles sont lues depuis des variables d'environnement. Le modèle est dans `.env.example`.

## 5. Choix techniques

| Domaine        | Choix                                   | Justification |
|----------------|-----------------------------------------|---------------|
| Frontend       | Angular + TypeScript + Bootstrap        | imposé ; Bootstrap simple et responsive |
| Backend        | Spring Boot 4.1, Java 21, Maven Wrapper | imposé ; Java 21 déjà installé, wrapper = pas besoin d'installer Maven |
| Sécurité       | Spring Security + JWT stateless         | séparation front/back, gestion des rôles |
| Base           | MySQL (compatible MariaDB XAMPP)        | imposé ; schéma géré par scripts de migration |
| IA             | Python + FastAPI                        | écosystème NLP/ML (pdfplumber, spaCy/regex, scikit-learn) |
| Matching       | score pondéré explicable                | chaque point du score est justifiable (voir `docs/ai/`) |
| Chatbot        | intentions + base de connaissances, LLM optionnel | fonctionne sans clé API, respecte les données personnelles |
