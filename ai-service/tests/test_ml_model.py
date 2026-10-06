import json
import math
import shutil

import pytest
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier

from app.services.ml import explain
from app.services.ml.dataset import DATASET_PATH, load_dataset, to_frame
from app.services.ml.model import METADATA_FILE, MODEL_FILE, ModelUnavailableError, TurnoverModel, load_model
from app.services.ml.training import build_pipeline

needs_dataset = pytest.mark.skipif(not DATASET_PATH.is_file(), reason="Dataset IBM absent")


def _copy(trained_model, tmp_path):
    directory, _ = trained_model
    target = tmp_path / "model"
    shutil.copytree(directory, target)
    return target


def test_missing_model_is_reported_unavailable(tmp_path):
    with pytest.raises(ModelUnavailableError, match="ml.train"):
        TurnoverModel.load(tmp_path)
    assert load_model(tmp_path) is None


def test_tampered_model_file_is_refused(trained_model, tmp_path):
    directory = _copy(trained_model, tmp_path)
    with (directory / MODEL_FILE).open("ab") as file:
        file.write(b"\x00")

    with pytest.raises(ModelUnavailableError, match="SHA-256"):
        TurnoverModel.load(directory)


@pytest.mark.parametrize(("field", "value", "message"), [
    ("sklearnVersion", "0.0.1", "scikit-learn"),
    ("features", ["Age"], "Variables"),
])
def test_incompatible_metadata_is_refused(trained_model, tmp_path, field, value, message):
    directory = _copy(trained_model, tmp_path)
    metadata = json.loads((directory / METADATA_FILE).read_text(encoding="utf-8"))
    metadata[field] = value
    (directory / METADATA_FILE).write_text(json.dumps(metadata), encoding="utf-8")

    with pytest.raises(ModelUnavailableError, match=message):
        TurnoverModel.load(directory)


def test_model_predictions_are_deterministic_and_match_the_report(trained_model):
    directory, report = trained_model
    model = TurnoverModel.load(directory)
    features, _ = load_dataset()

    for example in report["examples"].values():
        line = int(example["source"].split("ligne ")[1].split()[0])
        row = features.iloc[line - 2].to_dict()
        first, second = model.predict(row), model.predict(row)
        assert first == second
        assert first.probability == example["probability"]
        assert first.risk_level == example["riskLevel"]
        assert 1 <= len(first.factors) <= 5
        impacts = [abs(f.impact) for f in first.factors]
        assert impacts == sorted(impacts, reverse=True)


@needs_dataset
def test_linear_contributions_decompose_the_logit_exactly():
    features, target = load_dataset()
    pipeline = build_pipeline(LogisticRegression(max_iter=5000, random_state=0)).fit(features, target)
    background = explain.background_vector(pipeline, features)
    row = to_frame([features.iloc[10].to_dict()])

    contributions = explain.linear_contributions(pipeline, background, row)

    probability = pipeline.predict_proba(row)[0, 1]
    model = pipeline.named_steps["model"]
    logit_at_mean = float(model.coef_[0] @ background + model.intercept_[0])
    assert set(contributions) == set(features.columns)
    assert sum(contributions.values()) == pytest.approx(math.log(probability / (1 - probability)) - logit_at_mean)


@needs_dataset
def test_occlusion_contribution_is_the_probability_change_when_a_variable_takes_its_reference_value():
    features, target = load_dataset()
    pipeline = build_pipeline(RandomForestClassifier(n_estimators=50, random_state=0)).fit(features, target)
    reference = explain.reference_row(features)
    raw = features.iloc[3].to_dict()

    contributions = explain.occlusion_contributions(pipeline, reference, to_frame([raw]))

    base = pipeline.predict_proba(to_frame([raw]))[0, 1]
    changed = pipeline.predict_proba(to_frame([raw | {"OverTime": reference["OverTime"]}]))[0, 1]
    assert contributions["OverTime"] == pytest.approx(base - changed)
    assert reference["OverTime"] == "No" and reference["Department"] == "Research & Development"
