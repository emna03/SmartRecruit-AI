"""Chargement du modèle entraîné et prédiction du risque de turnover.

Le modèle n'est chargé que s'il a été produit par ``python -m ml.train`` avec la même version de scikit-learn
et si l'empreinte SHA-256 du fichier correspond à celle enregistrée dans ses métadonnées (fichier local du
service, jamais reçu d'un client). Sinon, le module ML est annoncé indisponible : aucune prédiction de secours.
"""

import json
import logging
from dataclasses import dataclass
from pathlib import Path

import joblib
import numpy as np
import sklearn

from app.services.ml import explain
from app.services.ml.dataset import sha256_of, to_frame
from app.services.ml.features import CATEGORIES, FEATURES, LABELS, api_code, api_name

logger = logging.getLogger("smartrecruit_ai.ml")

MODEL_FILE = "turnover_model.joblib"
METADATA_FILE = "turnover_model.json"
MAX_FACTORS = 5
INCREASES_RISK = "INCREASES_RISK"
DECREASES_RISK = "DECREASES_RISK"


class ModelUnavailableError(Exception):
    """Modèle absent, modifié ou incompatible."""


def risk_level(probability: float, medium_threshold: float, high_threshold: float) -> str:
    if probability >= high_threshold:
        return "ELEVE"
    if probability >= medium_threshold:
        return "MOYEN"
    return "FAIBLE"


@dataclass(frozen=True)
class Factor:
    feature: str
    label: str
    impact: float
    direction: str
    # Valeur de comparaison (format de l'API) pour la méthode OCCLUSION ; None pour LINEAR (moyenne).
    reference_value: str | None


@dataclass(frozen=True)
class Prediction:
    probability: float
    risk_level: str
    factors: list[Factor]


class TurnoverModel:
    def __init__(self, bundle: dict, metadata: dict):
        self._pipeline = bundle["pipeline"]
        self._explanation = bundle["explanation"]
        self._background = np.asarray(bundle["background"])
        self._reference = dict(bundle["reference"])
        self.metadata = metadata

    @classmethod
    def load(cls, directory: Path) -> "TurnoverModel":
        model_path, metadata_path = directory / MODEL_FILE, directory / METADATA_FILE
        if not model_path.is_file() or not metadata_path.is_file():
            raise ModelUnavailableError(f"Modèle absent de {directory} : lancer python -m ml.train")
        try:
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            raise ModelUnavailableError(f"Métadonnées illisibles : {error}") from error
        if metadata.get("sklearnVersion") != sklearn.__version__:
            raise ModelUnavailableError(f"Modèle entraîné avec scikit-learn {metadata.get('sklearnVersion')}, "
                                        f"version installée {sklearn.__version__} : relancer python -m ml.train")
        if metadata.get("features") != FEATURES:
            raise ModelUnavailableError("Variables du modèle différentes de celles du service : réentraîner")
        if sha256_of(model_path) != metadata.get("artifactSha256"):
            raise ModelUnavailableError("Empreinte SHA-256 du modèle différente de ses métadonnées : fichier refusé")
        bundle = joblib.load(model_path)
        if bundle.get("modelName") != metadata.get("modelName") or bundle.get("explanation") not in (
                explain.LINEAR, explain.OCCLUSION):
            raise ModelUnavailableError("Contenu du modèle incohérent avec ses métadonnées")
        return cls(bundle, metadata)

    def predict(self, row: dict[str, object]) -> Prediction:
        """``row`` : une valeur par colonne du dataset (``FEATURES``), déjà validée."""
        frame = to_frame([row])
        probability = float(self._pipeline.predict_proba(frame)[0, 1])
        if self._explanation == explain.LINEAR:
            contributions = explain.linear_contributions(self._pipeline, self._background, frame)
        else:
            contributions = explain.occlusion_contributions(self._pipeline, self._reference, frame)
        ranked = sorted((item for item in contributions.items() if abs(item[1]) > 1e-9),
                        key=lambda item: (-abs(item[1]), item[0]))[:MAX_FACTORS]
        factors = [Factor(api_name(column), LABELS[column], round(value, 4),
                          INCREASES_RISK if value > 0 else DECREASES_RISK, self._reference_value(column))
                   for column, value in ranked]
        level = risk_level(probability, self.metadata["mediumThreshold"], self.metadata["highThreshold"])
        return Prediction(round(probability, 4), level, factors)

    def _reference_value(self, column: str) -> str | None:
        if self._explanation != explain.OCCLUSION:
            return None
        value = self._reference[column]
        if column in CATEGORIES:
            code = api_code(column, str(value))
            return ("true" if code == "YES" else "false") if column == "OverTime" else code
        return str(value)


def load_model(directory: Path) -> TurnoverModel | None:
    """Charge le modèle au démarrage du service ; ``None`` (module indisponible) en cas de problème."""
    try:
        model = TurnoverModel.load(directory)
    except ModelUnavailableError as error:
        logger.warning("Module ML indisponible : %s", error)
        return None
    except Exception:
        logger.exception("Module ML indisponible : chargement du modèle impossible")
        return None
    logger.info("Modèle ML chargé : %s v%s", model.metadata["modelName"], model.metadata["modelVersion"])
    return model
