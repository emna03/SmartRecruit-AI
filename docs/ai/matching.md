# Module IA — Matching CV ↔ offre et score de compatibilité (étape 9)

Le module calcule, pour un couple (candidat, offre), un **score de compatibilité entre 0 et 100**, ses sous-scores
et une explication lisible. Le score est une **aide pour le recruteur**, jamais une décision automatique.

```
CV analysé (étape 8) ─┐
                      ├─► Spring Boot ─► FastAPI POST /api/matching/score ─► score + explication
Offre (MySQL)  ───────┘        │                                                    │
                               └──────────── validation ◄───────────────────────────┘
                                                 │
                                   MySQL matching_results ─► Angular (candidat : offre ; recruteur : classement)
```

**Version de l'algorithme : `1.0.0`** (règles de correspondance `matching_rules.json` version `1.0`).
Chaque résultat enregistré porte sa version (`matching_results.algorithm_version`).

## 1. Données utilisées

| Côté | Donnée | Origine |
|---|---|---|
| Candidat | compétences et technologies détectées | analyse du CV (`cvs.extracted_data`, étape 8) |
| Candidat | durée totale d'expérience en mois | analyse du CV (`totalExperienceMonths`) |
| Candidat | niveau d'études le plus élevé | analyse du CV (`highestEducationLevel`) |
| Offre | compétences requises | `job_offer_skills` |
| Offre | expérience minimale (années) | `job_offers.min_experience_years` |
| Offre | niveau d'études demandé (facultatif) | `job_offers.education_level` |

Choix : **seules les informations réellement extraites du CV analysé sont utilisées**. Les compétences déclarées à
la main dans le profil ne comptent pas : elles ne sont pas vérifiables et pourraient être complétées uniquement pour
gonfler le score. Le candidat peut néanmoins importer dans son profil ce que le CV contient (étape 8).

**Aucune donnée d'identité n'est envoyée au service IA** : ni nom, ni email, ni téléphone, ni texte du CV. La requête
contient uniquement des noms de compétences, un nombre de mois et un niveau d'études :

```json
{
  "candidate": { "skills": ["Java", "Spring Boot", "PostgreSQL"], "experienceMonths": 50, "educationLevel": "BAC_PLUS_5" },
  "offer": { "requiredSkills": ["Docker", "Java", "MySQL"], "minExperienceYears": 3, "educationLevel": "BAC_PLUS_5" }
}
```

FastAPI refuse tout champ supplémentaire (un champ `email` ⇒ 400).

## 2. Formule (version 1.0.0)

### 2.1 Compétences — S (0 à 100)

Pour chaque compétence requise par l'offre, on cherche la meilleure correspondance dans le CV :

| Correspondance | Crédit | Exemple |
|---|---|---|
| **Exacte** (`EXACT`) — même compétence après normalisation (casse, accents, alias du lexique) | **1** | offre « Spring boot » ↔ CV « Spring Boot » |
| **Impliquée** (`IMPLIED`) — une compétence plus précise du CV la couvre | **1** | offre « SQL » ↔ CV « MySQL » ; offre « Java » ↔ CV « Spring Boot » |
| **Proche** (`RELATED`) — technologie interchangeable de la même famille | **0,5** | offre « MySQL » ↔ CV « PostgreSQL » |
| Absente | 0 | — |

\[ S = 100 \times \frac{\sum \text{crédits}}{N_{\text{requises}}} \]

- Les règles « implique » (68 règles, appliquées de proche en proche : Spring Boot ⇒ Spring ⇒ Java) et les
  15 familles (bases relationnelles, NoSQL, frameworks front-end, cloud public, CI, deep learning, etc.) sont dans
  `ai-service/app/resources/matching_rules.json`. Elles ne vont que dans un sens : connaître MySQL implique SQL,
  connaître SQL n'implique pas MySQL.
- Une compétence requise reçoit le **meilleur** crédit possible (exacte > impliquée > proche).
- Les compétences de l'offre sont dédoublonnées ; les listes « trouvées » et « manquantes » suivent l'ordre de
  l'offre (ordre alphabétique, envoyé par Spring Boot).

### 2.2 Expérience — E (0 à 100, ou « non exigée »)

