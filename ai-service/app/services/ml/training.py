"""Pipeline d'entraînement du modèle de risque de turnover (lancé par ``python -m ml.train``).

Dataset -> nettoyage -> séparation train/test stratifiée (80/20, graine fixe) -> pour chaque modèle candidat,
recherche d'hyperparamètres par validation croisée sur le seul jeu d'entraînement -> choix du modèle final selon
une règle fixée à l'avance (F1 de la classe « départ » en validation croisée) -> évaluation unique sur le jeu de
test -> sauvegarde du modèle et du rapport.

Absence de fuite de données : le prétraitement (standardisation, one-hot) fait partie du pipeline scikit-learn,
il est donc ajusté uniquement sur les plis d'entraînement ; le jeu de test ne sert ni à l'ajustement, ni au choix
des hyperparamètres, ni au choix du modèle.
"""

import json
import logging
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

import joblib
import numpy as np
import sklearn
import xgboost
from sklearn.compose import ColumnTransformer
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score, average_precision_score, confusion_matrix, f1_score, precision_score, recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, StratifiedKFold, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from xgboost import XGBClassifier

from app.core.config import SERVICE_DIR
from app.services.ml import explain
from app.services.ml.dataset import (
    DATASET_NAME, DATASET_PATH, DATASET_SHA256, DATASET_URL, load_dataset, sha256_of,
)
from app.services.ml.features import (
    CATEGORICAL, CATEGORIES, DROPPED_CONSTANT, DROPPED_IDENTIFIER, DROPPED_SENSITIVE, DROPPED_UNDOCUMENTED,
    FEATURES, NUMERIC, row_to_api,
)
from app.services.ml.model import METADATA_FILE, MODEL_FILE
from app.services.ml.model import risk_level as _risk_level

logger = logging.getLogger("smartrecruit_ai.ml.training")

MODEL_VERSION = "1.1.0"
RANDOM_STATE = 42
TEST_SIZE = 0.2
CV_FOLDS = 5
# Règle de choix fixée avant l'entraînement : meilleur F1 moyen (classe « départ ») en validation croisée ;
# si l'écart est inférieur à cette tolérance, le modèle le plus explicable est retenu
# (régression logistique > forêt aléatoire > XGBoost).
SELECTION_TOLERANCE = 0.02
# Niveaux de risque : ÉLEVÉ à partir du seuil de décision du classifieur (0,5, celui des métriques),
# MOYEN dans la zone d'incertitude juste en dessous, FAIBLE sinon.
MEDIUM_THRESHOLD = 0.30
HIGH_THRESHOLD = 0.50

ARTIFACTS_DIR = SERVICE_DIR / "ml" / "artifacts"
REPORT_PATH = SERVICE_DIR / "ml" / "reports" / "turnover_evaluation.json"

LOGISTIC_REGRESSION = "LOGISTIC_REGRESSION"
RANDOM_FOREST = "RANDOM_FOREST"
XGBOOST = "XGBOOST"
EXPLAINABILITY_ORDER = (LOGISTIC_REGRESSION, RANDOM_FOREST, XGBOOST)
METRICS = ("accuracy", "precision", "recall", "f1", "roc_auc")


@dataclass(frozen=True)
class Candidate:
    name: str
    estimator: object
    grid: dict[str, list]
    explanation: str


def scale_pos_weight(y_train) -> float:
    """Poids de la classe positive calculé uniquement sur le jeu d'entraînement (pas de fuite)."""
    positives = int(np.sum(y_train == 1))
    negatives = int(np.sum(y_train == 0))
    if positives == 0:
        return 1.0
    return negatives / positives


def candidates(y_train=None) -> list[Candidate]:
    """Trois classifieurs comparés équitablement ; déséquilibre traité selon le modèle (class_weight / scale_pos_weight)."""
    pos_weight = scale_pos_weight(y_train) if y_train is not None else 1.0
    return [
        Candidate(
            LOGISTIC_REGRESSION,
            LogisticRegression(class_weight="balanced", max_iter=5000, random_state=RANDOM_STATE),
            {"model__C": [0.01, 0.03, 0.1, 0.3, 1.0, 3.0, 10.0]},
            explain.LINEAR,
        ),
        Candidate(
            RANDOM_FOREST,
            RandomForestClassifier(n_estimators=300, class_weight="balanced_subsample", random_state=RANDOM_STATE,
                                   n_jobs=1),
            {"model__max_depth": [4, 8, None], "model__min_samples_leaf": [1, 5, 10]},
            explain.OCCLUSION,
        ),
        Candidate(
            XGBOOST,
            XGBClassifier(
                objective="binary:logistic",
                eval_metric="logloss",
                scale_pos_weight=pos_weight,
                random_state=RANDOM_STATE,
                n_jobs=1,
                tree_method="hist",
            ),
            {
                "model__n_estimators": [100, 300],
                "model__max_depth": [3, 5, 7],
                "model__learning_rate": [0.05, 0.1],
            },
            explain.OCCLUSION,
        ),
    ]


