# Datasets ML

Ce dossier est ignoré par Git (sauf ce fichier) : les datasets ne sont pas versionnés.

## Dataset utilisé (étape 11) : IBM HR Analytics Employee Attrition & Performance

- Fichier attendu : `emp_attrition.csv` (1 470 lignes, 35 colonnes, cible `Attrition`, 237 départs).
- **Dataset fictif** créé par IBM à des fins pédagogiques : il ne contient aucun employé réel.
- Source : dépôt GitHub d'IBM [IBM/employee-attrition-aif360](https://github.com/IBM/employee-attrition-aif360)
  (licence Apache-2.0), même contenu que le dataset Kaggle
  [pavansubhasht/ibm-hr-analytics-attrition-dataset](https://www.kaggle.com/datasets/pavansubhasht/ibm-hr-analytics-attrition-dataset).
- SHA-256 : `a5c31e38bd7fafc9bc333884eb181b06b41b8e5e488e8f7ccb27199fb3be7659`.

Téléchargement et vérification de l'empreinte (depuis `ai-service`) :

```powershell
.venv\Scripts\python.exe -m ml.download_dataset
```

Variables utilisées, préparation, métriques et limites : `docs/ai/turnover-ml.md`.
