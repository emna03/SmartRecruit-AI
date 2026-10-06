import pytest

from app.services.cv_parser.sections import HEADER, lines_of, match_title, split_sections


@pytest.mark.parametrize(("title", "key"), [
    ("EXPÉRIENCE PROFESSIONNELLE", "experience"),
    ("Work Experience", "experience"),
    ("Formation académique", "formation"),
    ("EDUCATION", "formation"),
    ("Compétences techniques :", "competences"),
    ("Skills", "competences"),
    ("Langues", "langues"),
    ("Centres d’intérêt", "interets"),
    ("Experiances profesionnelles", "experience"),
    ("Compétence techniques", "competences"),
])
def test_match_title_exact_and_approximate(title, key):
    assert match_title(title) == key


@pytest.mark.parametrize("line", [
    "Développement d'API REST avec Spring Boot et Angular",
    "Formation 2019",
    "Profil junior",
    "Java, Python",
])
def test_match_title_rejects_ordinary_lines(line):
    assert match_title(line) is None


def test_split_sections_groups_lines_under_their_title():
    lines = ["Camille Exemple", "Développeuse", "PROFIL", "Passionnée", "EXPÉRIENCE", "Stage — Démo | 2022",
             "- Java", "Langues : Français, Anglais (B2)", "COMPÉTENCES", "Java, SQL"]

    sections = split_sections(lines)

    assert [s.key for s in sections] == [HEADER, "profil", "experience", "langues", "competences"]
    assert lines_of(sections, HEADER) == ["Camille Exemple", "Développeuse"]
    assert lines_of(sections, "experience") == ["Stage — Démo | 2022", "- Java"]
    assert lines_of(sections, "langues") == ["Français, Anglais (B2)"]
    assert lines_of(sections, "competences") == ["Java, SQL"]


def test_inline_label_requires_an_exact_title_and_bullets_are_never_titles():
    sections = split_sections(["Langages : Java, Python", "- Langues"])

    assert [s.key for s in sections] == [HEADER]
