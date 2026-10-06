# Module IA — Machine Learning : prédiction du risque de turnover (étape 11)

Le module entraîne un vrai modèle de classification sur un dataset RH public et **fictif**, puis l'utilise pour
estimer le risque de départ (attrition) d'un employé à partir de 24 variables saisies par un recruteur ou un
administrateur. C'est une **démonstration académique d'aide à la décision** : le résultat n'est jamais une
certitude et ne déclenche aucune action automatique.

```
Angular (ADMIN / RECRUTEUR)        Spring Boot                         FastAPI                       Modèle
formulaire 24 variables ──► POST /api/hr/turnover/predictions ──► POST /api/ml/turnover/predict ──► Random Forest
        ▲                    JWT + rôle, Bean Validation          X-Internal-Api-Key, Pydantic       (joblib, SHA-256)
        └──────── score, niveau, 5 facteurs ◄── réponse IA validée ◄──────────── probabilité + facteurs
```

Toutes les métriques citées ci-dessous proviennent du rapport généré par l'entraînement,
`ai-service/ml/reports/turnover_evaluation.json` (versionné). L'exemple de réponse du § 9 est la sortie réelle du
modèle pour la ligne 690 (enregistrée aussi dans `backend/src/test/resources/turnover/high-risk-prediction.json`).
Aucune valeur n'a été saisie à la main.

## 1. Objectif

- Montrer un pipeline ML complet et reproductible : dataset → nettoyage → séparation train/test → entraînement →
  évaluation → sauvegarde → prédiction servie par API.
- Comparer deux modèles simples et explicables (régression logistique, forêt aléatoire) et choisir le modèle final
  sur des métriques adaptées à une classe minoritaire, pas sur l'accuracy seule.
- Expliquer chaque prédiction (score, niveau de risque, principaux facteurs) et rappeler ses limites dans
  l'interface.

Le modèle prédit le départ **d'un employé** d'après sa situation dans l'entreprise. Il ne s'applique pas aux
candidats et n'est relié à aucune donnée de la plateforme : rien n'est lu dans MySQL ni enregistré.

## 2. Dataset : provenance exacte

