import pytest
from fastapi.testclient import TestClient

from app.api.routes.ml import MODEL_UNAVAILABLE_MESSAGE
from app.core.security import API_KEY_HEADER
from app.main import create_app
from tests.conftest import TEST_KEY, assert_api_error, make_settings

URL = "/api/ml/turnover/predict"
HEADERS = {API_KEY_HEADER: TEST_KEY}


@pytest.fixture
def ml_client(trained_model):
    directory, _ = trained_model
    with TestClient(create_app(make_settings(ai_ml_model_dir=directory)), raise_server_exceptions=False) as client:
        yield client


@pytest.fixture
def examples(trained_model):
    return trained_model[1]["examples"]


def test_valid_prediction_returns_score_level_factors_and_real_model_metrics(ml_client, trained_model, examples):
    report = trained_model[1]
    response = ml_client.post(URL, json=examples["highRisk"]["features"], headers=HEADERS)

    assert response.status_code == 200
    body = response.json()
    assert 0 <= body["probability"] <= 1
    assert body["riskLevel"] in {"FAIBLE", "MOYEN", "ELEVE"}
    assert (body["mediumThreshold"], body["highThreshold"]) == (0.3, 0.5)
    assert 1 <= len(body["factors"]) <= 5
    for factor in body["factors"]:
        assert factor["direction"] == ("INCREASES_RISK" if factor["impact"] > 0 else "DECREASES_RISK")
        assert factor["label"]
    chosen = report["selection"]["chosenModel"]
    metrics = report["test"]["results"][chosen]
    assert body["model"] == {
        "name": chosen, "version": report["modelVersion"],
        "dataset": "IBM HR Analytics Employee Attrition & Performance",
        "explanationMethod": report["explanation"], "testAccuracy": metrics["accuracy"],
        "testPrecision": metrics["precision"], "testRecall": metrics["recall"], "testF1": metrics["f1"],
        "testRocAuc": metrics["rocAuc"],
    }


def test_low_risk_employee_of_the_test_set(ml_client, examples):
    body = ml_client.post(URL, json=examples["lowRisk"]["features"], headers=HEADERS).json()

    assert body["riskLevel"] == "FAIBLE"
    assert body["probability"] == examples["lowRisk"]["probability"] < 0.3


def test_high_risk_employee_of_the_test_set(ml_client, examples):
    body = ml_client.post(URL, json=examples["highRisk"]["features"], headers=HEADERS).json()

    assert body["riskLevel"] == "ELEVE"
    assert body["probability"] == examples["highRisk"]["probability"] >= 0.5
    assert {"feature": "overTime", "direction": "INCREASES_RISK"}.items() <= next(
        f for f in body["factors"] if f["feature"] == "overTime").items()


def test_occlusion_factors_give_the_reference_value_in_the_request_format(ml_client, trained_model, examples):
    if trained_model[1]["explanation"] != "OCCLUSION":
        pytest.skip("modèle linéaire : pas de valeur de référence")
    body = ml_client.post(URL, json=examples["highRisk"]["features"], headers=HEADERS).json()

    references = {f["feature"]: f["referenceValue"] for f in body["factors"]}
    assert references["overTime"] == "false"
    assert all(value is not None for value in references.values())


@pytest.mark.parametrize("missing", ["monthlyIncome", "overTime", "jobRole", "yearsAtCompany"])
def test_missing_field_is_refused(ml_client, examples, missing):
    payload = dict(examples["lowRisk"]["features"])
    del payload[missing]

    body = assert_api_error(ml_client.post(URL, json=payload, headers=HEADERS), 400, "Données invalides")
    assert missing in body["fieldErrors"]


def test_empty_body_is_refused(ml_client):
    assert_api_error(ml_client.post(URL, json={}, headers=HEADERS), 400, "Données invalides")


@pytest.mark.parametrize(("field", "value"), [
    ("monthlyIncome", 500),
    ("monthlyIncome", 25000),
    ("monthlyIncome", "5000"),
    ("jobSatisfaction", 5),
    ("jobSatisfaction", 0),
    ("education", 2.5),
    ("performanceRating", 2),
    ("distanceFromHome", -1),
    ("overTime", "yes"),
    ("overTime", 1),
    ("businessTravel", "SOMETIMES"),
    ("jobRole", "CEO"),
    ("department", None),
])
def test_invalid_values_are_refused(ml_client, examples, field, value):
    payload = dict(examples["lowRisk"]["features"]) | {field: value}

    body = assert_api_error(ml_client.post(URL, json=payload, headers=HEADERS), 400, "Données invalides")
    assert field in body["fieldErrors"]


@pytest.mark.parametrize(("changes", "message"), [
    ({"totalWorkingYears": 3, "yearsAtCompany": 7}, "expérience totale"),
    ({"yearsInCurrentRole": 8}, "poste actuel"),
    ({"yearsWithCurrManager": 8}, "manager actuel"),
    ({"yearsSinceLastPromotion": 8}, "dernière promotion"),
    ({"department": "SALES", "jobRole": "RESEARCH_SCIENTIST"}, "département"),
])
def test_inconsistent_values_are_refused(ml_client, examples, changes, message):
    payload = dict(examples["lowRisk"]["features"]) | changes

    body = assert_api_error(ml_client.post(URL, json=payload, headers=HEADERS), 400, "Données invalides")
    assert message in body["fieldErrors"]["body"]


@pytest.mark.parametrize("extra", [{"age": 30}, {"gender": "Female"}, {"maritalStatus": "Single"},
                                   {"employeeName": "Alice"}])
def test_sensitive_or_unknown_fields_are_refused(ml_client, examples, extra):
    payload = dict(examples["lowRisk"]["features"]) | extra

    assert_api_error(ml_client.post(URL, json=payload, headers=HEADERS), 400, "Données invalides")


def test_prediction_requires_the_internal_key(ml_client, examples):
    assert_api_error(ml_client.post(URL, json=examples["lowRisk"]["features"]), 401)
    assert_api_error(ml_client.post(URL, json=examples["lowRisk"]["features"],
                                    headers={API_KEY_HEADER: "wrong-key-0123456789abcdef"}), 401)


def test_without_trained_model_the_route_answers_503_and_status_hides_the_module(client, auth_headers, examples):
    assert_api_error(client.post(URL, json=examples["lowRisk"]["features"], headers=auth_headers), 503,
                     MODEL_UNAVAILABLE_MESSAGE)
    assert client.get("/api/status", headers=auth_headers).json()["modules"]["ml"] is False


def test_status_announces_the_ml_module_when_the_model_is_loaded(ml_client):
    assert ml_client.get("/api/status", headers=HEADERS).json()["modules"] == {
        "cv_parser": True, "matching": True, "chatbot": True, "ml": True, "llm": False,
        "semantic": True, "clustering": False,
    }


def test_logs_contain_the_score_but_never_the_employee_data(ml_client, examples, caplog):
    payload = dict(examples["highRisk"]["features"]) | {"monthlyIncome": 12345}

    ml_client.post(URL, json=payload, headers=HEADERS)

    assert "Prédiction turnover : score" in caplog.text
    assert "12345" not in caplog.text
    assert "SALES_REPRESENTATIVE" not in caplog.text
