import json

import pytest

from app.services.cv_parser.lexicon import LEXICON_PATH, SKILL, TECHNOLOGY, Lexicon, default_lexicon
from app.services.cv_parser.text import fold


def names(text: str) -> set[str]:
    return {entry.name for entry in default_lexicon().find(text)}


def test_lexicon_loads_technologies_and_skills():
    lexicon = default_lexicon()
    kinds = {entry.kind for entry in lexicon.find("Java, Scrum")}

    assert lexicon.size > 150
    assert lexicon.version == "1.0"
    assert kinds == {TECHNOLOGY, SKILL}


def test_lexicon_aliases_are_unique():
    data = json.loads(LEXICON_PATH.read_text(encoding="utf-8"))
    seen: dict[str, str] = {}
    for groups in (data["technologies"], data["competences"]):
        for items in groups.values():
            for item in items:
                for alias in [item["name"], *item.get("aliases", [])]:
                    key = fold(alias).replace(" ", "").replace("-", "")
                    assert seen.setdefault(key, item["name"]) == item["name"], f"alias en double : {alias}"


@pytest.mark.parametrize(("text", "expected"), [
    ("Java et JavaScript", {"Java", "JavaScript"}),
    ("C, C++ et C#", {"C", "C++", "C#"}),
    ("MySQL et SQL", {"MySQL", "SQL"}),
    ("Spring Boot, spring-boot, SpringBoot", {"Spring Boot"}),
    ("Node.js et NodeJS", {"Node.js"}),
    ("React Native et React", {"React Native", "React"}),
    ("Angular 17", {"Angular"}),
    ("développement web, Développement Web", {"Développement web"}),
])
def test_find_matches_whole_terms_only(text, expected):
    assert names(text) == expected


def test_case_sensitive_acronyms_avoid_common_words():
    assert names("Langages : Go") == {"Go"}
    assert names("let's go to the office") == set()
    assert names("IA générative et Machine Learning") >= {"Intelligence artificielle", "Machine Learning"}
    assert "Intelligence artificielle" not in names("via le portail")


def test_find_counts_occurrences():
    counts = default_lexicon().find("Java, Java 17 et java")

    assert [(entry.name, n) for entry, n in counts.items()] == [("Java", 3)]


def test_custom_lexicon_prefers_longest_alias():
    lexicon = Lexicon({"technologies": {"Framework": [
        {"name": "Spring"}, {"name": "Spring Boot", "aliases": ["springboot"]}]}})

    assert {e.name for e in lexicon.find("Spring Boot et Spring")} == {"Spring Boot", "Spring"}
    assert sum(lexicon.find("Spring Boot").values()) == 1