| Élément | Valeur |
|---|---|
| Nom | IBM HR Analytics Employee Attrition & Performance |
| Nature | **Dataset fictif** créé par des data scientists d'IBM à des fins pédagogiques (aucun employé réel) |
| Fichier utilisé | `emp_attrition.csv`, dépôt GitHub d'IBM [IBM/employee-attrition-aif360](https://github.com/IBM/employee-attrition-aif360) (licence Apache-2.0) |
| URL de téléchargement | `https://raw.githubusercontent.com/IBM/employee-attrition-aif360/master/data/emp_attrition.csv` |
| Empreinte SHA-256 | `a5c31e38bd7fafc9bc333884eb181b06b41b8e5e488e8f7ccb27199fb3be7659` |
| Miroir connu | Kaggle [pavansubhasht/ibm-hr-analytics-attrition-dataset](https://www.kaggle.com/datasets/pavansubhasht/ibm-hr-analytics-attrition-dataset) (même contenu, 1 470 lignes × 35 colonnes) |
| Taille | **1 470 lignes**, **35 colonnes** (34 variables + la cible `Attrition`) |
| Cible | `Attrition` = `Yes` (départ) / `No` ; **237 départs (16,12 %)**, 1 233 non-départs |
| Valeurs manquantes / doublons | aucune / aucun (vérifié à chaque chargement) |

Le CSV n'est **pas versionné** (`ai-service/ml/data/` est ignoré par Git). Il est téléchargé par
`python -m ml.download_dataset`, qui refuse le fichier si son empreinte SHA-256 diffère. À chaque entraînement,
`load_dataset()` revérifie l'empreinte, les colonnes, le nombre de lignes, l'absence de valeurs manquantes et de
doublons, les modalités des variables catégorielles et les bornes des variables numériques.

## 3. Variables

### 3.1 Variables utilisées (24)

| Variable du dataset | Champ API | Type / domaine | Signification (documentation du dataset) |
|---|---|---|---|
| BusinessTravel | `businessTravel` | Non-Travel, Travel_Rarely, Travel_Frequently | fréquence des déplacements |
| Department | `department` | Sales, Research & Development, Human Resources | département |
| JobRole | `jobRole` | 9 postes (le poste doit appartenir au département) | poste |
| JobLevel | `jobLevel` | 1 – 5 | niveau hiérarchique |
| OverTime | `overTime` | Yes / No | heures supplémentaires |
| DistanceFromHome | `distanceFromHome` | 1 – 29 | distance domicile-travail (unité non précisée) |
| MonthlyIncome | `monthlyIncome` | 1 009 – 19 999 | salaire mensuel (devise non précisée) |
| PercentSalaryHike | `percentSalaryHike` | 11 – 25 | dernière augmentation (%) |
| StockOptionLevel | `stockOptionLevel` | 0 – 3 | niveau de stock-options |
| TotalWorkingYears | `totalWorkingYears` | 0 – 40 | années d'expérience totales |
| NumCompaniesWorked | `numCompaniesWorked` | 0 – 9 | nombre d'entreprises précédentes |
| YearsAtCompany | `yearsAtCompany` | 0 – 40 | ancienneté dans l'entreprise |
| YearsInCurrentRole | `yearsInCurrentRole` | 0 – 18 | années dans le poste actuel |
| YearsSinceLastPromotion | `yearsSinceLastPromotion` | 0 – 15 | années depuis la dernière promotion |
| YearsWithCurrManager | `yearsWithCurrManager` | 0 – 17 | années avec le manager actuel |
| TrainingTimesLastYear | `trainingTimesLastYear` | 0 – 6 | formations suivies l'an dernier |
| Education | `education` | 1 – 5 | 1 Below College, 2 College, 3 Bachelor, 4 Master, 5 Doctor |
| EducationField | `educationField` | 6 domaines | domaine d'études |
| EnvironmentSatisfaction | `environmentSatisfaction` | 1 – 4 | 1 Low … 4 Very High |
| JobSatisfaction | `jobSatisfaction` | 1 – 4 | 1 Low … 4 Very High |
| RelationshipSatisfaction | `relationshipSatisfaction` | 1 – 4 | 1 Low … 4 Very High |
| JobInvolvement | `jobInvolvement` | 1 – 4 | 1 Low … 4 Very High |
| WorkLifeBalance | `workLifeBalance` | 1 – 4 | 1 Bad, 2 Good, 3 Better, 4 Best |
| PerformanceRating | `performanceRating` | 3 – 4 | 3 Excellent, 4 Outstanding (seules valeurs présentes) |

Les bornes acceptées par l'API sont exactement les minimums et maximums observés dans le dataset : le modèle n'est
jamais interrogé hors du domaine qu'il a vu. Contrôles de cohérence supplémentaires (FastAPI, Spring Boot et
Angular) : ancienneté ≤ expérience totale ; années dans le poste, avec le manager et depuis la promotion ≤
ancienneté ; poste compatible avec le département.

### 3.2 Variables exclues (10)

| Raison | Colonnes |
|---|---|
| Constantes (aucune information) | `EmployeeCount` (toujours 1), `Over18` (toujours Y), `StandardHours` (toujours 80) |
| Identifiant (pas de pouvoir prédictif légitime, risque de mémorisation) | `EmployeeNumber` |
| **Sensibles — décision de l'équipe** (risque de discrimination) | `Age`, `Gender`, `MaritalStatus` |
| Non documentées (signification inconnue) | `DailyRate`, `HourlyRate`, `MonthlyRate` |

## 4. Préparation des données

1. Chargement et vérification du CSV (section 2) ; cible convertie en 1 (départ) / 0.
2. **Séparation train/test avant tout calcul** (section 6).
3. Prétraitement **à l'intérieur d'un `Pipeline` scikit-learn** (`ColumnTransformer`) :
   - variables numériques : `StandardScaler` (centrage-réduction) ;
   - variables catégorielles : `OneHotEncoder` avec des **catégories fixées** dans le code (même ordre des colonnes
     à chaque exécution ; une modalité inconnue est refusée par la validation de l'API avant d'atteindre le modèle).

Comme le prétraitement fait partie du pipeline, la moyenne, l'écart-type et les modalités sont appris **uniquement
sur les données d'entraînement** de chaque pli de validation croisée, puis sur le jeu d'entraînement complet. Le
jeu de test n'intervient dans aucun calcul d'apprentissage : **pas de fuite de données**. Aucune variable dérivée de
la cible n'est utilisée.

