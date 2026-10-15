# Statut des outils de test (cours)

Chaque membre utilise un outil distinct sur SmartRecruit AI.  
Le statut **VALIDÉ** n’est autorisé qu’avec une preuve d’exécution réelle.

| Membre | Modèle IA | Outil | Test réalisé | Résultat | Preuve | Statut |
|--------|-----------|-------|--------------|----------|--------|--------|
| 1 | Logistic Regression | Selenium IDE | Parcours candidat (login → CV → offres Java) | PASS | `tests-tools/selenium/` | **VALIDÉ** |
| 2 | Random Forest | Diffblue Cover | — | — | — | À faire |
| 3 | XGBoost | Tabnine | — | — | — | À faire |
| 4 | Sentence Transformer | Mabl | — | — | — | À faire |
| 5 | K-Means | Testim | — | — | — | À faire |
| 6 | Gemini | Applitools | — | — | — | À faire |

## Détail Selenium (VALIDÉ)

- Outil : Selenium IDE standalone **4.0.1-beta.14**
- Projet : `tests-tools/selenium/smartrecruit-candidat.side`
- Résultat : voir `tests-tools/selenium/resultat-execution.md`