def build_pipeline(estimator) -> Pipeline:
    preprocess = ColumnTransformer(
        [
            ("num", StandardScaler(), NUMERIC),
            # Catégories fixées à l'avance : mêmes colonnes quel que soit l'échantillon, valeur inconnue refusée.
            ("cat", OneHotEncoder(categories=[list(CATEGORIES[c].values()) for c in CATEGORICAL],
                                  handle_unknown="error", sparse_output=False), CATEGORICAL),
        ],
        remainder="drop",
    )
    return Pipeline([("preprocess", preprocess), ("model", estimator)])


def evaluate(y_true: np.ndarray, probabilities: np.ndarray, threshold: float = HIGH_THRESHOLD) -> dict:
    predicted = (probabilities >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, predicted, labels=[0, 1]).ravel()
    return {
        "accuracy": round(float(accuracy_score(y_true, predicted)), 4),
        "precision": round(float(precision_score(y_true, predicted, zero_division=0)), 4),
        "recall": round(float(recall_score(y_true, predicted, zero_division=0)), 4),
        "f1": round(float(f1_score(y_true, predicted, zero_division=0)), 4),
        "rocAuc": round(float(roc_auc_score(y_true, probabilities)), 4),
        "averagePrecision": round(float(average_precision_score(y_true, probabilities)), 4),
        "confusionMatrix": {"trueNegatives": int(tn), "falsePositives": int(fp),
                            "falseNegatives": int(fn), "truePositives": int(tp)},
    }


def select_model(cv_results: dict[str, dict]) -> tuple[str, str]:
    """Applique la règle de choix ; renvoie (modèle retenu, justification)."""
    ranked = sorted(cv_results, key=lambda name: cv_results[name]["mean"]["f1"], reverse=True)
    best, runner_up = ranked[0], ranked[1]
    gap = cv_results[best]["mean"]["f1"] - cv_results[runner_up]["mean"]["f1"]
    if gap < SELECTION_TOLERANCE:
        chosen = min(ranked[:2], key=EXPLAINABILITY_ORDER.index)
        return chosen, (f"Écart de F1 en validation croisée inférieur à {SELECTION_TOLERANCE} ({gap:.4f}) : "
                        f"le modèle le plus explicable est retenu.")
    return best, f"Meilleur F1 moyen en validation croisée (écart de {gap:.4f} avec {runner_up})."


def risk_level(probability: float) -> str:
    return _risk_level(probability, MEDIUM_THRESHOLD, HIGH_THRESHOLD)


def risk_bands(y_true: np.ndarray, probabilities: np.ndarray) -> list[dict]:
    """Taux de départ réellement observé dans chaque niveau de risque (jeu de test)."""
    levels = np.array([risk_level(float(p)) for p in probabilities])
    bands = []
    for level in ("FAIBLE", "MOYEN", "ELEVE"):
        mask = levels == level
        count = int(mask.sum())
        bands.append({"level": level, "employees": count, "departures": int(y_true[mask].sum()),
                      "observedAttritionRate": round(float(y_true[mask].mean()), 4) if count else None})
    return bands


def top_weights(pipeline: Pipeline, kind: str, count: int = 10) -> list[dict]:
    """Coefficients (variables standardisées, en log-odds) ou importances (impureté) les plus forts."""
    model = pipeline.named_steps["model"]
    weights = model.coef_[0] if kind == "coefficient" else model.feature_importances_
    names = pipeline.named_steps["preprocess"].get_feature_names_out()
    order = np.argsort(-np.abs(weights), kind="stable")[:count]
    return [{"column": str(names[i]).split("__", 1)[1], kind: round(float(weights[i]), 4)} for i in order]