## 5. Modèles comparés

| Modèle | Configuration | Hyperparamètres testés |
|---|---|---|
| Régression logistique (`LOGISTIC_REGRESSION`) | `class_weight="balanced"`, `max_iter=5000`, `random_state=42` | `C` ∈ {0,01 ; 0,03 ; 0,1 ; 0,3 ; 1 ; 3 ; 10} |
| Forêt aléatoire (`RANDOM_FOREST`) | 300 arbres, `class_weight="balanced_subsample"`, `random_state=42` | `max_depth` ∈ {4, 8, aucune} × `min_samples_leaf` ∈ {1, 5, 10} |
| Référence naïve (`DummyClassifier`) | prédit toujours la classe majoritaire (« pas de départ ») | — |

Les deux modèles sont explicables : la régression logistique par ses coefficients, la forêt aléatoire par
l'importance de ses variables et par occlusion (section 9). La pondération des classes compense le déséquilibre
(16 % de départs) : sans elle, un modèle peut atteindre une bonne accuracy en ne prédisant presque jamais de départ.

Les hyperparamètres sont choisis par **`GridSearchCV`, validation croisée stratifiée à 5 plis** (mélangée, graine 42)
**sur le jeu d'entraînement uniquement**, en maximisant le **F1-score** de la classe « départ ».

## 6. Séparation train/test

`train_test_split(test_size=0.2, stratify=y, random_state=42)` :

