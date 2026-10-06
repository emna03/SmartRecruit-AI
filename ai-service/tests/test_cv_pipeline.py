"""Pipeline complet sur les CV PDF fictifs de tests/fixtures/cv (vérité terrain : expected.json)."""

import importlib.util
import json
import sys
from datetime import date
from pathlib import Path

import pytest

from app.schemas.cv import CvParseResponse
from app.services.cv_parser.errors import CvParsingError
from app.services.cv_parser.pipeline import parse_cv

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures" / "cv"
TRUTH = json.loads((FIXTURES / "expected.json").read_text(encoding="utf-8"))
TODAY = date.fromisoformat(TRUTH["referenceDate"])


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def parse(name: str) -> CvParseResponse:
    return parse_cv((FIXTURES / name).read_bytes(), max_pages=10, today=TODAY)


@pytest.mark.parametrize("name", sorted(TRUTH["parsed"]))
def test_fixture_matches_ground_truth(name):
    expected = TRUTH["parsed"][name]
    result = parse(name).model_dump(by_alias=True)

    assert {s["name"] for s in result["technologies"]} == set(expected["technologies"])
    assert {s["name"] for s in result["skills"]} == set(expected["skills"])
    assert [(e["level"], e["institution"], e["startDate"], e["endDate"]) for e in result["educations"]] == [
        (e["level"], e["institution"], e["startDate"], e["endDate"]) for e in expected["educations"]]
    assert [(x["jobTitle"], x["company"], x["startDate"], x["endDate"], x["current"])
            for x in result["experiences"]] == [
        (x["jobTitle"], x["company"], x["startDate"], x["endDate"], x["current"]) for x in expected["experiences"]]
    for field in ("documentLanguage", "highestEducationLevel", "totalExperienceMonths"):
        assert result[field] == expected[field], field


def test_full_french_cv_details():
    result = parse("cv_dev_fullstack_fr.pdf")

    assert result.page_count == 1
    assert result.sections_detected == ["profil", "experience", "formation", "competences", "langues", "interets"]
    assert result.warnings == []
    java = next(t for t in result.technologies if t.name == "Java")
    assert (java.category, java.occurrences) == ("Langage", 3)
    first = result.experiences[0]
    assert (first.location, first.duration_months) == ("Tunis", 44)
    assert first.description.splitlines() == [
        "Développement d'API REST avec Java 17 et Spring Boot, sécurisées par JWT.",
        "Création d'interfaces Angular et TypeScript, tests unitaires avec JUnit et Mockito.",
        "Déploiement avec Docker et Jenkins (intégration continue).",
    ]
    assert [(l.name, l.level, l.cefr, l.certification) for l in result.languages] == [
        ("Français", "NATIF", None, None), ("Anglais", "COURANT", "B2", "TOEIC 850"), ("Allemand", "DEBUTANT", None, None)]
    assert result.educations[0].field_of_study == "Génie Logiciel"


def test_minimal_cv_without_sections_reports_warnings():
    result = parse("cv_minimal_fr.pdf")

    assert result.sections_detected == []
    assert result.experiences == []
    assert result.educations[0].degree == "Licence professionnelle en développement web"
    assert len(result.warnings) == 2


def test_personal_data_is_masked_in_returned_text():
    text = parse("cv_dev_fullstack_fr.pdf").text

    assert "camille.exemple@example.com" not in text
    assert "+216 20 000 000" not in text
    assert "linkedin.com" not in text
    assert "[email] | [téléphone] | Tunis | [lien]" in text


def test_parsing_is_deterministic():
    data = (FIXTURES / "cv_data_scientist_en.pdf").read_bytes()

    first = parse_cv(data, 10, TODAY).model_dump(exclude={"processing_time_ms"})
    second = parse_cv(data, 10, TODAY).model_dump(exclude={"processing_time_ms"})

    assert first == second


@pytest.mark.parametrize(("name", "code"), sorted(TRUTH["rejected"].items()))
def test_unusable_files_are_rejected(name, code):
    with pytest.raises(CvParsingError) as error:
        parse(name)

    assert error.value.code == code
    assert error.value.status_code == 422


def test_empty_file_is_rejected():
    with pytest.raises(CvParsingError) as error:
        parse_cv(b"", 10, TODAY)

    assert (error.value.code, error.value.status_code) == ("EMPTY_FILE", 400)


def test_too_many_pages_is_rejected(tmp_path):
    generator = load_module("generate_cv_fixtures", FIXTURES / "generate_fixtures.py")
    path = tmp_path / "long.pdf"
    generator.write_pages(path, 4)

    with pytest.raises(CvParsingError) as error:
        parse_cv(path.read_bytes(), max_pages=3, today=TODAY)

    assert error.value.code == "TOO_MANY_PAGES"
    assert parse_cv(path.read_bytes(), max_pages=4, today=TODAY).page_count == 4


def test_fixture_generator_is_reproducible(tmp_path):
    generator = load_module("generate_cv_fixtures", FIXTURES / "generate_fixtures.py")
    path = tmp_path / "cv.pdf"
    generator.write_cv(path, generator.CV_DEV_FR)

    assert path.read_bytes() == (FIXTURES / "cv_dev_fullstack_fr.pdf").read_bytes()


def test_evaluation_scores_on_annotated_fixtures():
    evaluation = load_module("evaluate_cv_parser", ROOT / "scripts" / "evaluate_cv_parser.py")

    report = evaluation.evaluate()

    for field, metrics in report["fields"].items():
        assert metrics["precision"] == 1.0, field
        assert metrics["recall"] >= 0.85, field
    assert report["rejected"] == {"correct": 5, "total": 5}
