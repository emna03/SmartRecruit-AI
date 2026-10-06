"""Correspondance des compétences : normalisation, correspondances exactes, implicites, proches."""

import json

import pytest

from app.services.cv_parser.lexicon import default_lexicon
from app.services.matching.skills import EXACT, IMPLIED, RELATED, RULES_PATH, default_rules, match_skills


def kinds(outcome):
    return {m.name: (m.kind, m.matched_by) for m in outcome.matched}


def test_lexicon_resolves_aliases_to_reference_names():
    lexicon = default_lexicon()
    assert lexicon.canonical("springboot") == "Spring Boot"
    assert lexicon.canonical("k8s") == "Kubernetes"
    assert lexicon.canonical("  postgres ") == "PostgreSQL"
    assert lexicon.canonical("Go") == "Go"
    assert lexicon.canonical("COBOL") is None


@pytest.mark.parametrize("required, owned", [
    ("Spring Boot", "spring-boot"),
    ("Kubernetes", "k8s"),
    ("JavaScript", "JS"),
    ("Développement web", "developpement WEB"),
    ("COBOL", "cobol"),  # hors lexique : comparaison normalisée du libellé
])
def test_exact_match_ignores_case_accents_separators_and_aliases(required, owned):
    outcome = match_skills([required], [owned])

    assert outcome.matched[0].kind == EXACT
    assert outcome.missing == []
    assert outcome.score == 100.0


def test_required_skill_implied_by_a_more_specific_one():
    outcome = match_skills(["SQL", "Java", "JavaScript"], ["MySQL", "Spring Boot", "Angular"])

    assert kinds(outcome) == {"SQL": (IMPLIED, "MySQL"), "Java": (IMPLIED, "Spring Boot"),
                              "JavaScript": (IMPLIED, "Angular")}
    assert outcome.score == 100.0


def test_implication_is_transitive():
    # Angular => TypeScript => JavaScript ; PyTorch => Deep Learning => Machine Learning => IA
    outcome = match_skills(["JavaScript", "Intelligence artificielle"], ["Angular", "PyTorch"])

    assert [m.kind for m in outcome.matched] == [IMPLIED, IMPLIED]


def test_implication_is_one_way():
    outcome = match_skills(["Spring Boot", "MySQL"], ["Java", "SQL"])

    assert outcome.matched == []
    assert outcome.missing == ["Spring Boot", "MySQL"]


def test_technology_of_the_same_family_gives_partial_credit():
    outcome = match_skills(["MySQL", "React"], ["PostgreSQL", "Angular"])

    assert kinds(outcome) == {"MySQL": (RELATED, "PostgreSQL"), "React": (RELATED, "Angular")}
    assert outcome.score == 50.0


def test_exact_match_wins_over_implied_and_related():
    outcome = match_skills(["SQL", "MySQL"], ["SQL", "MySQL", "PostgreSQL"])

    assert [m.kind for m in outcome.matched] == [EXACT, EXACT]


def test_missing_skills_keep_the_offer_order_and_reference_names():
    outcome = match_skills(["Docker", "k8s", "Java"], ["Java"])

    assert outcome.missing == ["Docker", "Kubernetes"]
    assert outcome.score == pytest.approx(100 / 3)


def test_duplicate_required_skills_are_counted_once():
    outcome = match_skills(["Java", "java", " JAVA ", "Docker"], ["Java"])

    assert outcome.required_count == 2
    assert outcome.score == 50.0


def test_result_does_not_depend_on_candidate_skill_order():
    skills = ["Spring Boot", "PostgreSQL", "MariaDB", "Angular", "Docker"]
    required = ["SQL", "MySQL", "Java", "React", "Kubernetes"]

    first = match_skills(required, skills)
    second = match_skills(required, list(reversed(skills)))

    assert first == second


def test_every_rule_uses_a_reference_name_of_the_lexicon():
    data = json.loads(RULES_PATH.read_text(encoding="utf-8"))
    lexicon = default_lexicon()
    names = set(data["implies"]) | {t for targets in data["implies"].values() for t in targets}
    names |= {m for members in data["families"].values() for m in members}

    unknown = sorted(n for n in names if lexicon.canonical(n) != n)
    assert unknown == []
    assert default_rules().version == data["version"]
