import pytest

from app import SERVICE_NAME, __version__
from app.core.security import API_KEY_HEADER, INVALID_KEY_MESSAGE
from tests.conftest import TEST_KEY, assert_api_error


def test_status_with_valid_key(client, auth_headers):
    response = client.get("/api/status", headers=auth_headers)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == SERVICE_NAME
    assert body["version"] == __version__
    # Le module ML (étape 11) n'est pas encore implémenté : il ne doit pas être annoncé.
    assert body["modules"] == {"cv_parser": True, "matching": True, "chatbot": True, "ml": False}


def test_status_without_key_is_refused(client):
    assert_api_error(client.get("/api/status"), 401, INVALID_KEY_MESSAGE)


@pytest.mark.parametrize("wrong_key", [
    "",
    "wrong-key-0123456789abcdef",
    TEST_KEY[:-1],
    TEST_KEY + "x",
    TEST_KEY.upper(),
    f" {TEST_KEY}x",
])
def test_status_with_wrong_key_is_refused(client, wrong_key):
    assert_api_error(client.get("/api/status", headers={API_KEY_HEADER: wrong_key}), 401, INVALID_KEY_MESSAGE)


def test_key_is_only_accepted_in_header(client):
    response = client.get("/api/status", params={API_KEY_HEADER: TEST_KEY})

    assert_api_error(response, 401)


def test_bearer_token_is_not_accepted_instead_of_internal_key(client):
    response = client.get("/api/status", headers={"Authorization": f"Bearer {TEST_KEY}"})

    assert_api_error(response, 401)


def test_every_internal_route_requires_the_key(app, client):
    """Garde-fou pour les modules futurs : toute route /api/** doit refuser un appel sans clé."""
    internal_paths = {path: ops for path, ops in app.openapi()["paths"].items() if path.startswith("/api/")}
    assert internal_paths, "aucune route interne trouvée"

    for path, operations in internal_paths.items():
        for method in operations:
            response = client.request(method.upper(), path.replace("{", "").replace("}", ""))
            assert response.status_code == 401, f"{method.upper()} {path} accessible sans clé"


def test_refused_call_does_not_log_the_key(client, caplog):
    client.get("/api/status", headers={API_KEY_HEADER: "leaked-candidate-key-123456"})

    assert "Appel interne refusé" in caplog.text
    assert "leaked-candidate-key-123456" not in caplog.text
    assert TEST_KEY not in caplog.text
