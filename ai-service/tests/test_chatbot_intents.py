"""Reconnaissance de l'intention : questions de l'énoncé, reformulations, fautes de frappe et hors sujet."""

import pytest

from app.services.chatbot.engine import answer_question, classifier
from app.services.chatbot.intents import features, tokens
from tests.chatbot_data import ask


@pytest.mark.parametrize(("question", "intent"), [
    # Questions de l'énoncé de l'étape 10
    ("Quelles offres correspondent à Java ?", "SEARCH_OFFERS"),
    ("Je cherche un poste Angular.", "SEARCH_OFFERS"),
    ("Quelles offres demandent Spring Boot ?", "SEARCH_OFFERS"),
    ("Quelles sont les compétences demandées pour cette offre ?", "OFFER_SKILLS"),
    ("Combien d'années d'expérience sont nécessaires ?", "OFFER_EXPERIENCE"),
    ("Quel niveau d'étude est demandé ?", "OFFER_EDUCATION"),
    ("Comment postuler à une offre ?", "FAQ_APPLY"),
    ("Comment importer mon CV ?", "FAQ_CV"),
    ("Comment fonctionne le score de compatibilité ?", "FAQ_MATCHING"),
    # Reformulations
    ("je veux déposer mon cv", "FAQ_CV"),
    ("Comment je peux candidater ?", "FAQ_APPLY"),
    ("comment est calculé le score", "FAQ_MATCHING"),
    ("C'est quoi le matching ?", "FAQ_MATCHING"),
    ("Je cherche un stage à Tunis", "SEARCH_OFFERS"),
    ("Y a-t-il des offres pour débutants ?", "SEARCH_OFFERS"),
    ("j'ai 1 an d'expérience, quelles offres ?", "SEARCH_OFFERS"),
    ("Est-ce que cette offre demande Docker ?", "OFFER_SKILLS"),
    ("Faut-il un master pour ce poste ?", "OFFER_EDUCATION"),
    ("Quel est le salaire ?", "OFFER_SALARY"),
    ("Parle-moi de cette offre", "OFFER_DETAILS"),
    ("Comment suivre ma candidature ?", "FAQ_APPLICATIONS"),
    ("Comment modifier mon profil ?", "FAQ_PROFILE"),
    ("Mes données sont-elles protégées ?", "FAQ_PRIVACY"),
    ("Quelles offres me correspondent ?", "FAQ_RECOMMENDATION"),
    ("Bonjour", "GREETING"),
    ("merci !", "THANKS"),
    ("Que peux-tu faire ?", "HELP"),
    # Fautes de frappe
    ("comment postuller a une offre", "FAQ_APPLY"),
    ("quelles competances sont demandees pour cette offre", "OFFER_SKILLS"),
    # Compétence seule : recherche d'offres
    ("Java", "SEARCH_OFFERS"),
])
def test_intent_is_recognized(question, intent):
    assert answer_question(ask(question)).intent == intent


@pytest.mark.parametrize("question", [
    "Quelle est la capitale de la France ?",
    "Quel temps fait-il demain ?",
    "Raconte-moi une blague",
    "Qui a gagné la coupe du monde ?",
    "Combien coûte un abonnement ?",
    "Donne-moi une recette de cuisine",
    "asdfgh qwerty",
    "???",
])
def test_out_of_scope_questions_are_not_answered(question):
    response = answer_question(ask(question))

    assert response.intent == "UNKNOWN"
    assert response.answer.startswith("Je ne sais pas répondre à cette question.")
    assert response.offer_ids == []


def test_features_combine_words_and_character_ngrams():
    assert tokens("Quelles offres demandent xskill ?") == ["quelle", "offre", "demandent", "xskill"]
    counts = features("offres")
    assert counts["w:offre"] == 1
    assert {" of", "off", "ffr", "fre", "re ", " off", "ffre "} <= counts.keys()
    assert "offre " not in counts


def test_ranking_is_deterministic_and_bounded():
    first = classifier().rank("quelles offres demandent xskill ?")
    second = classifier().rank("quelles offres demandent xskill ?")

    assert first == second
    assert first[0][0] == "SEARCH_OFFERS"
    assert all(0.0 <= score <= 1.0 + 1e-9 for _, score in first)
