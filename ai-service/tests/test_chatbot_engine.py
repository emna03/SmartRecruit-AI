"""Réponses du chatbot : construites uniquement à partir du catalogue reçu et de la base de connaissances."""

from app.services.chatbot.engine import CHATBOT_VERSION, answer_question
from tests.chatbot_data import OFFERS, ask

CATALOGUE_IDS = {o.id for o in OFFERS}


def test_search_by_skill_returns_only_offers_requiring_it():
    response = answer_question(ask("Quelles offres correspondent à Java ?"))

    assert response.intent == "SEARCH_OFFERS"
    assert response.offer_ids == [11, 14]
    assert response.total_matches == 2
    assert response.answer.startswith("J'ai trouvé 2 offres publiées qui demandent Java.")
    assert response.entities.skills == ["Java"]
    assert response.context_offer_id is None


def test_single_result_becomes_the_conversation_context():
    response = answer_question(ask("Je cherche un poste en TypeScript"))

    assert response.offer_ids == [12]
    assert response.context_offer_id == 12
    assert response.suggestions[0] == "Quelles sont les compétences demandées pour cette offre ?"


def test_search_combines_skill_contract_location_experience_and_education():
    assert answer_question(ask("Spring Boot en CDI à Tunis")).offer_ids == [11, 14]
    assert answer_question(ask("Je cherche un poste Java, j'ai 2 ans d'expérience")).offer_ids == [11]
    assert answer_question(ask("Je cherche un stage")).offer_ids == [13]
    response = answer_question(ask("Quelles offres avec un niveau bac+3 ?"))
    assert response.offer_ids == [12, 13]
    assert response.entities.education_level == "BAC_PLUS_3"


def test_several_skills_without_common_offer_are_announced_as_partial():
    response = answer_question(ask("Quelles offres demandent Python et Angular ?"))

    assert response.offer_ids == [12, 13, 14]
    assert response.answer.startswith("Aucune offre publiée ne demande à la fois Python et Angular.")


def test_several_skills_without_any_offer_are_repeated_in_the_answer():
    response = answer_question(ask("Quelles offres demandent Rust et Go ?"))

    assert response.offer_ids == []
    assert response.total_matches == 0
    assert response.answer.startswith("Je n'ai trouvé aucune offre publiée qui demande Rust ou Go.")


def test_no_offer_is_invented_for_an_unknown_technology_or_term():
    for question in ("Quelles offres demandent Kubernetes et Rust ?", "Je cherche un poste COBOL",
                     "Quelles offres demandent Flutter ?"):
        response = answer_question(ask(question))
        assert response.intent == "SEARCH_OFFERS"
        assert set(response.offer_ids) <= CATALOGUE_IDS
    flutter = answer_question(ask("Quelles offres demandent Flutter ?"))
    assert flutter.offer_ids == []
    assert flutter.answer.startswith("Je n'ai trouvé aucune offre publiée qui demande Flutter.")
    assert flutter.actions == ["OPEN_JOBS"]
    cobol = answer_question(ask("Je cherche un poste COBOL"))
    assert cobol.offer_ids == []
    assert "« cobol »" in cobol.answer


def test_empty_catalogue_never_produces_an_offer():
    response = answer_question(ask("Quelles offres correspondent à Java ?", offers=[]))

    assert response.offer_ids == []
    assert response.answer.startswith("Aucune offre n'est publiée")


def test_offer_skills_from_the_context_offer():
    response = answer_question(ask("Quelles sont les compétences demandées pour cette offre ?", context=11))

    assert response.intent == "OFFER_SKILLS"
    assert response.answer == ("L'offre « Développeur Java Spring Boot » (Acme) demande 4 compétences : "
                               "Java, Spring Boot, MySQL et Docker.")
    assert response.offer_ids == [11]
    assert response.context_offer_id == 11


def test_offer_skills_checks_the_skills_cited_in_the_question():
    response = answer_question(ask("Est-ce que cette offre demande Docker et Kubernetes ?", context=11))

    assert "Docker : oui, cette compétence est demandée." in response.answer
    assert "Kubernetes : non, elle ne fait pas partie des compétences demandées." in response.answer


