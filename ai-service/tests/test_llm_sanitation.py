import pytest
from pydantic import ValidationError

from app.schemas.llm import ProfessionalSummaryRequest
from app.services.llm.sanitation import (
    assert_no_identity_fields,
    allowed_keyword_tokens,
    has_usable_content,
    sanitize,
)


def test_sanitize_keeps_only_professional_fields():
    request = ProfessionalSummaryRequest.model_validate({
        "skills": ["API REST"],
        "technologies": ["Java", "Spring Boot"],
        "educations": [{"degree": "Master", "fieldOfStudy": "Informatique", "level": "BAC_PLUS_5"}],
        "experiences": [{"jobTitle": "Développeur", "company": "Demo SA", "durationMonths": 24}],
        "languages": [{"name": "Anglais", "cefr": "B2"}],
        "totalExperienceMonths": 24,
        "highestEducationLevel": "BAC_PLUS_5",
    })
    payload = sanitize(request)
    assert payload["technologies"] == ["Java", "Spring Boot"]
    assert "email" not in payload
    assert "text" not in payload
    assert has_usable_content(payload)
    tokens = allowed_keyword_tokens(payload)
    assert "java" in tokens
    assert "spring boot" in tokens


def test_identity_fields_are_forbidden_in_raw_dict():
    with pytest.raises(ValueError, match="identité"):
        assert_no_identity_fields({"skills": ["Java"], "email": "x@y.z"})


def test_request_schema_rejects_identity_extra_fields():
    with pytest.raises(ValidationError):
        ProfessionalSummaryRequest.model_validate({
            "skills": ["Java"],
            "email": "secret@example.com",
        })


def test_empty_profile_is_detected():
    payload = sanitize(ProfessionalSummaryRequest())
    assert not has_usable_content(payload)