\[ E = \min\left(100,\ 100 \times \frac{\text{mois d'expérience du CV}}{12 \times \text{années demandées}}\right) \]

- Si l'offre demande 0 an, le critère est **non applicable** (`null`) et ne compte pas dans le score global.
- Une expérience sans date dans le CV n'est pas comptée (0 mois) : on ne devine pas une durée.
- Progression linéaire : 2 ans pour 4 demandés ⇒ 50 %. Au-delà de l'exigence, pas de bonus (plafond 100).

### 2.3 Formation — F (0 à 100, ou « non exigée »)

Niveaux ordonnés : Bac < Bac+2 < Bac+3 < Bac+5 < Doctorat.

\[ F = \max(0,\ 100 - 40 \times \text{nombre de niveaux manquants}) \]

- Niveau atteint ou dépassé ⇒ 100 ; un niveau en dessous ⇒ 60 ; deux ⇒ 20 ; trois ou plus ⇒ 0.
- Aucun diplôme reconnu dans le CV alors que l'offre exige un niveau ⇒ 0.
- Offre sans niveau précisé ⇒ critère **non applicable** (`null`).

### 2.4 Score global

\[ \text{Score} = \frac{0{,}60 \cdot S + 0{,}25 \cdot E + 0{,}15 \cdot F}{\text{somme des poids des critères applicables}} \]

Pondérations : **compétences 60 %, expérience 25 %, formation 15 %**. Si un critère n'est pas exigé par l'offre,
son poids est redistribué proportionnellement sur les autres (ex. offre sans niveau d'études : compétences
70,6 %, expérience 29,4 %). Les scores sont arrondis à une décimale. Aucun élément aléatoire : la même entrée
donne toujours le même résultat (vérifié par les tests).

Libellés affichés : **≥ 75 % compatibilité élevée**, **≥ 50 % partielle**, **< 50 % faible**.

### 2.5 Pourquoi ces pondérations

- **Compétences 60 %** : c'est le critère le plus directement lié au poste et le plus fiable dans le CV (lexique
  de l'étape 8).
- **Expérience 25 %** : important, mais la durée totale ne dit pas si l'expérience est dans le bon domaine.
- **Formation 15 %** : souvent un prérequis administratif, moins discriminant une fois le niveau atteint.
- Une pénalité de 40 points par niveau rend un écart d'un niveau visible (60 %) sans être éliminatoire.

Ces valeurs sont un **choix d'expert documenté**, pas un apprentissage statistique : le projet ne dispose pas
d'historique de recrutements pour les ajuster. Elles sont centralisées dans
`ai-service/app/services/matching/scoring.py` (`WEIGHTS`, `EDUCATION_PENALTY_PER_LEVEL`).

## 3. Exemples réels (CV fictifs analysés par le CV Parser)

Offre : Java, Spring Boot, Angular, MySQL, Docker, Kubernetes — 3 ans — Bac+5.

| CV | Compétences S | Expérience E | Formation F | Score | Calcul |
|---|---|---|---|---|---|
| Développeuse full stack FR | 83,3 (5/6) | 100 (50 mois ≥ 36) | 100 (Bac+5) | **90,0** | 0,6×83,3 + 0,25×100 + 0,15×100 |
| Data scientist EN | 25,0 (Docker 1 + MySQL proche de PostgreSQL 0,5 = 1,5/6) | 100 | 100 | **55,0** | 0,6×25 + 25 + 15 |
| CV minimal FR | 16,7 (MySQL 1/6) | 0 (aucune expérience datée) | 60 (Bac+3, 1 niveau manquant) | **19,0** | 0,6×16,7 + 0 + 0,15×60 |

Autres situations testées de bout en bout :

- **Expérience insuffisante** (offre Java + Spring Boot, 10 ans, Doctorat, CV full stack) : S = 100, E = 41,7
  (50/120 mois), F = 60 ⇒ **79,4**.
- **Aucune compétence commune** (offre DevOps : Kubernetes, Terraform, Ansible ; 0 an ; pas de niveau ; CV minimal) :
  seul le critère compétences s'applique (poids 100 %) ⇒ **0**.
- **Offre modifiée** (5 ans au lieu de 3) : les scores de l'offre sont supprimés puis recalculés ; CV full stack :
  E = 83,3 (50/60) ⇒ **85,8**.

Exemple d'explication renvoyée (texte construit uniquement à partir des valeurs du calcul, sans LLM) :

```
Score global : 90 % — compatibilité élevée.
Compétences (poids 60 %) : 83 % — 5 compétence(s) requise(s) sur 6 trouvée(s) dans le CV : Angular, Docker, Java, MySQL, Spring Boot. Manquantes : Kubernetes.
Expérience (poids 25 %) : 100 % — 4 ans et 2 mois d'expérience détectée(s) dans le CV pour 3 ans demandé(s) (exigence satisfaite).
Formation (poids 15 %) : 100 % — niveau Bac+5 détecté pour Bac+5 demandé (niveau atteint).
Ce score est une aide indicative calculée automatiquement à partir du CV et de l'offre : il ne constitue pas une décision de recrutement.
```

## 4. Enregistrement (MySQL)

Entité `MatchingResult` / table `matching_results` (créée par la migration V1, **aucune nouvelle migration**) :
un seul résultat par (candidat, offre), remplacé à chaque calcul.

| Colonne | Contenu |
|---|---|
| `score`, `skills_score` | 0 à 100, deux décimales |
| `experience_score`, `education_score` | 0 à 100, ou `NULL` si l'offre n'exige pas ce critère |
| `matched_skills` | JSON : `[{"name":"MySQL","matchedBy":"PostgreSQL","matchType":"RELATED","credit":0.5}, …]` |
| `missing_skills` | JSON : `["Kubernetes"]` |
| `explanation` | explication lisible (≤ 4 000 caractères) |
| `algorithm_version` | `1.0.0` |
| `computed_at` | date du calcul (UTC) |

Un score est signalé **« CV modifié depuis ce calcul »** (`stale`) quand le CV a été remplacé ou réanalysé après le
calcul. Les scores sont **supprimés** quand le CV est supprimé, quand les critères de l'offre (compétences,
expérience, niveau) changent, et par cascade quand le candidat ou l'offre est supprimé.

## 5. Quand le score est calculé

- **Le candidat consulte une offre** (publiée, ou à laquelle il a postulé) : recalcul et enregistrement à chaque
  consultation — `GET /api/candidate/jobs/{id}/matching`.
- **Le candidat postule** : calcul automatique après la candidature ; s'il échoue, la candidature est quand même
  enregistrée.
- **Le recruteur clique sur « Calculer / Recalculer les scores »** : recalcul pour tous les candidats de l'offre ayant
  un CV analysé — `POST /api/recruiter/jobs/{id}/matching`.
- **Liste des candidats** (`GET /api/recruiter/jobs/{id}/applications`) : triée par score décroissant ; les
  candidats sans score viennent ensuite (du plus récent au plus ancien). Le recruteur ne voit que les scores des
  candidats qui ont postulé à **ses** offres.

## 6. Gestion des erreurs

| Situation | Candidat | Recruteur |
|---|---|---|
| Pas de CV | `CV_ABSENT` + lien « Importer mon CV » | « Sans score — Aucun CV importé » |
| CV non analysé (analyse en échec) | `CV_NON_ANALYSE` + lien « Mon CV » | « Sans score — CV non analysé » |
| Service IA arrêté, trop lent ou clé refusée | `IA_INDISPONIBLE` + **dernier score enregistré** s'il existe | recalcul ⇒ 503 « service IA indisponible » ; le classement enregistré reste consultable |
| Réponse IA invalide (score hors 0–100, listes incohérentes, JSON illisible) | rejetée par Spring Boot, jamais enregistrée ni affichée ⇒ `IA_INDISPONIBLE` | 503 |
| Offre non publiée (sans candidature) ou inexistante | 404 | — |
| Offre d'une autre entreprise | — | 404 |
| Mauvais rôle / non connecté | 403 / 401 | 403 / 401 |

Double validation : Pydantic dans FastAPI (`schemas/matching.py`), Bean Validation dans Spring Boot
(`AiMatchingResult` : bornes, tailles, compétences trouvées + manquantes = compétences requises).

## 7. Le score n'est pas une décision

- L'interface rappelle sur chaque résultat : « ne constitue pas une décision de recrutement » (candidat) et
  « Aide à la décision uniquement » (recruteur), ainsi qu'au-dessus du classement.
- Aucun statut de candidature n'est modifié automatiquement : présélection, acceptation et refus restent des
  actions manuelles du recruteur, qui voit aussi le profil complet et la lettre de motivation.
- L'explication détaille chaque point du score : le recruteur peut le contester (ex. compétence absente du lexique).

## 8. Limites (à présenter en soutenance)

- **Dépend de l'extraction de l'étape 8** : une compétence que le lexique ne connaît pas, ou écrite d'une façon non
  prévue, n'est pas détectée et compte comme manquante.
- **Règles de proximité manuelles** : 68 implications et 15 familles ; une proximité réelle non décrite n'est pas
  reconnue. Le crédit de 50 % est le même pour toutes les familles.
- **Expérience = durée totale**, pas l'expérience dans le domaine du poste ni le niveau de maîtrise d'une compétence.
- **Formation = niveau seulement**, pas la spécialité (un Bac+5 en biologie compte comme un Bac+5 en informatique).
- **Pondérations fixées à la main**, non apprises ; elles ne distinguent pas les compétences indispensables des
  compétences « appréciées » (l'offre n'a pas ce champ).
- Les langues, la localisation, le salaire et le type de contrat ne sont pas pris en compte.
- Un score n'a de sens qu'au sein d'une même version d'algorithme : en cas de changement de version, recalculer
  les scores (bouton recruteur).

## 9. Versionnement

`ALGORITHM_VERSION = "1.0.0"` (`ai-service/app/services/matching/engine.py`). Toute modification de la formule, des
pondérations ou des règles de correspondance doit changer cette version (et `version` dans
`matching_rules.json`), afin que chaque score enregistré reste traçable.

## 10. Tests

| Niveau | Tests | Contenu |
|---|---|---|
| FastAPI (pytest) | 52 | `test_matching_skills` (15 : exacte, impliquée transitive, proche, normalisation, dédoublonnage), `test_matching_scoring` (21 : formules, bornes, redistribution des poids), `test_matching_engine` (10 : vrais CV fictifs analysés — très / partiellement / peu compatible, aucune compétence commune, expérience et niveau insuffisants, déterminisme), `test_matching_route` (6 : clé interne, validation, champ personnel refusé, journaux sans compétence) |
| Spring Boot (JUnit) | 11 | `MatchingApiTest` : calcul et enregistrement, critères non exigés, CV absent / non analysé, IA indisponible (dernier score), réponses IA invalides, visibilité de l'offre et rôles, classement, recalcul recruteur, candidature malgré la panne, invalidation (CV, offre), cascade |
| Angular (Vitest) | 16 | carte de résultat (sous-scores, poids, compétences, avertissement), états de la page offre, classement et recalcul recruteur, service HTTP |
| E2E API + MySQL | 50 vérifications | `scripts/e2e-step9.ps1` : vrais CV → FastAPI → MySQL, classement, panne du service IA, invalidation, journaux |
| E2E navigateur | 4 | `frontend/e2e/matching-flow.e2e.ts` (Playwright) : candidat → CV → offre → score → MySQL → classement recruteur |

Captures : `frontend/e2e/screenshots/14-candidat-score-compatibilite.png`, `15-recruteur-classement-scores.png`,
`16-recruteur-detail-score.png`.

## 11. Fichiers

- FastAPI : `app/services/matching/` (`skills.py`, `scoring.py`, `explanation.py`, `engine.py`),
  `app/schemas/matching.py`, `app/api/routes/matching.py`, `app/resources/matching_rules.json`.
- Spring Boot : `matching/` (`MatchingService`, `MatchingResultMapper`, contrôleurs candidat et recruteur, DTO),
  classement dans `ApplicationService`, invalidation dans `CvService` et `JobOfferService`.
- Angular : `core/services/matching.service.ts`, `core/models/matching.models.ts`,
  `shared/matching-result-card.*`, `shared/match-score-badge.ts`, pages offre, candidats et détail de candidature.