def test_experience_required_by_the_offer():
    assert answer_question(ask("Combien d'années d'expérience sont nécessaires ?", context=14)).answer == (
        "L'offre « Ingénieur Full Stack Java Angular » (Acme) demande au moins 4 ans d'expérience.")
    assert "ouverte aux débutants" in answer_question(ask("Faut-il de l'expérience pour ce poste ?", context=13)).answer
    insufficient = answer_question(ask("J'ai 1 an d'expérience, est-ce suffisant pour cette offre ?", context=11))
    assert insufficient.intent == "OFFER_EXPERIENCE"
    assert "en dessous du minimum demandé" in insufficient.answer
    assert "c'est le recruteur qui décide" in insufficient.answer


def test_education_required_by_the_offer():
    assert answer_question(ask("Quel niveau d'étude est demandé ?", context=12)).answer == (
        "L'offre « Développeur Front-end Angular » (WebCo) demande un niveau Bac+3 (Licence).")
    assert "n'indique pas de niveau d'études minimum" in answer_question(
        ask("Quel diplôme faut-il ?", context=13)).answer
    lower = answer_question(ask("Une licence suffit-elle pour cette offre ?", context=11))
    assert "Un niveau Bac+3 (Licence) est inférieur au niveau demandé" in lower.answer


def test_salary_and_details_come_from_the_offer():
    assert answer_question(ask("Quel est le salaire ?", context=11)).answer == (
        "Salaire indiqué pour l'offre « Développeur Java Spring Boot » (Acme) : entre 2 500 et 3 500.")
    assert "n'est pas précisé" in answer_question(ask("Quel est le salaire ?", context=12)).answer
    details = answer_question(ask("Parle-moi de cette offre", context=13)).answer
    assert details.startswith("« Data Scientist » chez DataLab : contrat stage, à Tunis.")
    assert "Salaire : jusqu'à 1 200." in details


def test_offer_cited_by_its_title_takes_precedence_over_the_context():
    response = answer_question(ask("Quel niveau d'étude pour l'offre Data Scientist ?", context=11))

    assert response.offer_ids == [13]
    assert response.context_offer_id == 13


def test_question_about_an_offer_without_target_asks_which_one():
    response = answer_question(ask("Combien d'années d'expérience sont nécessaires ?"))

    assert response.intent == "OFFER_EXPERIENCE"
    assert response.answer.startswith("De quelle offre parlez-vous ?")
    assert response.offer_ids == []


def test_offer_that_does_not_exist_is_reported_without_hallucination():
    response = answer_question(ask("Quelles compétences pour l'offre Ingénieur Quantique ?", context=11))

    assert response.intent == "OFFER_SKILLS"
    assert response.offer_ids == []
    assert response.answer.startswith("Je ne trouve aucune offre publiée intitulée « Ingénieur Quantique ».")
    assert "Java" not in response.answer


def test_platform_questions_use_the_knowledge_base_and_keep_the_context():
    apply = answer_question(ask("Comment postuler à une offre ?", context=12))
    cv = answer_question(ask("Comment importer mon CV ?"))
    matching = answer_question(ask("Comment fonctionne le score de compatibilité ?"))

    assert "« Postuler à cette offre »" in apply.answer
    assert apply.actions == ["OPEN_JOBS", "OPEN_APPLICATIONS"]
    assert apply.context_offer_id == 12
    assert "PDF" in cv.answer and cv.actions == ["OPEN_CV"]
    assert "60 %" in matching.answer and "25 %" in matching.answer and "15 %" in matching.answer
    assert "jamais une décision automatique" in matching.answer


def test_unknown_question_says_so_and_offers_suggestions():
    response = answer_question(ask("Quelle est la capitale de la France ?", context=11))

    assert response.intent == "UNKNOWN"
    assert response.offer_ids == []
    assert response.context_offer_id == 11
    assert len(response.suggestions) == 4


def test_suggestions_do_not_repeat_the_question():
    response = answer_question(ask("Comment importer mon CV ?"))

    assert "Comment importer mon CV ?" not in response.suggestions


def test_same_question_always_gives_the_same_answer():
    first = answer_question(ask("Je cherche un poste Angular à Tunis", context=13))
    second = answer_question(ask("Je cherche un poste Angular à Tunis", context=13))

    assert first == second
    assert first.chatbot_version == CHATBOT_VERSION == "1.0.0"
