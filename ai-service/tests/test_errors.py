from fastapi.testclient import TestClient
from pydantic import BaseModel, Field

from app.main import create_app
from tests.conftest import assert_api_error, make_settings


class EchoRequest(BaseModel):
    text: str = Field(min_length=3)
    count: int = Field(ge=1)


def _client_with_test_routes() -> TestClient:
    app = create_app(make_settings())

    @app.post("/test/echo")
    def echo(payload: EchoRequest) -> EchoRequest:
        return payload

    @app.get("/test/boom")
    def boom():
        raise RuntimeError("détail interne secret : C:\\chemin\\cv.pdf")

    return TestClient(app, raise_server_exceptions=False)


def test_unknown_route_returns_api_error(client, auth_headers):
    assert_api_error(client.get("/does-not-exist"), 404, "Ressource introuvable")
    assert_api_error(client.get("/api/does-not-exist", headers=auth_headers), 404, "Ressource introuvable")


def test_wrong_method_returns_api_error(client):
    body = assert_api_error(client.post("/health"), 405, "Méthode non autorisée")
    assert body["error"] == "Method Not Allowed"


def test_validation_error_lists_fields():
    body = assert_api_error(_client_with_test_routes().post("/test/echo", json={"text": "a", "count": 0}),
                            400, "Données invalides")

    assert set(body["fieldErrors"]) == {"text", "count"}


def test_malformed_json_is_a_validation_error():
    response = _client_with_test_routes().post("/test/echo", content="{not json",
                                               headers={"Content-Type": "application/json"})

    assert_api_error(response, 400, "Données invalides")


def test_unexpected_error_hides_internal_details(caplog):
    response = _client_with_test_routes().get("/test/boom")

    assert_api_error(response, 500, "Erreur interne du service IA")
    assert "secret" not in response.text
    assert "Erreur inattendue sur GET /test/boom" in caplog.text


def test_docs_are_disabled_by_default(client):
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404


def test_docs_can_be_enabled():
    with TestClient(create_app(make_settings(ai_docs_enabled=True))) as client:
        assert client.get("/docs").status_code == 200
        paths = client.get("/openapi.json").json()["paths"]
        assert {"/health", "/api/status"} <= paths.keys()