| Jeu | Lignes | Départs | Taux |
|---|---|---|---|
| Entraînement (+ validation croisée) | 1 176 | 190 | 16,2 % |
| **Test (jamais vu à l'entraînement)** | **294** | **47** | 16,0 % |

La stratification garde la même proportion de départs dans les deux jeux. Le jeu de test est utilisé **une seule
fois**, après le choix du modèle, pour mesurer les performances finales.

## 7. Résultats réellement obtenus

### 7.1 Validation croisée (jeu d'entraînement, moyenne ± écart-type sur 5 plis)

| Modèle | Meilleurs hyperparamètres | Accuracy | Precision | Recall | **F1** | ROC-AUC |
|---|---|---|---|---|---|---|
| Régression logistique | `C = 0.1` | 0,7525 ± 0,0202 | 0,3712 ± 0,0162 | 0,7526 ± 0,0636 | **0,4956 ± 0,0113** | 0,8316 ± 0,0272 |
| Forêt aléatoire | `max_depth = 8`, `min_samples_leaf = 10` | 0,8359 ± 0,0184 | 0,4964 ± 0,0438 | 0,5474 ± 0,0788 | **0,5173 ± 0,0476** | 0,8072 ± 0,0301 |

### 7.2 Jeu de test (294 employés, seuil de décision 0,5)

| Modèle | Accuracy | Precision | Recall | F1 | ROC-AUC | Average precision |
|---|---|---|---|---|---|---|
| Régression logistique | 0,7721 | 0,3780 | **0,6596** | 0,4806 | **0,8068** | **0,6009** |
| **Forêt aléatoire (retenue)** | 0,8197 | **0,4483** | 0,5532 | **0,4952** | 0,7844 | 0,4303 |
| Référence « jamais de départ » | **0,8401** | 0 | 0 | 0 | 0,5 | 0,1599 |

### 7.3 Matrices de confusion (jeu de test)

Lignes = réalité, colonnes = prédiction.

**Forêt aléatoire (modèle retenu)**

| | Prédit : reste | Prédit : part |
|---|---|---|
| **Reste (247)** | 215 (vrais négatifs) | 32 (faux positifs) |
| **Part (47)** | 21 (faux négatifs) | 26 (vrais positifs) |

**Régression logistique**

| | Prédit : reste | Prédit : part |
|---|---|---|
| **Reste (247)** | 196 | 51 |
| **Part (47)** | 16 | 31 |

**Référence naïve** : 247 vrais négatifs, 47 faux négatifs, aucun départ prédit.

### 7.4 Lecture des résultats

- **L'accuracy est trompeuse ici** : la référence naïve obtient la meilleure accuracy (84,0 %) sans détecter un seul
  départ. C'est pourquoi le choix se fait sur le F1 (compromis precision/recall de la classe « départ »).
- La forêt aléatoire détecte 26 départs sur 47 (recall 55,3 %) ; quand elle annonce un départ, elle a raison dans
  44,8 % des cas (precision). La régression logistique détecte plus de départs (31/47, recall 66,0 %) mais avec
  davantage de fausses alertes (51 contre 32).
- La régression logistique a un meilleur ROC-AUC et une meilleure average precision : elle **classe** globalement
  un peu mieux les employés par risque, alors que la forêt est meilleure au seuil 0,5.
- **Les deux modèles sont proches** : l'écart de F1 en validation croisée (0,0217) est inférieur à l'écart-type de la
  forêt (0,0476), et le test ne contient que 47 départs. Il ne faut pas conclure qu'un modèle est nettement
  supérieur à l'autre.

### 7.5 Niveaux de risque sur le jeu de test (modèle retenu)

| Niveau | Score | Employés | Départs réels | Taux de départ observé |
|---|---|---|---|---|
| FAIBLE | < 0,30 | 144 | 8 | 5,6 % |
| MOYEN | 0,30 – 0,50 | 92 | 13 | 14,1 % |
| ÉLEVÉ | ≥ 0,50 | 58 | 26 | 44,8 % |

Le taux de départ réel augmente bien avec le niveau : les niveaux ont un sens, mais même dans le niveau ÉLEVÉ,
**plus de la moitié des employés ne sont pas partis**. Les seuils 0,30 et 0,50 sont des constantes fixées dans le
code (`MEDIUM_THRESHOLD`, `HIGH_THRESHOLD`), pas optimisées sur le jeu de test ; 0,50 est aussi le seuil utilisé pour
les métriques.

### 7.6 Interprétation globale

Coefficients les plus forts de la régression logistique (variables standardisées ou indicatrices ; positif = augmente
le risque) : poste Laboratory Technician (+0,69), heures supplémentaires Oui (+0,66) / Non (−0,66), expérience totale
(−0,59), aucun déplacement (−0,58), déplacements fréquents (+0,58), poste Sales Representative (+0,48), années depuis
la dernière promotion (+0,43), stock-options (−0,39), satisfaction vis-à-vis de l'environnement (−0,37).

Importances de la forêt aléatoire (diminution moyenne d'impureté) : salaire mensuel (0,101), expérience totale
(0,088), ancienneté (0,064), années avec le manager (0,064), stock-options (0,059), heures supplémentaires (0,058 /
0,056), niveau hiérarchique (0,046), nombre d'entreprises (0,044), distance (0,043). Ce type d'importance favorise
les variables numériques à nombreuses valeurs : elle sert d'indication, pas de mesure exacte.

Les deux modèles s'accordent sur les mêmes grands signaux (heures supplémentaires, expérience et ancienneté faibles,
peu de stock-options, faible rémunération). Ce sont des **corrélations dans un dataset fictif**, pas des causes.

## 8. Modèle retenu et justification

**Forêt aléatoire** (`max_depth = 8`, `min_samples_leaf = 10`, 300 arbres), version `1.0.0`.

Règle de sélection **fixée avant l'entraînement** (`select_model()` dans `training.py`) :

1. on retient le modèle au meilleur **F1 moyen en validation croisée** (jeu d'entraînement seulement) ;
2. si l'écart est inférieur à **0,02**, on préfère la régression logistique, plus simple et plus directement
   explicable.

Résultat : 0,5173 (forêt) contre 0,4956 (régression logistique), soit un écart de 0,0217 > 0,02 ⇒ forêt aléatoire.
Le jeu de test confirme ce classement en F1 (0,4952 contre 0,4806) et en precision, alors que la régression logistique
reste meilleure en recall et en ROC-AUC. La règle n'a pas été modifiée après avoir vu les résultats du test :
choisir le modèle d'après le test reviendrait à l'utiliser pour l'apprentissage et rendrait ses métriques trop
optimistes.

Dans le contexte de l'application, la meilleure precision limite les fausses alertes sur des employés qui ne
comptent pas partir. Si l'objectif était au contraire de ne manquer aucun départ, la régression logistique (meilleur
recall) serait un choix défendable : c'est une décision métier, pas seulement technique.

## 9. Fonctionnement d'une prédiction

1. **Angular** (`/recruiter/turnover` ou `/admin/turnover`, lien « Risque de turnover ») : formulaire des 24
   variables, sans nom, identifiant, âge, genre ni situation familiale. Validation immédiate (champs requis, entiers,
   bornes, cohérence, poste filtré par département). Deux boutons remplissent des exemples fictifs issus du jeu de
   test.
2. **Spring Boot** `POST /api/hr/turnover/predictions` (JWT, rôles ADMIN ou RECRUTEUR) : Bean Validation stricte
   (pas de chaîne à la place d'un nombre, pas de nombre à la place d'un booléen), puis envoi à FastAPI de ces 24
   champs uniquement, avec l'en-tête `X-Internal-Api-Key` : un champ inconnu envoyé par le navigateur est ignoré et
   jamais transmis. Rien n'est enregistré.
3. **FastAPI** `POST /api/ml/turnover/predict` : vérifie la clé interne, revalide la requête (Pydantic strict,
   champs supplémentaires refusés), calcule la probabilité avec le modèle chargé au démarrage.
4. **Niveau de risque** : FAIBLE si score < 0,30, MOYEN si < 0,50, ÉLEVÉ sinon.
5. **Facteurs (méthode `OCCLUSION`)** : pour chaque variable, on remplace sa valeur par une **valeur de référence**
   (médiane pour une variable numérique, modalité la plus fréquente pour une catégorielle, calculées sur le jeu
   d'entraînement), les autres restant inchangées, et on mesure la variation du score. Les 5 variations les plus
   fortes (en valeur absolue) sont renvoyées, avec leur sens (`INCREASES_RISK` / `DECREASES_RISK`) et la valeur de
   référence. Pour la régression logistique, la méthode serait `LINEAR` : contribution exacte au logit,
   coefficient × (valeur − moyenne d'entraînement). Ces facteurs décrivent **le comportement du modèle**, pas une
   cause réelle du départ.
6. **Spring Boot valide la réponse** avant de l'afficher : probabilité entre 0 et 1, niveau cohérent avec les seuils,
   au plus 5 facteurs distincts portant sur des variables de la requête, sens cohérent avec le signe de l'impact.
   Une réponse invalide ⇒ 503, jamais affichée.

Exemple réel (employé fictif, ligne 690 du CSV, jeu de test, parti dans le dataset) :

```json
{
  "probability": 0.9166, "riskLevel": "ELEVE", "mediumThreshold": 0.3, "highThreshold": 0.5,
  "factors": [
    { "feature": "totalWorkingYears", "label": "Années d'expérience totales", "impact": 0.104, "direction": "INCREASES_RISK", "referenceValue": "10" },
    { "feature": "jobLevel", "label": "Niveau hiérarchique", "impact": 0.0932, "direction": "INCREASES_RISK", "referenceValue": "2" },
    { "feature": "monthlyIncome", "label": "Salaire mensuel", "impact": 0.0924, "direction": "INCREASES_RISK", "referenceValue": "5004" },
    { "feature": "overTime", "label": "Heures supplémentaires", "impact": 0.0764, "direction": "INCREASES_RISK", "referenceValue": "false" },
    { "feature": "yearsAtCompany", "label": "Ancienneté dans l'entreprise", "impact": 0.0749, "direction": "INCREASES_RISK", "referenceValue": "5" }
  ],
  "model": { "name": "RANDOM_FOREST", "version": "1.0.0", "dataset": "IBM HR Analytics Employee Attrition & Performance",
             "explanationMethod": "OCCLUSION", "testAccuracy": 0.8197, "testPrecision": 0.4483, "testRecall": 0.5532,
             "testF1": 0.4952, "testRocAuc": 0.7844 }
}
```

Lecture : avec 10 ans d'expérience (la médiane) au lieu de 1, et toutes les autres valeurs identiques, le score
baisserait d'environ 10,4 points. L'exemple fictif n°1 (ligne 1290, resté dans le dataset) obtient 0,0786 (FAIBLE).

L'écran affiche le score en pourcentage, le niveau, les facteurs, le modèle et ses métriques de test, et le
message obligatoire : « Cette prédiction est une estimation produite par un modèle entraîné sur un dataset de
démonstration et ne doit pas être utilisée seule pour prendre une décision RH. »

### Disponibilité et intégrité du modèle

- Le modèle est un fichier local (`ai-service/ml/artifacts/turnover_model.joblib` + métadonnées
  `turnover_model.json`), **non versionné**, produit par `python -m ml.train`. Emplacement configurable :
  `AI_ML_MODEL_DIR`.
- Au démarrage, FastAPI refuse de le charger si l'empreinte SHA-256 du fichier diffère de ses métadonnées, si la
  version de scikit-learn diffère, ou si la liste des variables ne correspond pas. Dans ce cas, le module `ml` est
  annoncé indisponible (`GET /api/status`) et la prédiction renvoie 503 : **aucun score de secours**.
- Service IA arrêté ou trop lent ⇒ Spring Boot renvoie 503 « service IA indisponible » ; Angular affiche le message.

## 10. Reproductibilité

- Graine 42 pour la séparation, la validation croisée et les modèles ; catégories one-hot dans un ordre fixe ;
  versions figées dans `requirements.txt` (scikit-learn 1.7.2, numpy 2.5.3, pandas 3.0.6, scipy 1.16.3).
- Le rapport ne contient pas de date : deux entraînements donnent un fichier identique. Le test
  `test_training_is_reproducible_and_matches_the_committed_report` et le script `scripts/e2e-step11.ps1` réentraînent et comparent au rapport versionné.

```powershell
cd ai-service
.venv\Scripts\python.exe -m ml.download_dataset   # télécharge et vérifie le SHA-256
.venv\Scripts\python.exe -m ml.train              # entraîne, compare, évalue, sauvegarde
```

## 11. Limites

- **Dataset fictif** : les relations apprises reflètent la façon dont IBM a construit les données, pas le
  comportement d'employés réels. Le modèle ne doit pas être appliqué à une vraie entreprise.
- **Petit échantillon** : 1 470 lignes, 237 départs, 47 dans le test ; les métriques ont une incertitude importante
  (écart-type du F1 de la forêt en validation croisée : 0,048).
- **Performances modestes** : F1 ≈ 0,50 ; sur 58 employés classés ÉLEVÉ dans le test, 32 ne sont pas partis, et 21
  départs sur 47 n'ont pas été détectés.
- **Le score n'est pas une probabilité calibrée** : la pondération des classes rehausse les scores (dans le niveau
  ÉLEVÉ, le taux de départ observé est 44,8 % alors que les scores sont ≥ 50 %). Il sert à comparer et classer, pas
  à annoncer une probabilité exacte.
- **Photo à un instant donné** : pas de dimension temporelle (on ne sait pas quand le départ survient), et la nature
  du départ (démission, licenciement, retraite) n'est pas précisée.
- Unités du salaire et de la distance non documentées ; `PerformanceRating` ne prend que les valeurs 3 et 4.
- Les facteurs par occlusion changent une variable à la fois : ils ignorent les interactions et peuvent produire des
  combinaisons peu réalistes (ex. ancienneté modifiée sans changer l'expérience totale).
- Les importances globales de la forêt (impureté) favorisent les variables numériques.

## 12. Biais possibles

- **Variables sensibles exclues, mais des variables corrélées restent** : l'expérience totale, l'ancienneté, le niveau
  et le salaire sont liés à l'âge ; certains postes ou départements peuvent être liés au genre. Exclure `Age`,
  `Gender` et `MaritalStatus` réduit le risque, sans garantir l'absence de discrimination indirecte.
- **Aucun audit d'équité par groupe n'a été réalisé** (par exemple comparer recall et faux positifs selon le genre
  ou la tranche d'âge, à partir des colonnes exclues). C'est une étape nécessaire avant tout usage réel ; elle n'est
  pas présentée ici car elle n'a pas été mesurée.
- Les satisfactions sont des réponses déclaratives : un modèle réel dépendrait de la qualité et de la sincérité de
  ces enquêtes.
- Un risque ÉLEVÉ peut refléter des conditions de travail (heures supplémentaires, absence de promotion, faible
  salaire) plutôt qu'un trait de la personne : l'interpréter comme un défaut de l'employé serait un biais.

## 13. Aspects éthiques

- **Aide à la décision uniquement** : aucune action n'est déclenchée, aucun statut n'est modifié, rien n'est
  enregistré. L'avertissement est affiché au-dessus du formulaire et sous chaque résultat, et le score n'est jamais
  présenté comme une certitude.
- **Usage prévu** : identifier des leviers de rétention (charge de travail, évolution, rémunération) dans un
  contexte pédagogique, pas sanctionner ni écarter quelqu'un. Le modèle ne concerne pas les candidats.
- **Minimisation des données** : 24 variables professionnelles, aucune donnée d'identité (nom, email, identifiant),
  aucune variable sensible ; FastAPI refuse tout champ supplémentaire (un champ `gender` ou `age` ⇒ 400). Le dataset
  ne contient que des personnes fictives.
- **Journaux sans donnée personnelle** : Spring Boot journalise seulement le niveau et le modèle ; FastAPI le score,
  le niveau et le modèle. Les valeurs saisies ne sont jamais journalisées.
- **Accès restreint** : ADMIN et RECRUTEUR uniquement ; un candidat reçoit 403, un visiteur 401. Le navigateur
  n'appelle jamais FastAPI directement.
- **Transparence** : modèle, dataset, métriques de test et facteurs affichés ; métriques complètes dans le rapport
  versionné et dans ce document.
- Dans un contexte réel (RGPD), un tel traitement exigerait au minimum l'information des salariés, une analyse
  d'impact et une intervention humaine sur toute décision (article 22 : pas de décision fondée exclusivement sur un
  traitement automatisé).

## 14. Tests

| Niveau | Tests | Contenu |
|---|---|---|
| FastAPI (pytest) | 61 | `test_ml_dataset` (5 : empreinte, colonnes, bornes, modalités), `test_ml_training` (14 : **reproductibilité** contre le rapport versionné, séparation stratifiée sans recouvrement, métriques et référence naïve, règle de sélection, ROC-AUC calculé à la main, niveaux de risque, exemples), `test_ml_model` (7 : modèle absent, modifié ou incompatible refusé, déterminisme, décomposition linéaire, occlusion), `test_ml_route` (35 : prédiction valide, risque faible, risque élevé, données manquantes, valeurs invalides, incohérences, champs sensibles refusés, clé interne, 503 sans modèle, statut, journaux) |
| Spring Boot (JUnit) | 12 | `TurnoverPredictionApiTest` (11 : risque élevé et faible avec les vraies réponses du modèle, admin, données envoyées à FastAPI, champs manquants, hors bornes, mauvais type, incohérents, FastAPI 500/503, réponses IA invalides, 401/403), `TurnoverAiUnreachableTest` (service injoignable ⇒ 503) |
| Angular (Vitest) | 11 | page (avertissement, validations, filtrage des postes, exemples, résultat et facteurs, erreurs) et service HTTP |
| E2E API | 36 vérifications | `scripts/e2e-step11.ps1` : réentraînement identique au rapport, FastAPI direct, sécurité, vraies prédictions via Spring Boot, entrées invalides, aucune nouvelle table ni migration, panne de FastAPI, journaux |
| E2E navigateur | 3 | `frontend/e2e/turnover-flow.e2e.ts` (Playwright) : recruteur → saisie → Spring Boot → FastAPI → modèle réel → risque ÉLEVÉ (score identique au rapport) ; admin → exemple fictif → risque FAIBLE ; candidat sans accès |

Captures : `frontend/e2e/screenshots/19-recruteur-risque-turnover-eleve.png`,
`20-admin-risque-turnover-faible.png`.

## 15. Fichiers

- Entraînement : `ai-service/ml/download_dataset.py`, `ai-service/ml/train.py`, rapport
  `ai-service/ml/reports/turnover_evaluation.json`.
- FastAPI : `app/services/ml/` (`features.py`, `dataset.py`, `training.py`, `explain.py`, `model.py`),
  `app/schemas/ml.py`, `app/api/routes/ml.py`.
- Spring Boot : `turnover/` (contrôleur, service, DTO `TurnoverPredictionRequest` / `TurnoverPrediction`, énumérations),
  `common/json/` (désérialiseurs stricts), règle `/api/hr/**` dans `SecurityConfig`.
- Angular : `core/models/turnover.models.ts`, `core/services/turnover.service.ts`,
  `features/hr/turnover-prediction-page.*`, `features/hr/turnover-fields.ts`.
