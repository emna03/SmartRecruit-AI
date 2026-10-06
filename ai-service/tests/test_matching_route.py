"""Route interne POST /api/matching/score (appelée uniquement par Spring Boot avec la clé interne)."""

import logging

from tests.conftest import assert_api_error

URL = "/api/matching/score"
BODY = {
    "candidate": {"skills": ["Java", "Spring Boot", "PostgreSQL"], "experienceMonths": 30,
                  "educationLevel": "BAC_PLUS_5"},
    "offer": {"requiredSkills": ["Java", "MySQL", "Docker"], "minExperienceYears": 2, "educationLevel": "BAC_PLUS_3"},
}


def test_score_requires_the_internal_key(client):
    assert_api_error(client.post(URL, json=BODY), 401)


def test_score_returns_explainable_camel_case_json(client, auth_headers):
    response = client.post(URL, json=BODY, headers=auth_headers)

    assert response.status_code == 200
    body = response.json()
    assert body["algorithmVersion"] == "1.0.0"
    assert body["skillsScore"] == 50.0  # Java exact, MySQL proche de PostgreSQL (0,5), Docker manquant
    assert body["experienceScore"] == 100.0
    assert body["educationScore"] == 100.0
    assert body["score"] == 70.0
    assert body["weights"] == {"skills": 0.6, "experience": 0.25, "education": 0.15}
    assert body["matchedSkills"] == [
        {"name": "Java", "matchedBy": "Java", "matchType": "EXACT", "credit": 1.0},
        {"name": "MySQL", "matchedBy": "PostgreSQL", "matchType": "RELATED", "credit": 0.5},
    ]
    assert body["missingSkills"] == ["Docker"]
    assert body["explanation"].startswith("Score global : 70 %")


def test_offer_without_required_skill_is_refused(client, auth_headers):
    body = {**BODY, "offer": {**BODY["offer"], "requiredSkills": []}}

    assert_api_error(client.post(URL, json=body, headers=auth_headers), 400)


def test_invalid_values_are_refused(client, auth_headers):
    for candidate in ({**BODY["candidate"], "experienceMonths": -1},
                      {**BODY["candidate"], "educationLevel": "MASTER"},
                      {**BODY["candidate"], "skills": [""]}):
        response = client.post(URL, json={**BODY, "candidate": candidate}, headers=auth_headers)
        assert_api_error(response, 400)


def test_personal_or_unexpected_fields_are_refused(client, auth_headers):
    body = {**BODY, "candidate": {**BODY["candidate"], "email": "camille@example.com"}}

    assert_api_error(client.post(URL, json=body, headers=auth_headers), 400)


def test_logs_contain_only_counts_and_score(client, auth_headers, caplog):
    with caplog.at_level(logging.INFO, logger="smartrecruit_ai"):
        client.post(URL, json=BODY, headers=auth_headers)

    messages = " ".join(r.getMessage() for r in caplog.records)
    assert "Matching calculé : score 70.0, 2/3 compétences, version 1.0.0" in messages
    for value in ("Java", "PostgreSQL", "Docker"):
        assert value not in messages
