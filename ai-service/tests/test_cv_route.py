"""Route interne POST /api/cv/parse (appelée uniquement par Spring Boot avec la clé interne)."""

from pathlib import Path

from fastapi.testclient import TestClient

from app.main import create_app
from tests.conftest import assert_api_error, make_settings

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "cv"
PDF = {"Content-Type": "application/pdf"}
URL = "/api/cv/parse"


def fixture(name: str) -> bytes:
    return (FIXTURES / name).read_bytes()


def test_parse_requires_the_internal_key(client):
    response = client.post(URL, content=fixture("cv_dev_fullstack_fr.pdf"), headers=PDF)

    assert_api_error(response, 401)


def test_parse_returns_structured_camel_case_json(client, auth_headers):
    response = client.post(URL, content=fixture("cv_dev_fullstack_fr.pdf"), headers=auth_headers | PDF)

    assert response.status_code == 200
    body = response.json()
    assert {"parserVersion", "method", "skills", "technologies", "educations", "experiences", "languages",
            "totalExperienceMonths", "highestEducationLevel", "warnings", "text"} <= body.keys()
    assert {"Java", "Spring Boot", "Angular", "MySQL"} <= {t["name"] for t in body["technologies"]}
    assert body["educations"][0]["fieldOfStudy"] == "Génie Logiciel"
    assert body["experiences"][0]["jobTitle"] == "Développeuse Full Stack"
    assert "LLM" in body["method"]


def test_parse_refuses_other_media_types(client, auth_headers):
    response = client.post(URL, content=b"%PDF-1.4", headers=auth_headers | {"Content-Type": "text/plain"})

    assert_api_error(response, 415, "Le CV doit être envoyé au format PDF.")


def test_parse_refuses_empty_body(client, auth_headers):
    assert_api_error(client.post(URL, content=b"", headers=auth_headers | PDF), 400, "Le fichier est vide.")


def test_parse_checks_the_content_not_the_declared_type(client, auth_headers):
    response = client.post(URL, content=fixture("not_a_pdf.pdf"), headers=auth_headers | PDF)

    assert_api_error(response, 422, "Le fichier n'est pas un PDF valide ou il est endommagé.")


def test_parse_reports_pdf_without_text(client, auth_headers):
    response = client.post(URL, content=fixture("cv_image_only.pdf"), headers=auth_headers | PDF)

    body = assert_api_error(response, 422)
    assert "texte exploitable" in body["message"]


def test_parse_reports_encrypted_pdf(client, auth_headers):
    response = client.post(URL, content=fixture("cv_encrypted.pdf"), headers=auth_headers | PDF)

    body = assert_api_error(response, 422)
    assert "mot de passe" in body["message"]


def test_parse_refuses_files_over_the_size_limit(auth_headers):
    app = create_app(make_settings(max_cv_size_mb=1))
    oversized = b"%PDF-1.4\n" + b"0" * (1024 * 1024)
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(URL, content=oversized, headers=auth_headers | PDF)

        streamed = client.post(URL, content=iter([oversized[:600_000], oversized[600_000:]]),
                               headers=auth_headers | PDF)

    assert_api_error(response, 413, "Le fichier dépasse la taille maximale de 1 Mo.")
    assert_api_error(streamed, 413)


def test_parse_never_logs_cv_content(client, auth_headers, caplog):
    caplog.set_level("INFO")

    client.post(URL, content=fixture("cv_dev_fullstack_fr.pdf"), headers=auth_headers | PDF)

    assert "CV analysé" in caplog.text
    for secret in ("Camille", "camille.exemple", "Société Exemple", "Génie Logiciel", "+216"):
        assert secret not in caplog.text
