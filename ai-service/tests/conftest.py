from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.core.security import API_KEY_HEADER
from app.main import create_app

# Valeur réservée aux tests automatisés, sans usage réel.
TEST_KEY = "test-only-internal-key-0123456789"
# Par défaut, aucun modèle ML : les tests ne dépendent pas du modèle entraîné localement (ml/artifacts).
NO_MODEL_DIR = Path(__file__).parent / "no-ml-model"


@pytest.fixture(autouse=True)
def isolated_environment(monkeypatch):
    """Les tests ne dépendent ni du fichier .env ni des variables AI_* de la machine."""
    for name in ("AI_INTERNAL_API_KEY", "AI_SERVICE_URL", "AI_SERVICE_HOST", "AI_SERVICE_PORT",
                 "AI_LOG_LEVEL", "AI_DOCS_ENABLED", "MAX_CV_SIZE_MB", "AI_CV_MAX_PAGES", "AI_ML_MODEL_DIR",
                 "LLM_PROVIDER", "LLM_API_KEY", "LLM_MODEL", "LLM_TIMEOUT_SECONDS"):
        monkeypatch.delenv(name, raising=False)


def make_settings(**overrides) -> Settings:
    values = {"ai_internal_api_key": TEST_KEY, "ai_ml_model_dir": NO_MODEL_DIR} | overrides
    return Settings(_env_file=None, **values)


@pytest.fixture(scope="session")
def trained_model(tmp_path_factory):
    """Pipeline ML complet exécuté une fois sur le vrai dataset : (dossier du modèle, rapport obtenu)."""
    from app.services.ml.dataset import DATASET_PATH
    from app.services.ml.training import train

    if not DATASET_PATH.is_file():
        pytest.skip("Dataset IBM absent : lancer python -m ml.download_dataset")
    directory = tmp_path_factory.mktemp("ml-model")
    report = train(artifacts_dir=directory, report_path=directory / "report.json")
    return directory, report


@pytest.fixture
def app():
    return create_app(make_settings())


@pytest.fixture
def client(app):
    with TestClient(app, raise_server_exceptions=False) as test_client:
        yield test_client


@pytest.fixture
def auth_headers():
    return {API_KEY_HEADER: TEST_KEY}


def assert_api_error(response, status: int, message: str | None = None):
    """Vérifie le format d'erreur commun avec l'ApiError de Spring Boot."""
    assert response.status_code == status
    body = response.json()
    assert {"timestamp", "status", "error", "message", "path"} <= body.keys()
    assert body["status"] == status
    assert body["path"] == response.request.url.path
    if message is not None:
        assert body["message"] == message
    return body
