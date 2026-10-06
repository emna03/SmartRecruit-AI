"""Chargement et nettoyage du dataset IBM HR Analytics Employee Attrition & Performance.

Dataset public et fictif (créé par des data scientists d'IBM), non versionné : ``python -m ml.download_dataset``
le télécharge dans ``ml/data`` et vérifie son empreinte SHA-256. Voir ``ml/data/README.md``.
"""

import hashlib
from pathlib import Path

import pandas as pd

from app.core.config import SERVICE_DIR
from app.services.ml.features import (
    CATEGORICAL, CATEGORIES, DROPPED_CONSTANT, DROPPED_IDENTIFIER, DROPPED_SENSITIVE, DROPPED_UNDOCUMENTED,
    FEATURES, NUMERIC, NUMERIC_RANGES, POSITIVE_LABEL, TARGET,
)

DATASET_NAME = "IBM HR Analytics Employee Attrition & Performance"
DATASET_FILE = "emp_attrition.csv"
DATASET_PATH = SERVICE_DIR / "ml" / "data" / DATASET_FILE
DATASET_URL = "https://raw.githubusercontent.com/IBM/employee-attrition-aif360/master/data/emp_attrition.csv"
DATASET_SHA256 = "a5c31e38bd7fafc9bc333884eb181b06b41b8e5e488e8f7ccb27199fb3be7659"
EXPECTED_ROWS = 1470

EXPECTED_COLUMNS = frozenset(
    FEATURES + [TARGET] + list(DROPPED_CONSTANT + DROPPED_IDENTIFIER + DROPPED_SENSITIVE + DROPPED_UNDOCUMENTED)
)


class DatasetError(Exception):
    """Dataset absent, modifié ou incohérent : aucun entraînement n'est lancé."""


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1 << 16), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_dataset(path: Path = DATASET_PATH) -> tuple[pd.DataFrame, pd.Series]:
    """Renvoie (X, y) : les 24 variables explicatives et la cible (1 = départ, 0 = reste).

    Vérifie l'empreinte du fichier, les colonnes, l'absence de valeurs manquantes ou de doublons, les catégories
    et les plages de valeurs : toute anomalie arrête le pipeline au lieu d'être corrigée silencieusement.
    """
    if not path.is_file():
        raise DatasetError(f"Dataset introuvable : {path}. Lancer : python -m ml.download_dataset")
    if (actual := sha256_of(path)) != DATASET_SHA256:
        raise DatasetError(f"Empreinte SHA-256 inattendue pour {path.name} : {actual}")

    frame = pd.read_csv(path, encoding="utf-8-sig")
    if set(frame.columns) != EXPECTED_COLUMNS:
        raise DatasetError(f"Colonnes inattendues : {sorted(set(frame.columns) ^ EXPECTED_COLUMNS)}")
    if len(frame) != EXPECTED_ROWS:
        raise DatasetError(f"{len(frame)} lignes au lieu de {EXPECTED_ROWS}")
    if frame.isna().any().any():
        raise DatasetError("Le dataset contient des valeurs manquantes")
    if frame.duplicated().any():
        raise DatasetError("Le dataset contient des lignes en double")
    if set(frame[TARGET].unique()) != {"Yes", "No"}:
        raise DatasetError("La cible Attrition doit valoir Yes ou No")
    for column in CATEGORICAL:
        unknown = set(frame[column].unique()) - set(CATEGORIES[column].values())
        if unknown:
            raise DatasetError(f"Catégories inconnues pour {column} : {sorted(unknown)}")
    for column, (low, high) in NUMERIC_RANGES.items():
        if frame[column].min() < low or frame[column].max() > high:
            raise DatasetError(f"{column} sort du domaine [{low}, {high}]")

    target = (frame[TARGET] == POSITIVE_LABEL).astype("int64").rename(TARGET)
    return to_frame(frame[FEATURES].to_dict("records")), target


def to_frame(rows: list[dict[str, object]]) -> pd.DataFrame:
    """Lignes (colonnes du dataset) -> DataFrame au format attendu par le pipeline (entraînement et prédiction)."""
    frame = pd.DataFrame(rows, columns=FEATURES)
    frame[NUMERIC] = frame[NUMERIC].astype("int64")
    frame[CATEGORICAL] = frame[CATEGORICAL].astype(object)
    return frame
