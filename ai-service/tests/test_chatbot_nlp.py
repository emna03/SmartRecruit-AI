"""Analyse d'une question : entités extraites et texte masqué utilisé pour la classification."""

import pytest

from app.services.chatbot.nlp import analyze
from tests.chatbot_data import OFFERS


def test_skills_are_recognized_with_their_reference_name():
    analysis = analyze("Je cherche un poste springboot ou k8s avec du JAVA", OFFERS)

    assert analysis.skills == ["Spring Boot", "Kubernetes", "Java"]
    assert analysis.masked == "je cherche un poste xskill ou xskill avec du xskill"


def test_elision_is_not_the_c_language():
    assert analyze("C'est quoi le matching ?", OFFERS).skills == []
    assert analyze("Des offres en C ?", OFFERS).skills == ["C"]


def test_skill_known_only_by_the_offers_is_recognized():
    offers = [OFFERS[0].model_copy(update={"required_skills": ["Talend"]})]

    assert analyze("Quelles offres demandent Talend ?", offers).skills == ["Talend"]


@pytest.mark.parametrize(("question", "years"), [
    ("J'ai 3 ans d'expérience", 3),
    ("deux années d'expérience", 2),
    ("je suis débutant", 0),
    ("un poste junior", 0),
    ("sans expérience", 0),
    ("Combien d'années d'expérience sont nécessaires ?", None),
])
def test_experience_years(question, years):
    assert analyze(question, OFFERS).experience_years == years


@pytest.mark.parametrize(("question", "level"), [
    ("J'ai un master en informatique", "BAC_PLUS_5"),
    ("niveau bac+3", "BAC_PLUS_3"),
    ("je prépare une licence", "BAC_PLUS_3"),
    ("titulaire d'un BTS", "BAC_PLUS_2"),
    ("Quel niveau d'étude est demandé ?", None),
])
def test_education_level(question, level):
    assert analyze(question, OFFERS).education_level == level


@pytest.mark.parametrize(("question", "contract"), [
    ("Je cherche un stage", "STAGE"),
    ("offres en CDI", "CDI"),
    ("une alternance en Java", "ALTERNANCE"),
    ("mission freelance", "FREELANCE"),
    ("Je cherche un emploi", None),
])
def test_contract_type(question, contract):
    assert analyze(question, OFFERS).contract_type == contract


def test_location_comes_only_from_the_offers():
    assert analyze("des offres à tunis ?", OFFERS).location == "Tunis"
    assert analyze("des offres à Paris ?", OFFERS).location is None


def test_cited_offer_title_exact_or_with_a_typo():
    exact = analyze("Quelles compétences pour l'offre Data Scientist ?", OFFERS)
    typo = analyze("Parle-moi de l'offre Developeur Front-end Angular", OFFERS)

    assert exact.mentioned_offer_ids == [13]
    assert "xtitre" in exact.masked
    assert typo.mentioned_offer_ids == [12]


def test_a_single_skill_is_not_taken_for_a_title():
    analysis = analyze("Java", OFFERS)

    assert analysis.mentioned_offer_ids == []
    assert analysis.skills == ["Java"]


def test_skills_inside_a_cited_title_are_kept_apart():
    analysis = analyze("Est-ce que l'offre Développeur Java Spring Boot demande Docker ?", OFFERS)

    assert analysis.mentioned_offer_ids == [11]
    assert analysis.skills == ["Docker"]
    assert analysis.all_skills == ["Java", "Spring Boot", "Docker"]


def test_explicit_title_that_does_not_exist_is_captured():
    analysis = analyze("Quelles compétences pour l'offre Ingénieur Quantique ?", OFFERS)

    assert analysis.mentioned_offer_ids == []
    assert analysis.explicit_title == "Ingénieur Quantique"
    assert analyze("Quelles compétences pour cette offre ?", OFFERS).explicit_title is None


def test_keywords_exclude_search_vocabulary_and_entities():
    assert analyze("Je cherche un poste de data analyst", OFFERS).keywords == ["data", "analyst"]
    assert analyze("Quelles offres sont disponibles en CDI ?", OFFERS).keywords == []
