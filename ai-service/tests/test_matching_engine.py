"""Scénarios de matching sur les données réellement extraites des CV fictifs par le CV Parser (étape 8)."""

from datetime import date
from pathlib import Path

import pytest

from app.schemas.matching import MatchingRequest
from app.services.cv_parser.pipeline import parse_cv
from app.services.matching.engine import ALGORITHM_VERSION, compute_match
from app.services.matching.explanation import DISCLAIMER

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "cv"
REFERENCE_DATE = date(2026, 10, 6)
FULL_STACK_OFFER = {"requiredSkills": ["Java", "Spring Boot", "Angular", "MySQL", "Docker", "Kubernetes"],
                    "minExperienceYears": 3, "educationLevel": "BAC_PLUS_5"}
DEVOPS_OFFER = {"requiredSkills": ["Kubernetes", "Terraform", "Ansible"], "minExperienceYears": 0}


def candidate_from_cv(name: str) -> dict:
    """Ce que Spring Boot envoie : compétences, durée et niveau extraits du CV (aucune donnée d'identité)."""
    result = parse_cv((FIXTURES / name).read_bytes(), max_pages=10, today=REFERENCE_DATE)
    return {"skills": [s.name for s in result.technologies + result.skills],
            "experienceMonths": result.total_experience_months,
            "educationLevel": result.highest_education_level}


def match(candidate: dict, offer: dict):
    return compute_match(MatchingRequest.model_validate({"candidate": candidate, "offer": offer}))


def test_very_compatible_candidate():
    result = match(candidate_from_cv("cv_dev_fullstack_fr.pdf"), FULL_STACK_OFFER)

    assert (result.score, result.skills_score, result.experience_score, result.education_score) == \
           (90.0, 83.3, 100.0, 100.0)
    assert [m.name for m in result.matched_skills] == ["Java", "Spring Boot", "Angular", "MySQL", "Docker"]
    assert result.missing_skills == ["Kubernetes"]
    assert result.explanation.startswith("Score global : 90 % — compatibilité élevée.")
    assert "4 ans et 2 mois d'expérience détectée(s) dans le CV pour 3 ans demandé(s)" in result.explanation


def test_partially_compatible_candidate_with_a_related_technology():
    result = match(candidate_from_cv("cv_data_scientist_en.pdf"), FULL_STACK_OFFER)

    assert (result.score, result.skills_score, result.experience_score, result.education_score) == \
           (55.0, 25.0, 100.0, 100.0)
    assert {(m.name, m.match_type, m.matched_by, m.credit) for m in result.matched_skills} == \
           {("Docker", "EXACT", "Docker", 1.0), ("MySQL", "RELATED", "PostgreSQL", 0.5)}
    assert result.missing_skills == ["Java", "Spring Boot", "Angular", "Kubernetes"]
    assert "compatibilité partielle" in result.explanation
    assert "MySQL (le CV mentionne PostgreSQL)" in result.explanation


def test_weakly_compatible_candidate_with_insufficient_experience_and_education():
    result = match(candidate_from_cv("cv_minimal_fr.pdf"), FULL_STACK_OFFER)

    assert (result.score, result.skills_score, result.experience_score, result.education_score) == \
           (19.0, 16.7, 0.0, 60.0)
    assert "aucune expérience datée détectée dans le CV pour 3 ans demandé(s)" in result.explanation
    assert "niveau Bac+3 détecté pour Bac+5 demandé (1 niveau(x) en dessous)" in result.explanation
    assert "compatibilité faible" in result.explanation


def test_no_common_skill_gives_zero_when_skills_are_the_only_requirement():
    result = match(candidate_from_cv("cv_minimal_fr.pdf"), DEVOPS_OFFER)

    assert result.score == 0.0
    assert result.matched_skills == []
    assert result.missing_skills == ["Kubernetes", "Terraform", "Ansible"]
    assert (result.experience_score, result.education_score) == (None, None)
    assert (result.weights.skills, result.weights.experience, result.weights.education) == (1.0, None, None)
    assert "aucune des 3 compétences requises n'a été trouvée" in result.explanation
    assert "critère non pris en compte" in result.explanation


def test_insufficient_experience_lowers_only_the_experience_part():
    candidate = {"skills": ["Java", "Spring Boot"], "experienceMonths": 18, "educationLevel": "BAC_PLUS_5"}
    offer = {"requiredSkills": ["Java", "Spring Boot"], "minExperienceYears": 3, "educationLevel": "BAC_PLUS_5"}

    result = match(candidate, offer)

    assert result.experience_score == 50.0
    assert result.score == pytest.approx(0.6 * 100 + 0.25 * 50 + 0.15 * 100)
    assert "1 an et 6 mois d'expérience détectée(s) dans le CV pour 3 ans demandé(s) (en dessous" \
           in result.explanation


def test_insufficient_education_level():
    candidate = {"skills": ["Java"], "experienceMonths": 60, "educationLevel": "BAC_PLUS_2"}
    offer = {"requiredSkills": ["Java"], "minExperienceYears": 2, "educationLevel": "BAC_PLUS_5"}

    result = match(candidate, offer)

    assert result.education_score == 20.0
    assert result.score == pytest.approx(0.6 * 100 + 0.25 * 100 + 0.15 * 20)
    assert "niveau Bac+2 détecté pour Bac+5 demandé (2 niveau(x) en dessous)" in result.explanation


def test_unknown_education_level_gets_zero_on_that_criterion():
    result = match({"skills": ["Java"]}, {"requiredSkills": ["Java"], "educationLevel": "BAC_PLUS_3"})

    assert result.education_score == 0.0
    assert "aucun diplôme reconnu dans le CV" in result.explanation


def test_perfect_match_reaches_100():
    candidate = {"skills": ["Java", "Docker"], "experienceMonths": 120, "educationLevel": "DOCTORAT"}
    offer = {"requiredSkills": ["Java", "Docker"], "minExperienceYears": 5, "educationLevel": "BAC_PLUS_5"}

    assert match(candidate, offer).score == 100.0


def test_same_input_always_gives_the_same_result():
    candidate = candidate_from_cv("cv_data_scientist_en.pdf")

    results = {match(candidate, FULL_STACK_OFFER).model_dump_json() for _ in range(5)}

    assert len(results) == 1


def test_result_carries_version_and_disclaimer():
    result = match(candidate_from_cv("cv_dev_fullstack_fr.pdf"), FULL_STACK_OFFER)

    assert result.algorithm_version == ALGORITHM_VERSION
    assert result.explanation.endswith(DISCLAIMER)
    assert "décision de recrutement" in result.explanation
    assert result.required_skills_count == 6
