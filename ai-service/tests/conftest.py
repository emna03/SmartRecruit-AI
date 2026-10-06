import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.core.security import API_KEY_HEADER
from app.main import create_app

# Valeur réservée aux tests automatisés, sans usage réel.
TEST_KEY = "test-only-internal-key-0123456789"


@pytest.fixture(autouse=True)
def isolated_environment(monkeypatch):
    """Les tests ne dépendent ni du fichier .env ni des variables AI_* de la machine."""
    for name in ("AI_INTERNAL_API_KEY", "AI_SERVICE_URL", "AI_SERVICE_HOST", "AI_SERVICE_PORT",
                 "AI_LOG_LEVEL", "AI_DOCS_ENABLED"):
        monkeypatch.delenv(name, raising=False)


def make_settings(**overrides) -> Settings:
    values = {"ai_internal_api_key": TEST_KEY} | overrides
    return Settings(_env_file=None, **values)


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