def train(dataset_path: Path = DATASET_PATH, artifacts_dir: Path = ARTIFACTS_DIR,
          report_path: Path | None = REPORT_PATH) -> dict:
    """Exécute tout le pipeline et renvoie le rapport (aussi écrit dans ``report_path`` s'il est fourni)."""
    features, target = load_dataset(dataset_path)
    x_train, x_test, y_train, y_test = train_test_split(
        features, target, test_size=TEST_SIZE, stratify=target, random_state=RANDOM_STATE)
    folds = StratifiedKFold(n_splits=CV_FOLDS, shuffle=True, random_state=RANDOM_STATE)

    candidate_list = candidates(y_train)
    searches: dict[str, GridSearchCV] = {}
    cv_results: dict[str, dict] = {}
    for candidate in candidate_list:
        search = GridSearchCV(build_pipeline(candidate.estimator), candidate.grid, cv=folds, n_jobs=1,
                              scoring=list(METRICS), refit="f1")
        search.fit(x_train, y_train)
        best = search.best_index_
        key = {"roc_auc": "rocAuc"}
        entry: dict = {
            "bestParams": {name.removeprefix("model__"): value for name, value in search.best_params_.items()},
            "mean": {key.get(m, m): round(float(search.cv_results_[f"mean_test_{m}"][best]), 4) for m in METRICS},
            "std": {key.get(m, m): round(float(search.cv_results_[f"std_test_{m}"][best]), 4) for m in METRICS},
        }
        if candidate.name == XGBOOST:
            entry["scalePosWeight"] = round(float(scale_pos_weight(y_train)), 4)
        cv_results[candidate.name] = entry
        searches[candidate.name] = search
        logger.info("%s : F1 CV %.4f, ROC-AUC CV %.4f, paramètres %s", candidate.name,
                    cv_results[candidate.name]["mean"]["f1"], cv_results[candidate.name]["mean"]["rocAuc"],
                    cv_results[candidate.name]["bestParams"])

    chosen, justification = select_model(cv_results)
    y_test_values = y_test.to_numpy()
    test_results = {name: evaluate(y_test_values, search.predict_proba(x_test)[:, 1])
                    for name, search in searches.items()}
    baseline = DummyClassifier(strategy="most_frequent").fit(x_train, y_train)
    baseline_metrics = evaluate(y_test_values, baseline.predict_proba(x_test)[:, 1])

    final = searches[chosen].best_estimator_
    test_probabilities = final.predict_proba(x_test)[:, 1]
    explanation = next(c.explanation for c in candidate_list if c.name == chosen)
    bundle = {
        "pipeline": final,
        "modelName": chosen,
        "explanation": explanation,
        "background": explain.background_vector(final, x_train),
        "reference": explain.reference_row(x_train),
    }

    examples = {}
    for label, position in (("lowRisk", int(np.argmin(test_probabilities))),
                            ("highRisk", int(np.argmax(test_probabilities)))):
        row = x_test.iloc[position]
        examples[label] = {
            "source": f"Jeu de test, ligne {int(x_test.index[position]) + 2} du fichier CSV (employé fictif)",
            "observedAttrition": bool(y_test.iloc[position]),
            "probability": round(float(test_probabilities[position]), 4),
            "riskLevel": risk_level(float(test_probabilities[position])),
            "features": row_to_api(row.to_dict()),
        }

    report = {
        "modelVersion": MODEL_VERSION,
        "dataset": {
            "name": DATASET_NAME, "url": DATASET_URL, "sha256": DATASET_SHA256,
            "rows": int(len(features)), "positiveRate": round(float(target.mean()), 4),
            "featuresUsed": FEATURES,
            "dropped": {"constant": list(DROPPED_CONSTANT), "identifier": list(DROPPED_IDENTIFIER),
                        "sensitive": list(DROPPED_SENSITIVE), "undocumented": list(DROPPED_UNDOCUMENTED)},
        },
        "split": {"testSize": TEST_SIZE, "stratified": True, "randomState": RANDOM_STATE,
                  "trainRows": int(len(x_train)), "testRows": int(len(x_test)),
                  "trainDepartures": int(y_train.sum()), "testDepartures": int(y_test.sum())},
        "crossValidation": {"folds": CV_FOLDS, "shuffle": True, "randomState": RANDOM_STATE,
                            "selectionMetric": "f1", "results": cv_results},
        "selection": {"chosenModel": chosen, "rule": f"F1 CV maximal ; tolérance {SELECTION_TOLERANCE} en faveur "
                      "du modèle le plus explicable (LR > RF > XGBoost)", "justification": justification},
        "test": {"threshold": HIGH_THRESHOLD, "results": test_results,
                 "baselineMostFrequent": baseline_metrics},
        "riskLevels": {"mediumThreshold": MEDIUM_THRESHOLD, "highThreshold": HIGH_THRESHOLD,
                       "bandsOnTestSet": risk_bands(y_test_values, test_probabilities)},
        "explanation": explanation,
        "globalInterpretation": {
            LOGISTIC_REGRESSION: top_weights(searches[LOGISTIC_REGRESSION].best_estimator_, "coefficient"),
            RANDOM_FOREST: top_weights(searches[RANDOM_FOREST].best_estimator_, "importance"),
            XGBOOST: top_weights(searches[XGBOOST].best_estimator_, "importance"),
        },
        "examples": examples,
        "sklearnVersion": sklearn.__version__,
        "xgboostVersion": xgboost.__version__,
    }

    save(bundle, report, artifacts_dir)
    if report_path is not None:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def save(bundle: dict, report: dict, artifacts_dir: Path) -> None:
    """Modèle (joblib, non versionné) + métadonnées (empreinte du fichier, version, seuils, métriques de test)."""
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    model_path = artifacts_dir / MODEL_FILE
    joblib.dump(bundle, model_path)
    chosen = report["selection"]["chosenModel"]
    test = report["test"]["results"][chosen]
    metadata = {
        "modelVersion": report["modelVersion"],
        "modelName": chosen,
        "explanation": bundle["explanation"],
        "artifactFile": MODEL_FILE,
        "artifactSha256": sha256_of(model_path),
        "sklearnVersion": sklearn.__version__,
        "xgboostVersion": xgboost.__version__,
        "datasetName": DATASET_NAME,
        "datasetSha256": DATASET_SHA256,
        "features": FEATURES,
        "mediumThreshold": MEDIUM_THRESHOLD,
        "highThreshold": HIGH_THRESHOLD,
        "testMetrics": {k: test[k] for k in ("accuracy", "precision", "recall", "f1", "rocAuc")},
        "trainedAt": datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (artifacts_dir / METADATA_FILE).write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
                                               encoding="utf-8")
