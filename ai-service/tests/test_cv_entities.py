"""Extraction des formations, expériences et langues à partir de lignes déjà nettoyées."""

from datetime import date

from app.services.cv_parser.dates import YearMonth
from app.services.cv_parser.education import extract_educations, highest_level
from app.services.cv_parser.experience import extract_experiences, total_experience_months
from app.services.cv_parser.languages import document_language, extract_languages

TODAY = date(2026, 10, 6)


def test_education_degree_and_institution_on_one_line():
    [edu] = extract_educations(["Master en Génie Logiciel — Université Exemple | 2020 – 2022"], TODAY)

    assert edu.degree == "Master en Génie Logiciel"
    assert edu.field_of_study == "Génie Logiciel"
    assert edu.institution == "Université Exemple"
    assert (edu.start, edu.end, edu.level) == (YearMonth(2020), YearMonth(2022), "BAC_PLUS_5")


def test_education_dates_first_layout():
    lines = ["2016 – 2019", "Licence en Informatique", "Faculté des Sciences Fictive",
             "2014 – 2016", "Classes préparatoires", "Institut Préparatoire Exemple"]

    educations = extract_educations(lines, TODAY)

    assert [(e.level, e.institution, e.start, e.end) for e in educations] == [
        ("BAC_PLUS_3", "Faculté des Sciences Fictive", YearMonth(2016), YearMonth(2019)),
        ("BAC_PLUS_2", "Institut Préparatoire Exemple", YearMonth(2014), YearMonth(2016)),
    ]


def test_education_ignores_lines_without_degree():
    assert extract_educations(["Université Exemple", "- Mention Bien"], TODAY) == []


def test_highest_level():
    educations = extract_educations(["Baccalauréat Mathématiques | 2016", "Doctorat en Informatique | 2024"], TODAY)

    assert highest_level(educations) == "DOCTORAT"
    assert highest_level([]) is None


def test_experiences_split_on_bullets_and_dates():
    lines = [
        "Développeur Java — Société Exemple, Tunis | 01/2024 – Présent",
        "- API REST Spring Boot",
        "Stagiaire — Entreprise Démo | 02/2023 – 06/2023",
        "- Application Angular",
    ]

    first, second = extract_experiences(lines, TODAY)

    assert (first.job_title, first.company, first.location) == ("Développeur Java", "Société Exemple", "Tunis")
    assert (first.start, first.end, first.current, first.duration_months) == (YearMonth(2024, 1), None, True, 34)
    assert first.description == "API REST Spring Boot"
    assert (second.job_title, second.company, second.location) == ("Stagiaire", "Entreprise Démo", None)
    assert (second.start, second.end, second.duration_months) == (YearMonth(2023, 2), YearMonth(2023, 6), 5)


def test_experience_with_title_company_and_dates_on_separate_lines():
    lines = ["Software Engineer", "Example Corp", "Mar 2020 - Feb 2022", "Built internal tools."]

    [exp] = extract_experiences(lines, TODAY)

    assert (exp.job_title, exp.company) == ("Software Engineer", "Example Corp")
    assert (exp.start, exp.end, exp.duration_months) == (YearMonth(2020, 3), YearMonth(2022, 2), 24)
    assert exp.description == "Built internal tools."


def test_total_experience_does_not_double_count_overlaps():
    lines = ["Développeur — A | 01/2022 – 12/2022", "- x", "Consultant — B | 06/2022 – 03/2023", "- y"]

    assert total_experience_months(extract_experiences(lines, TODAY), TODAY) == 15
    assert total_experience_months([], TODAY) is None


def test_languages_in_section_and_levels():
    languages = extract_languages(
        ["Français : langue maternelle", "Anglais : courant (B2) – TOEIC 850", "Arabe", "Espagnol : notions"], [])

    assert [(l.name, l.level, l.cefr, l.certification) for l in languages] == [
        ("Français", "NATIF", None, None),
        ("Anglais", "COURANT", "B2", "TOEIC 850"),
        ("Arabe", None, None, None),
        ("Espagnol", "DEBUTANT", None, None),
    ]


def test_languages_outside_section_need_an_explicit_level():
    languages = extract_languages([], ["Stage dans une entreprise française", "English: fluent"])

    assert [(l.name, l.level) for l in languages] == [("Anglais", "COURANT")]


def test_document_language():
    assert document_language("Développement des applications pour la gestion de la paie et des congés") == "fr"
    assert document_language("Developed and built tools for the management of data with Python") == "en"
    assert document_language("Java Python SQL") == "inconnu"
