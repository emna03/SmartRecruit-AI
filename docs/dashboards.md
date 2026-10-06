# Dashboards et statistiques — SmartRecruit AI

Étape 12 : tableaux de bord des trois rôles (candidat, recruteur, administrateur), calculés à chaque affichage à partir
des données enregistrées dans MySQL.

## 1. Principes

- **Aucun chiffre simulé ni codé en dur** : chaque KPI et chaque graphique provient d'une requête d'agrégation sur les
  tables de la migration V1. Aucune migration n'a été ajoutée ; rien n'est mis en cache ni précalculé.
- **Périmètre imposé par le jeton JWT** : aucun endpoint ne prend d'identifiant d'utilisateur ou d'entreprise ; le
  candidat ne voit que ses données, le recruteur celles de son entreprise, l'administrateur les chiffres globaux.
- **Le matching n'est jamais présenté comme une décision** : les scores sont accompagnés d'un rappel (« classement
  indicatif… jamais une décision automatique » / « ne constituent pas une décision de recrutement »).
- **Turnover** : les prédictions ne sont pas enregistrées (choix de l'étape 11, minimisation des données). Le dashboard
  admin affiche uniquement l'état réel du module ML (réponse de `GET /api/admin/ai/status`) et indique explicitement
  qu'il n'existe aucune statistique de prédiction.

## 2. Endpoints et autorisations

| Méthode | Chemin | Rôle | Périmètre |
|---|---|---|---|
| GET | `/api/candidate/dashboard` | `CANDIDAT` | le candidat connecté |
| GET | `/api/recruiter/dashboard` | `RECRUTEUR` | l'entreprise du recruteur connecté (toutes ses offres, quel que soit le recruteur qui les a créées) |
| GET | `/api/admin/dashboard` | `ADMIN` | toute la plateforme |

- Règles d'accès par préfixe (voir [security.md](security.md#règles-daccès)) : anonyme → 401, autre rôle → 403
  (l'administrateur n'accède pas non plus aux dashboards candidat et recruteur).
- Paramètres de requête ignorés (ex. `?companyId=` ou `?userId=`) : vérifié par les tests.
- Recruteur sans entreprise : `200` avec `hasCompany: false` et des compteurs à zéro (Angular affiche l'invitation à
  créer l'entreprise).
- Le dashboard admin charge aussi `GET /api/admin/ai/status` séparément : une panne du service IA n'empêche pas
  l'affichage des statistiques (état « service IA injoignable »).

Code : `backend/src/main/java/com/smartrecruit/dashboard/` (`controller/DashboardController`,
`service/DashboardService`, `repository/DashboardQueryRepository`, `dto/*`).

## 3. KPI et origine des données

### 3.1 Candidat

| KPI / donnée | Origine (MySQL) |
|---|---|
| Candidatures envoyées, par statut | `applications` du candidat, `GROUP BY status` |
| Offres disponibles / sans candidature de sa part | `job_offers.status = 'PUBLIEE'` / avec `NOT EXISTS` sur ses `applications` |
| État du CV : absent / importé / analysé | absence de ligne `cvs` / `cvs.parsing_status` ≠ / = `TERMINE` ; nombre de compétences extraites (`cv_skills`), dates d'import et d'analyse |
| Meilleurs scores de compatibilité (5) | `matching_results` du candidat pour les offres publiées ou auxquelles il a postulé (une offre brouillon n'est jamais visible), triés par score ; indicateurs « candidature envoyée », « offre plus publiée », « à recalculer » (CV modifié depuis le calcul) |
| Nombre d'offres avec un score | même filtre que ci-dessus, `COUNT` |

La page conserve aussi les blocs existants (candidatures récentes, complétude du profil, dernières offres publiées),
alimentés par les endpoints déjà en place, et propose des accès rapides : Mon CV, Offres, Mes candidatures,
Assistant RH.

### 3.2 Recruteur

| KPI / donnée | Origine (MySQL) |
|---|---|
| Offres créées ; publiées / brouillons / fermées | `job_offers` de l'entreprise, par `status` |
| Candidatures reçues, par statut | `applications` jointes aux offres de l'entreprise |
| Candidats par offre (et par statut) | `GROUP BY job_offer_id, status` |
| Score moyen de matching, nombre de candidatures avec score | `matching_results` **des seuls candidats ayant postulé** (jointure candidat + offre sur `applications`), moyenne arrondie à 0,1 |
| Score moyen et meilleur score par offre | idem, `GROUP BY` offre |
| Meilleurs candidats (5) | candidatures de l'entreprise ayant un score, triées par score décroissant ; lien vers le détail de la candidature |
| Candidatures par mois (6 mois) | `applications.created_at` |

Un score calculé lorsqu'un candidat a seulement consulté une offre n'apparaît pas chez le recruteur tant que ce
candidat n'a pas postulé.

### 3.3 Administrateur

| KPI / donnée | Origine (MySQL) |
|---|---|
| Utilisateurs (total, par rôle, désactivés) | `users` + `roles`, `users.enabled` |
| Candidats, recruteurs, recruteurs sans entreprise | `users` par rôle, `recruiter_profiles.company_id IS NULL` |
| Entreprises | `companies` |
| Offres (total, par statut), offres publiées par type de contrat | `job_offers` |
| Candidatures (total, par statut) | `applications` |
| CV importés, par statut d'analyse | `cvs.parsing_status` |
| Scores de compatibilité enregistrés, score moyen | `matching_results` (tous les calculs, y compris ceux faits à la consultation d'une offre) |
| Compétences les plus demandées (8) | `job_offer_skills` des offres publiées |
| Activité des 6 derniers mois | `created_at` de `users`, `job_offers`, `applications` |
| Module ML (turnover) | `GET /api/admin/ai/status` → `modules.ml` : modèle chargé / non disponible / service injoignable |

## 4. Graphiques

Bibliothèque : **Chart.js 4** utilisée directement via un composant partagé `app-chart`
(`frontend/src/app/shared/chart.ts`). `ng2-charts` n'est pas compatible avec Angular 21 (il exige Angular ≥ 22) ;
le composant maison se limite à créer et détruire le graphique, sans dépendance supplémentaire. Chart.js est chargé
dans un chunk paresseux (≈ 60 kB transférés) : le bundle initial n'a pas changé.

| Dashboard | Graphique | Type | Information apportée |
|---|---|---|---|
| Candidat | Mes candidatures par statut | doughnut | où en sont ses candidatures |
| Candidat | Mes meilleurs scores de compatibilité | barres horizontales (0–100) | offres les plus adaptées à son CV |
| Recruteur | Candidats par offre et par statut | barres horizontales empilées (10 offres les plus demandées) | offres qui attirent, avancement du traitement |
| Recruteur | Candidatures par statut | doughnut | charge de traitement |
| Recruteur | Score moyen et meilleur score par offre | barres (0–100) | adéquation des candidats à chaque offre |
| Recruteur | Candidatures reçues par mois | courbe (6 mois) | évolution réelle dans le temps (`created_at`) |
| Admin | Utilisateurs par rôle, offres par statut, candidatures par statut | doughnuts | répartition de la plateforme |
| Admin | Activité des 6 derniers mois | courbe, 3 séries | croissance : comptes, offres, candidatures |
| Admin | Compétences les plus demandées | barres horizontales | besoins des entreprises |
| Admin | Offres publiées par type de contrat | barres | nature des offres disponibles |

- Responsive (`maintainAspectRatio: false`, hauteur fixe, largeur du conteneur) ; pas de défilement horizontal sur
  mobile (vérifié par Playwright à 390 px).
- Accessibilité : chaque graphique a un `aria-label` et ses valeurs sont aussi rendues dans un tableau masqué
  visuellement, lu par les lecteurs d'écran (et utilisé par les tests pour comparer avec MySQL).
- État vide : si toutes les valeurs sont nulles, un message explicite remplace le graphique (« Aucune candidature
  reçue pour le moment », « Aucun score : … »).

## 5. Interface

- Cartes KPI (`app-kpi-card`), avec lien vers la page concernée quand c'est utile.
- États : chargement (spinner), erreur claire avec message du backend, états vides, recruteur sans entreprise.
- Mise en page Bootstrap responsive (2 cartes par ligne sur mobile, 4 à 6 sur grand écran), style des autres pages.

## 6. Performances

- Une requête JPQL agrégée (`COUNT`, `AVG`, `MAX`, `GROUP BY`, `EXISTS`) par indicateur, dans
  `DashboardQueryRepository` : aucune entité chargée en masse, aucun N+1.
- `DashboardQueryCountTest` vérifie avec les statistiques Hibernate que le nombre de requêtes SQL est **identique**
  avec un petit et un grand jeu de données.
- Mesure E2E sur MySQL : moins de 200 ms par dashboard (`scripts/e2e-step12.ps1`).

## 7. Tests

| Niveau | Fichier | Contenu |
|---|---|---|
| Spring Boot (H2) | `dashboard/DashboardApiTest` (8) | dashboards vides, candidat (statuts, CV, scores visibles), évolution après candidature et changement de statut, recruteur (scores des seuls postulants, moyenne), isolation entre deux recruteurs, admin comparé aux dépôts, 401/403 par rôle, aucun identifiant ciblable |
| Spring Boot (H2) | `dashboard/DashboardQueryCountTest` (1) | nombre de requêtes constant (pas de N+1) |
| Angular (Vitest) | `chart.spec.ts`, `dashboard.service.spec.ts`, `*-dashboard-page.spec.ts` (20) | configuration Chart.js, tableau accessible, état vide, KPI, graphiques, liens, erreurs, état du module ML |
| E2E API + MySQL | `scripts/e2e-step12.ps1` (102 vérifications) | vrais CV analysés et vrais scores FastAPI ; **chaque chiffre des trois dashboards comparé à une requête SQL** avant/après nouvelles candidatures et changements de statut ; isolation ; 401/403 ; nettoyage |
| E2E navigateur | `frontend/e2e/dashboard-flow.e2e.ts` (5) | données créées par l'API → MySQL → endpoints → Angular : KPI et valeurs des graphiques affichés comparés à MySQL, mise à jour après candidature et changement de statut, isolation, accès interdits, affichage mobile ; captures 21 à 24 |

```powershell
cd backend; .\mvnw.cmd test -Dtest='Dashboard*'
cd frontend; npx ng test --watch=false; npx playwright test e2e/dashboard-flow.e2e.ts   # service IA démarré
powershell -ExecutionPolicy Bypass -File scripts/e2e-step12.ps1 -BaseUrl http://localhost:8080  # service IA arrêté
```

## 8. Limites

- Les scores n'existent qu'une fois calculés (consultation d'une offre par un candidat ayant un CV analysé, ou
  recalcul par le recruteur) : un candidat qui a postulé sans CV analysé n'a pas de score.
- Le total des scores du dashboard admin inclut les calculs faits à la simple consultation d'une offre.
- Courbes mensuelles : 6 derniers mois en UTC, mois en cours inclus ; une donnée créée juste avant minuit peut
  apparaître dans le mois UTC voisin.
- Le graphique « candidats par offre » est limité aux 10 offres ayant le plus de candidatures (le tableau des offres
  reste complet).
- Aucune statistique de turnover : les prédictions ne sont pas enregistrées (voir [ai/turnover-ml.md](ai/turnover-ml.md)).
- Pas d'historique : les statuts sont ceux du moment (aucune table d'historique des changements de statut en V1),
  donc pas de délai moyen de traitement ni de taux de conversion dans le temps.
- Calcul à chaque affichage, sans cache : adapté au volume d'un projet universitaire ; sur une très grande base, des
  index supplémentaires ou un cache seraient à prévoir.
