"""Sanitation : ne conserve que les informations professionnelles anonymisées.

Refuse toute tentative d'injecter des champs d'identité (nom, email, téléphone, etc.).
Le PDF et le texte brut du CV ne sont jamais acceptés.
"""

from __future__ import annotations

from app.schemas.llm import (
    LlmEducation,
    LlmExperience,
    LlmLanguage,
    ProfessionalSummaryRequest,
)
from app.services.cv_parser.text import fold

FORBIDDEN_KEYS = frozenset({
    "firstName", "lastName", "first_name", "last_name", "name", "fullName", "full_name",
    "email", "phone", "telephone", "mobile", "address", "adresse", "location",
    "userId", "user_id", "candidateId", "candidate_id", "cvId", "cv_id", "id",
    "linkedin", "github", "url", "website", "pdf", "text", "rawText", "raw_text",
    "extractedText", "extracted_text", "content", "fileName", "originalFileName",
})


def assert_no_identity_fields(raw: dict) -> None:
    """Lève ValueError si un champ d'identité est présent à la racine."""
    for key in raw:
        if key in FORBIDDEN_KEYS or fold(str(key)) in {fold(k) for k in FORBIDDEN_KEYS}:
            raise ValueError(f"Champ d'identité interdit : {key}")


def sanitize(request: ProfessionalSummaryRequest) -> dict:
    """Produit le dictionnaire strictement professionnel transmis au prompt builder."""
    payload = {
        "skills": list(dict.fromkeys(request.skills)),
        "technologies": list(dict.fromkeys(request.technologies)),
        "educations": [_education(item) for item in request.educations],
        "experiences": [_experience(item) for item in request.experiences],
        "languages": [_language(item) for item in request.languages],
    }
    if request.total_experience_months is not None:
        payload["totalExperienceMonths"] = request.total_experience_months
    if request.highest_education_level:
        payload["highestEducationLevel"] = request.highest_education_level
    return payload


def has_usable_content(payload: dict) -> bool:
    return bool(
        payload.get("skills")
        or payload.get("technologies")
        or payload.get("educations")
        or payload.get("experiences")
        or payload.get("languages")
    )


def allowed_keyword_tokens(payload: dict) -> set[str]:
    """Termes professionnels autorisés pour les mots-clés (comparaison repliée).

    Les libellés trop longs (phrases d'injection éventuelles) sont enregistrés tels quels
    pour une égalité exacte, mais leurs mots isolés ne deviennent pas un vocabulaire libre.
    """
    tokens: set[str] = set()
    for name in payload.get("skills", []) + payload.get("technologies", []):
        _add_label(tokens, name)
    for education in payload.get("educations", []):
        for field in ("degree", "fieldOfStudy", "institution", "level"):
            _add_label(tokens, education.get(field))
    for experience in payload.get("experiences", []):
        for field in ("jobTitle", "company"):
            _add_label(tokens, experience.get(field))
    for language in payload.get("languages", []):
        for field in ("name", "level", "cefr"):
            _add_label(tokens, language.get(field))
    _add_label(tokens, payload.get("highestEducationLevel"))
    return {token for token in tokens if token}


def _add_label(tokens: set[str], value: object) -> None:
    if not value or not isinstance(value, str):
        return
    text = " ".join(value.split()).strip()
    if not text:
        return
    tokens.add(fold(text))


def _education(item: LlmEducation) -> dict:
    data = {"degree": item.degree.strip()}
    if item.field_of_study:
        data["fieldOfStudy"] = item.field_of_study.strip()
    if item.institution:
        data["institution"] = item.institution.strip()
    if item.level:
        data["level"] = item.level.strip()
    return data


def _experience(item: LlmExperience) -> dict:
    data = {"jobTitle": item.job_title.strip()}
    if item.company:
        data["company"] = item.company.strip()
    if item.duration_months is not None:
        data["durationMonths"] = item.duration_months
    return data


def _language(item: LlmLanguage) -> dict:
    data = {"name": item.name.strip()}
    if item.level:
        data["level"] = item.level.strip()
    if item.cefr:
        data["cefr"] = item.cefr.strip()
    return data
