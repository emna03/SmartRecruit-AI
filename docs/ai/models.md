# Six modèles d’IA — SmartRecruit AI (étape 12.6)

| Modèle | Type | Entraîné par nous ? | Données | Fonction | Métriques |
|--------|------|---------------------|---------|----------|-----------|
| Logistic Regression | ML supervisé | Oui (dataset IBM HR) | Variables RH anonymisées (24 features) | Prédiction risque de turnover | Accuracy, Precision, Recall, F1, ROC-AUC, AP, matrice de confusion |
| Random Forest | ML supervisé | Oui (même dataset / split) | Idem | Prédiction risque de turnover | Idem |
| XGBoost | ML supervisé | Oui (même dataset / split) | Idem ; `scale_pos_weight` calculé sur le train | Prédiction risque de turnover | Idem |
| Sentence Transformer (`paraphrase-multilingual-MiniLM-L12-v2`) | Deep Learning / NLP | Non — pré-entraîné | Textes professionnels CV ↔ offre (sans identité) | Similarité sémantique (embeddings + cosinus) | Similarité [0,1], score 0–100 |
| K-Means | ML non supervisé | Oui (profils CV analysés) | Embeddings Sentence Transformer des profils anonymisés | Clustering de profils candidats | Silhouette, inertie, K choisi |
| Gemini (`gemini-3.5-flash-lite`) | LLM / IA générative | Non — API externe | Données CV structurées anonymisées | Résumé professionnel + mots-clés | Qualité JSON, filtre anti-hallucination |

## Règle de sélection turnover (fixée avant comparaison)

Meilleur **F1 moyen en validation croisée** (classe « départ ») sur le train uniquement.  
Si l’écart entre les 2 meilleurs est &lt; **0,02**, on retient le modèle le plus explicable : **LR &gt; RF &gt; XGBoost**.

Les trois modèles sont toujours évalués sur le **même** jeu de test ; leurs métriques restent dans `ai-service/ml/reports/turnover_evaluation.json`.

## Matching : deux scores séparés

| Score | Origine | Affichage |
|-------|---------|-----------|
| Matching métier | Règles 60 % / 25 % / 15 % | « Matching métier : XX % » |
| Similarité sémantique IA | Sentence Transformer | « Similarité sémantique IA : YY % » |

Aucune formule de fusion arbitraire : les deux approaches restent comparables en soutenance.

## Répartition proposée (6 membres)

| Membre | Modèle | À expliquer |
|--------|--------|-------------|
| 1 | Logistic Regression | objectif, entrées, coefficients, entraînement, sorties, métriques, limites, tests |
| 2 | Random Forest | idem + importances / occlusion |
| 3 | XGBoost | idem + `scale_pos_weight`, comparaison équitable |
| 4 | Sentence Transformer | embeddings, cosinus, pré-entraîné ≠ entraîné par nous |
| 5 | K-Means | choix de K (silhouette), clusters vs descriptions |
| 6 | Gemini | prompt engineering, JSON, anti-hallucination / injection |

## Phrase de soutenance (validée seulement si le code le prouve)

> SmartRecruit AI exploite six modèles d'intelligence artificielle : trois modèles supervisés pour l'étude du turnover (Logistic Regression, Random Forest et XGBoost), un Sentence Transformer pré-entraîné pour le matching sémantique, K-Means pour le clustering des profils candidats, et Gemini comme LLM d'IA générative.
