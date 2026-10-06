from app import SERVICE_NAME, __version__


def test_health_is_public_and_minimal(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": SERVICE_NAME, "version": __version__}


def test_request_id_from_backend_is_echoed(client):
    response = client.get("/health", headers={"X-Request-Id": "spring-1234_abc"})

    assert response.headers["X-Request-Id"] == "spring-1234_abc"


def test_invalid_request_id_is_replaced(client):
    response = client.get("/health", headers={"X-Request-Id": "bad id\twith spaces"})

    request_id = response.headers["X-Request-Id"]
    assert request_id != "bad id\twith spaces"
    assert len(request_id) == 32


def test_server_header_is_not_exposed_by_app(client):
    assert "x-powered-by" not in {name.lower() for name in client.get("/health").headers}
