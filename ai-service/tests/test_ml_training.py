import json

import numpy as np
import pytest

from app.core.config import SERVICE_DIR
from app.services.ml.dataset import load_dataset
from app.services.ml.training import (
    HIGH_THRESHOLD, LOGISTIC_REGRESSION, MEDIUM_THRESHOLD, RANDOM_FOREST, RANDOM_STATE, SELECTION_TOLERANCE,
    evaluate, risk_level, select_model,
)

COMMITTED_REPORT = SERVICE_DIR / "ml" / "reports" / "turnover_evaluation.json"


def _cv(f1_lr: float, f1_rf: float) -> dict:
    return {LOGISTIC_REGRESSION: {"mean": {"f1": f1_lr}}, RANDOM_FOREST: {"mean": {"f1": f1_rf}}}


def test_training_is_reproducible_and_matches_the_committed_report(trained_model):
    """Mêmes graines, mêmes versions : le pipeline relancé redonne exactement les métriques documentées."""
    _, report = trained_model

    assert json.loads(COMMITTED_REPORT.read_text(encoding="utf-8")) == json.loads(json.dumps(report))


def test_test_set_is_held_out_stratified_and_never_used_for_model_selection(trained_model):
    _, report = trained_model
    split = report["split"]

    assert (split["trainRows"], split["testRows"]) == (1176, 294)
    assert split["trainRows"] + split["testRows"] == report["dataset"]["rows"]
    assert split["randomState"] == RANDOM_STATE
    # Stratification : même proportion de départs (16 %) dans les deux jeux.
    assert split["trainDepartures"] / split["trainRows"] == pytest.approx(split["testDepartures"] / split["testRows"],
                                                                          abs=0.01)
    # Le choix repose sur la validation croisée du jeu d'entraînement uniquement.
    assert report["crossValidation"]["selectionMetric"] == "f1"
    assert report["selection"]["chosenModel"] == select_model(report["crossValidation"]["results"])[0]


def test_at_least_two_models_are_compared_with_all_required_metrics(trained_model):
    _, report = trained_model

    assert set(report["test"]["results"]) == {LOGISTIC_REGRESSION, RANDOM_FOREST}
    for metrics in report["test"]["results"].values():
        assert {"accuracy", "precision", "recall", "f1", "rocAuc", "confusionMatrix"} <= metrics.keys()
        matrix = metrics["confusionMatrix"]
        assert sum(matrix.values()) == report["split"]["testRows"]
        assert matrix["truePositives"] + matrix["falseNegatives"] == report["split"]["testDepartures"]


def test_accuracy_alone_is_misleading_the_majority_baseline_detects_no_departure(trained_model):
    _, report = trained_model
    baseline = report["test"]["baselineMostFrequent"]
    chosen = report["test"]["results"][report["selection"]["chosenModel"]]

    assert baseline["accuracy"] > 0.8
    assert baseline["recall"] == 0
    assert chosen["recall"] > 0.4 and chosen["rocAuc"] > baseline["rocAuc"]


def test_risk_levels_order_the_real_attrition_rate_on_the_test_set(trained_model):
    _, report = trained_model
    bands = {band["level"]: band for band in report["riskLevels"]["bandsOnTestSet"]}

    assert sum(band["employees"] for band in bands.values()) == report["split"]["testRows"]
    assert bands["FAIBLE"]["observedAttritionRate"] < bands["MOYEN"]["observedAttritionRate"] \
        < bands["ELEVE"]["observedAttritionRate"]


def test_selection_rule():
    assert select_model(_cv(0.40, 0.50))[0] == RANDOM_FOREST
    assert select_model(_cv(0.50, 0.40))[0] == LOGISTIC_REGRESSION
    # Écart inférieur à la tolérance : la régression logistique, plus explicable, est retenue.
    assert select_model(_cv(0.50, 0.50 + SELECTION_TOLERANCE / 2))[0] == LOGISTIC_REGRESSION


def test_evaluate_computes_the_confusion_matrix_and_metrics():
    y_true = np.array([1, 1, 0, 0, 0, 1])
    scores = np.array([0.9, 0.2, 0.6, 0.1, 0.3, 0.8])

    metrics = evaluate(y_true, scores)

    assert metrics["confusionMatrix"] == {"trueNegatives": 2, "falsePositives": 1, "falseNegatives": 1,
                                          "truePositives": 2}
    assert metrics["accuracy"] == round(4 / 6, 4)
    assert metrics["precision"] == round(2 / 3, 4)
    assert metrics["recall"] == round(2 / 3, 4)
    # 7 paires (départ, non-départ) sur 9 sont correctement ordonnées par le score.
    assert metrics["rocAuc"] == round(7 / 9, 4)


@pytest.mark.parametrize(("probability", "level"), [
    (0.0, "FAIBLE"), (MEDIUM_THRESHOLD - 0.0001, "FAIBLE"), (MEDIUM_THRESHOLD, "MOYEN"),
    (HIGH_THRESHOLD - 0.0001, "MOYEN"), (HIGH_THRESHOLD, "ELEVE"), (1.0, "ELEVE"),
])
def test_risk_level_thresholds(probability, level):
    assert risk_level(probability) == level


def test_report_examples_are_real_rows_of_the_test_set(trained_model):
    _, report = trained_model
    features, target = load_dataset()

    for example in report["examples"].values():
        line = int(example["source"].split("ligne ")[1].split()[0])
        row = features.iloc[line - 2]
        assert example["features"]["monthlyIncome"] == int(row["MonthlyIncome"])
        assert example["observedAttrition"] == bool(target.iloc[line - 2])
    assert report["examples"]["lowRisk"]["riskLevel"] == "FAIBLE"
    assert report["examples"]["highRisk"]["riskLevel"] == "ELEVE"
