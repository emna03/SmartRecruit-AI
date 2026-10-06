"""Facteurs ayant influencé une prédiction.

- Régression logistique (méthode ``LINEAR``) : contribution exacte de chaque variable au logit,
  ``coef × (x − moyenne d'entraînement)`` dans l'espace prétraité (standardisé / one-hot), additionnée sur les
  colonnes one-hot d'une même variable. C'est la décomposition du score par rapport à un employé « moyen » du jeu
  d'entraînement (équivalente aux valeurs SHAP d'un modèle linéaire à variables indépendantes).
- Forêt aléatoire (méthode ``OCCLUSION``) : variation de probabilité quand la variable est remplacée par sa valeur
  de référence (médiane ou modalité la plus fréquente du jeu d'entraînement), les autres restant inchangées.

Ces facteurs décrivent le comportement du modèle, pas une causalité dans la réalité.
"""

import numpy as np
import pandas as pd
from sklearn.pipeline import Pipeline

from app.services.ml.features import CATEGORICAL, CATEGORIES, NUMERIC

LINEAR = "LINEAR"
OCCLUSION = "OCCLUSION"


def transformed_columns() -> list[str]:
    """Variable d'origine de chaque colonne produite par le prétraitement (numériques puis one-hot)."""
    return list(NUMERIC) + [column for column in CATEGORICAL for _ in CATEGORIES[column]]


def background_vector(pipeline: Pipeline, train_features: pd.DataFrame) -> np.ndarray:
    """Moyenne des variables prétraitées sur le jeu d'entraînement (référence des contributions linéaires)."""
    return np.asarray(pipeline.named_steps["preprocess"].transform(train_features)).mean(axis=0)


def reference_row(train_features: pd.DataFrame) -> dict[str, object]:
    """Valeur de référence de chaque variable : médiane (numériques) ou modalité la plus fréquente."""
    reference: dict[str, object] = {column: int(round(float(train_features[column].median()))) for column in NUMERIC}
    for column in CATEGORICAL:
        counts = train_features[column].value_counts()
        reference[column] = str(sorted(counts[counts == counts.max()].index)[0])
    return reference


def linear_contributions(pipeline: Pipeline, background: np.ndarray, row: pd.DataFrame) -> dict[str, float]:
    values = np.asarray(pipeline.named_steps["preprocess"].transform(row))[0]
    contributions = pipeline.named_steps["model"].coef_[0] * (values - background)
    totals: dict[str, float] = {}
    for column, contribution in zip(transformed_columns(), contributions, strict=True):
        totals[column] = totals.get(column, 0.0) + float(contribution)
    return totals


def occlusion_contributions(pipeline: Pipeline, reference: dict[str, object], row: pd.DataFrame) -> dict[str, float]:
    probability = float(pipeline.predict_proba(row)[0, 1])
    variants = pd.concat([row] * len(reference), ignore_index=True)
    for index, column in enumerate(reference):
        variants.loc[index, column] = reference[column]
    probabilities = pipeline.predict_proba(variants)[:, 1]
    return {column: probability - float(p) for column, p in zip(reference, probabilities, strict=True)}
