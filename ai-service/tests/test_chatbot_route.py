"""Route interne POST /api/chatbot/ask (appelée uniquement par Spring Boot avec la clé interne)."""

import logging

from tests.chatbot_data import OFFERS_JSON
from tests.conftest import assert_api_error

URL = "/api/chatbot/ask"
BODY = {"message": "Quelles offres correspondent à Java ?", "contextOfferId": None, "offers": OFFERS_JSON}


def test_ask_requires_the_internal_key(client):
    assert_api_error(client.post(URL, json=BODY), 401)
    assert_api_error(client.post(URL, json=BODY, headers={"X-Internal-Api-Key": "wrong-key-0123456789"}), 401)


def test_ask_returns_camel_case_answer_based_on_the_catalogue(client, auth_headers):
    response = client.post(URL, json=BODY, headers=auth_headers)

    assert response.status_code == 200
    body = response.json()
    assert body["chatbotVersion"] == "1.0.0"
    assert body["intent"] == "SEARCH_OFFERS"
    assert body["offerIds"] == [11, 14]
    assert body["totalMatches"] == 2
    assert body["contextOfferId"] is None
    assert body["entities"]["skills"] == ["Java"]
    assert body["answer"].startswith("J'ai trouvé 2 offres publiées qui demandent Java.")
    assert 0 <= body["confidence"] <= 1


def test_empty_or_blank_question_is_refused(client, auth_headers):
    for message in ("", "   ", "\n\t"):
        assert_api_error(client.post(URL, json={**BODY, "message": message}, headers=auth_headers), 400)


def test_too_long_question_is_refused(client, auth_headers):
    assert_api_error(client.post(URL, json={**BODY, "message": "a" * 501}, headers=auth_headers), 400)


def test_context_offer_must_belong_to_the_catalogue(client, auth_headers):
    body = {**BODY, "contextOfferId": 999}

    assert_api_error(client.post(URL, json=body, headers=auth_headers), 400)


def test_duplicate_offers_are_refused(client, auth_headers):
    body = {**BODY, "offers": [OFFERS_JSON[0], OFFERS_JSON[0]]}

    assert_api_error(client.post(URL, json=body, headers=auth_headers), 400)


def test_personal_or_unexpected_fields_are_refused(client, auth_headers):
    for body in ({**BODY, "email": "camille@example.com"},
                 {**BODY, "offers": [{**OFFERS_JSON[0], "recruiterEmail": "bob@example.com"}]}):
        assert_api_error(client.post(URL, json=body, headers=auth_headers), 400)


def test_logs_never_contain_the_question(client, auth_headers, caplog):
    body = {**BODY, "message": "Je m'appelle Camille Dupont, quelles offres correspondent à Java ?"}
    with caplog.at_level(logging.INFO, logger="smartrecruit_ai"):
        client.post(URL, json=body, headers=auth_headers)

    messages = " ".join(r.getMessage() for r in caplog.records)
    assert "Chatbot : intention SEARCH_OFFERS" in messages
    assert "4 offres reçues, 2 citées, version 1.0.0" in messages
    for value in ("Camille", "Dupont", "Java", "appelle"):
        assert value not in messages
