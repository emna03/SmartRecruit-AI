import json

import pytest

from app.services.llm.errors import LlmError
from app.services.llm.validation import filter_keywords, parse_and_validate

PAYLOAD = {
    "skills": ["API REST"],
    "technologies": ["Java", "Spring Boot", "Angular"],
    "educations": [{"degree": "Master informatique", "level": "BAC_PLUS_5"}],
    "experiences": [{"jobTitle": "Développeur backend", "durationMonths": 24}],
    "languages": [{"name": "Anglais", "cefr": "B2"}],
}


def test_valid_response_is_accepted():
    raw = json.dumps({
        "summary": "Profil backend Java Spring Boot avec une pratique d'API REST et Angular, "
                   "issu d'un Master informatique et deux ans d'expérience.",
        "keywords": ["Java", "Spring Boot", "Angular", "API REST"],
    })
    result = parse_and_validate(raw, PAYLOAD, "gemini-3.5-flash-lite")
    assert result.provider == "gemini"
    assert "Kubernetes" not in result.keywords
    assert result.keywords[0] == "Java"


def test_hallucinated_keyword_is_filtered():
    raw = json.dumps({
        "summary": "Profil Java Spring Boot Angular avec API REST et une solide expérience backend fictive.",
        "keywords": ["Java", "Spring Boot", "Kubernetes"],
    })
    result = parse_and_validate(raw, PAYLOAD, "gemini-3.5-flash-lite")
    assert "Kubernetes" not in result.keywords
    assert "Java" in result.keywords


def test_majority_hallucination_is_rejected():
    raw = json.dumps({
        "summary": "Profil inventé avec des technologies absentes des données fournies pour le test.",
        "keywords": ["Kubernetes", "AWS", "Docker", "Java"],
    })
    with pytest.raises(LlmError) as caught:
        parse_and_validate(raw, PAYLOAD, "gemini-3.5-flash-lite")
    assert caught.value.code == "LLM_HALLUCINATION"
    assert caught.value.status_code == 422


def test_invalid_json_is_rejected():
    with pytest.raises(LlmError) as caught:
        parse_and_validate("{not-json", PAYLOAD, "gemini-3.5-flash-lite")
    assert caught.value.code == "LLM_INVALID_JSON"


def test_filter_keywords_helpers():
    accepted, rejected = filter_keywords(["Java", "Kubernetes", "Spring Boot"], {"java", "spring boot"})
    assert accepted == ["Java", "Spring Boot"]
    assert rejected == ["Kubernetes"]
