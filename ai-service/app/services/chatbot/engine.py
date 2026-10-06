"""Chatbot RH : question + catalogue des offres publiées -> intention -> réponse construite à partir des données.

Déroulement (entièrement local et déterministe, aucun LLM) :

1. ``nlp.analyze`` extrait les entités (compétences, titre d'offre, expérience, niveau, contrat, ville) ;
2. ``IntentClassifier`` classe la question masquée (similarité TF-IDF avec les exemples) ; sous le seuil, la
   question n'est pas reconnue et le chatbot le dit ; une question sans intention claire mais citant une
   compétence, un contrat ou une ville est traitée comme une recherche d'offres ;
3. la réponse est produite par un modèle de phrase propre à l'intention, rempli uniquement avec les offres
   reçues (recherche, détail d'une offre) ou avec la base de connaissances (fonctionnement de la plateforme).

Le chatbot ne cite jamais une offre absente du catalogue : les offres sont désignées par leur identifiant et
Spring Boot vérifie qu'elles font partie des offres qu'il a envoyées. Toute modification des règles, des
exemples ou des réponses doit incrémenter ``CHATBOT_VERSION``.
"""

import json
from dataclasses import dataclass
from decimal import Decimal
from functools import lru_cache
from pathlib import Path

from app.schemas.chatbot import ChatbotEntities, ChatbotOffer, ChatbotRequest, ChatbotResponse
from app.services.chatbot import offers as catalogue
from app.services.chatbot.intents import MIN_COVERAGE, THRESHOLD, IntentClassifier
from app.services.chatbot.nlp import Analysis, analyze
from app.services.cv_parser.education import LEVEL_RANK
from app.services.cv_parser.text import fold
from app.services.matching.skills import default_rules

CHATBOT_VERSION = "1.0.0"
KNOWLEDGE_PATH = Path(__file__).resolve().parents[2] / "resources" / "chatbot_knowledge.json"
MAX_SHOWN = 5

UNKNOWN = "UNKNOWN"
SEARCH = "SEARCH_OFFERS"
OFFER_INTENTS = {"OFFER_SKILLS", "OFFER_EXPERIENCE", "OFFER_EDUCATION", "OFFER_DETAILS", "OFFER_SALARY"}
SMALL_TALK = {"GREETING", "THANKS", "HELP"}

EDUCATION_LABELS = {"BAC": "Bac", "BAC_PLUS_2": "Bac+2", "BAC_PLUS_3": "Bac+3 (Licence)",
                    "BAC_PLUS_5": "Bac+5 (Master / Ingénieur)", "DOCTORAT": "Doctorat"}
CONTRACT_LABELS = {"CDI": "CDI", "CDD": "CDD", "STAGE": "stage", "ALTERNANCE": "alternance", "FREELANCE": "freelance"}

NO_TARGET_ANSWER = ("De quelle offre parlez-vous ? Ouvrez une offre et cliquez sur « Poser une question à "
                    "l'assistant », choisissez une offre dans une de mes réponses, ou citez son titre exact.")
SELECT_HINT = ("Ouvrez une offre pour voir son détail et votre score de compatibilité, ou sélectionnez-la pour "
               "me poser des questions dessus.")


@dataclass(frozen=True)
class Knowledge:
    version: str
    examples: dict[str, list[str]]
    answers: dict[str, str]
    actions: dict[str, list[str]]
    default_suggestions: list[str]
    offer_suggestions: list[str]
    unknown_answer: str


@lru_cache
def knowledge() -> Knowledge:
    data = json.loads(KNOWLEDGE_PATH.read_text(encoding="utf-8"))
    intents = data["intents"]
    return Knowledge(
        version=data["version"],
        examples={name: spec["examples"] for name, spec in intents.items()},
        answers={name: spec["answer"] for name, spec in intents.items() if "answer" in spec},
        actions={name: spec.get("actions", []) for name, spec in intents.items()},
        default_suggestions=data["defaultSuggestions"],
        offer_suggestions=data["offerSuggestions"],
        unknown_answer=data["unknownAnswer"],
    )


@lru_cache
def classifier() -> IntentClassifier:
    return IntentClassifier(knowledge().examples)


@dataclass
class Reply:
    answer: str
    offers: list[ChatbotOffer]
    total: int
    context: int | None
    actions: list[str]


# ---------- Mise en forme ----------

def _plural(count: int, singular: str, plural: str) -> str:
    return f"{count} {singular if count == 1 else plural}"


def _years(count: int) -> str:
    return _plural(count, "an", "ans")


def _enumerate(items: list[str]) -> str:
    return items[0] if len(items) == 1 else ", ".join(items[:-1]) + " et " + items[-1]


def _amount(value: Decimal) -> str:
    text = f"{value:,.2f}".rstrip("0").rstrip(".") if value != int(value) else f"{int(value):,}"
    return text.replace(",", " ")


def _named(offer: ChatbotOffer) -> str:
    return f"L'offre « {offer.title} » ({offer.company})"


# ---------- Recherche d'offres ----------

def _criteria(outcome: catalogue.SearchOutcome, analysis: Analysis) -> str:
    plural = len(outcome.offers) > 1
    s = "s" if plural else ""
    parts = []
    if outcome.mode == catalogue.ALL and outcome.skills:
        parts.append(f"qui {'demandent' if plural else 'demande'} {_enumerate(outcome.skills)}")
    if outcome.mode == catalogue.ANY and outcome.skills and not outcome.offers:
        parts.append(f"qui demande {' ou '.join(outcome.skills)}")
    if outcome.mode == catalogue.TITLE:
        parts.append("portant le titre cité")
    if outcome.keywords:
        parts.append(f"correspondant à « {' '.join(outcome.keywords)} »")
    if analysis.contract_type:
        parts.append(f"en {CONTRACT_LABELS[analysis.contract_type]}")
    if analysis.location:
        parts.append(f"à {analysis.location}")
    if analysis.experience_years is not None:
        parts.append(f"ouverte{s} aux débutants" if analysis.experience_years == 0
                     else f"accessible{s} avec {_years(analysis.experience_years)} d'expérience")
    if analysis.education_level:
        parts.append(f"accessible{s} avec un niveau {EDUCATION_LABELS[analysis.education_level]}")
    return (" " + ", ".join(parts)) if parts else ""


def _search_reply(request: ChatbotRequest, analysis: Analysis) -> Reply:
    outcome = catalogue.search(request.offers, analysis, default_rules())
    found, total = outcome.offers[:MAX_SHOWN], len(outcome.offers)
    context = found[0].id if total == 1 else None

    if not request.offers:
        return Reply("Aucune offre n'est publiée sur SmartRecruit pour le moment. Revenez plus tard ou "
                     "consultez la liste des offres.", [], 0, None, ["OPEN_JOBS"])
    if outcome.unmatched_keywords:
        terms = " ".join(outcome.unmatched_keywords)
        return Reply(f"Je n'ai trouvé aucune offre publiée correspondant à « {terms} ». Je ne propose que des "
                     "offres réellement publiées sur SmartRecruit : essayez une compétence (par exemple Java ou "
                     "Angular), un type de contrat ou une ville.", [], 0, None, ["OPEN_JOBS"])
    if outcome.mode == catalogue.LATEST:
        answer = (f"Voici les offres publiées les plus récentes ({_plural(total, 'offre', 'offres')} au total). "
                  "Précisez une compétence, un type de contrat ou une ville pour affiner, par exemple « Je cherche "
                  "un stage en Java ».")
        return Reply(answer, found, total, context, [])

    criteria = _criteria(outcome, analysis)
    if total == 0:
        return Reply(f"Je n'ai trouvé aucune offre publiée{criteria}. Je ne propose que des offres réellement "
                     "publiées sur SmartRecruit : essayez d'autres critères ou consultez toutes les offres.",
                     [], 0, None, ["OPEN_JOBS"])
    if outcome.mode == catalogue.ANY:
        answer = (f"Aucune offre publiée ne demande à la fois {_enumerate(outcome.skills)}. Voici "
                  f"{_plural(total, 'offre qui demande', 'offres qui demandent')} au moins l'une de ces "
                  f"compétences{criteria}.")
    else:
        answer = f"J'ai trouvé {_plural(total, 'offre publiée', 'offres publiées')}{criteria}."
    if total > MAX_SHOWN:
        answer += f" Voici les {MAX_SHOWN} plus récentes."
    return Reply(f"{answer} {SELECT_HINT}", found, total, context, [])


# ---------- Questions sur une offre ----------

def _target(request: ChatbotRequest, analysis: Analysis) -> tuple[ChatbotOffer | None, list[ChatbotOffer]]:
    by_id = {o.id: o for o in request.offers}
    if analysis.mentioned_offer_ids:
        mentioned = [by_id[i] for i in analysis.mentioned_offer_ids]
        return (mentioned[0], []) if len(mentioned) == 1 else (None, mentioned)
    if analysis.explicit_title:
        return None, []
    return by_id.get(request.context_offer_id), []


def _skills_answer(offer: ChatbotOffer, analysis: Analysis) -> str:
    if not offer.required_skills:
        return f"{_named(offer)} n'indique aucune compétence requise."
    answer = (f"{_named(offer)} demande {_plural(len(offer.required_skills), 'compétence', 'compétences')} : "
              f"{_enumerate(offer.required_skills)}.")
    rules = default_rules()
    owned = {rules.key(s) for s in offer.required_skills}
    for skill in analysis.skills:
        answer += (f" {skill} : oui, cette compétence est demandée." if rules.key(skill) in owned
                   else f" {skill} : non, elle ne fait pas partie des compétences demandées.")
    return answer


def _experience_answer(offer: ChatbotOffer, analysis: Analysis) -> str:
    required = offer.min_experience_years
    if required == 0:
        answer = f"{_named(offer)} n'exige pas d'expérience minimale : elle est ouverte aux débutants."
    else:
        answer = f"{_named(offer)} demande au moins {_years(required)} d'expérience."
    own = analysis.experience_years
    if own is not None and required > 0:
        profile = "un profil débutant" if own == 0 else f"{_years(own)} d'expérience"
        answer += (f" Avec {profile}, vous atteignez ce minimum." if own >= required
                   else f" Avec {profile}, vous êtes en dessous du minimum demandé : vous pouvez tout de même "
                        "postuler, c'est le recruteur qui décide.")
    return answer


def _education_answer(offer: ChatbotOffer, analysis: Analysis) -> str:
    if offer.education_level is None:
        return f"{_named(offer)} n'indique pas de niveau d'études minimum."
    answer = f"{_named(offer)} demande un niveau {EDUCATION_LABELS[offer.education_level]}."
    own = analysis.education_level
    if own is not None:
        label = EDUCATION_LABELS[own]
        answer += (f" Un niveau {label} satisfait cette exigence." if LEVEL_RANK[own] >= LEVEL_RANK[offer.education_level]
                   else f" Un niveau {label} est inférieur au niveau demandé : le score de compatibilité en tient "
                        "compte, mais la décision revient au recruteur.")
    return answer


def _salary_text(offer: ChatbotOffer) -> str | None:
    low, high = offer.salary_min, offer.salary_max
    if low is not None and high is not None:
        return f"entre {_amount(low)} et {_amount(high)}"
    if low is not None:
        return f"à partir de {_amount(low)}"
    if high is not None:
        return f"jusqu'à {_amount(high)}"
    return None


def _salary_answer(offer: ChatbotOffer) -> str:
    salary = _salary_text(offer)
    if salary is None:
        return f"Le salaire n'est pas précisé pour l'offre « {offer.title} » ({offer.company})."
    return f"Salaire indiqué pour l'offre « {offer.title} » ({offer.company}) : {salary}."


def _details_answer(offer: ChatbotOffer) -> str:
    place = f", à {offer.location}" if offer.location else ""
    experience = ("aucune" if offer.min_experience_years == 0 else f"{_years(offer.min_experience_years)}")
    education = EDUCATION_LABELS[offer.education_level] if offer.education_level else "non précisé"
    skills = _enumerate(offer.required_skills) if offer.required_skills else "non précisées"
    salary = _salary_text(offer) or "non précisé"
    return (f"« {offer.title} » chez {offer.company} : contrat {CONTRACT_LABELS[offer.contract_type]}{place}. "
            f"Expérience minimale : {experience}. Niveau d'études : {education}. Compétences demandées : {skills}. "
            f"Salaire : {salary}.")


def _offer_reply(intent: str, request: ChatbotRequest, analysis: Analysis) -> Reply:
    offer, ambiguous = _target(request, analysis)
    if ambiguous:
        return Reply("Plusieurs offres publiées portent ce titre : sélectionnez celle qui vous intéresse.",
                     ambiguous[:MAX_SHOWN], len(ambiguous), None, [])
    if offer is None and analysis.explicit_title:
        return Reply(f"Je ne trouve aucune offre publiée intitulée « {analysis.explicit_title[:80]} ». Je ne réponds "
                     "que sur les offres réellement publiées : vérifiez le titre ou recherchez par compétence.",
                     [], 0, request.context_offer_id, ["OPEN_JOBS"])
    if offer is None:
        return Reply(NO_TARGET_ANSWER, [], 0, None, [])
    answer = {
        "OFFER_SKILLS": lambda: _skills_answer(offer, analysis),
        "OFFER_EXPERIENCE": lambda: _experience_answer(offer, analysis),
        "OFFER_EDUCATION": lambda: _education_answer(offer, analysis),
        "OFFER_SALARY": lambda: _salary_answer(offer),
        "OFFER_DETAILS": lambda: _details_answer(offer),
    }[intent]()
    return Reply(answer, [offer], 1, offer.id, [])


# ---------- Assemblage ----------

def _intent(analysis: Analysis) -> tuple[str, float]:
    ranking = classifier().rank(analysis.masked)
    scores = dict(ranking)
    intent, score = ranking[0]
    if score < THRESHOLD or classifier().coverage(analysis.masked) < MIN_COVERAGE:
        intent = UNKNOWN
    # Une compétence, un contrat ou une ville sans autre demande claire : recherche d'offres.
    if (intent == UNKNOWN or intent in SMALL_TALK) and (analysis.all_skills or analysis.contract_type
                                                        or analysis.location):
        return SEARCH, scores[SEARCH]
    if intent == UNKNOWN and analysis.mentioned_offer_ids:
        return "OFFER_DETAILS", scores["OFFER_DETAILS"]
    return intent, score


def _suggestions(context: int | None, question: str) -> list[str]:
    kb = knowledge()
    pool = kb.offer_suggestions if context is not None else kb.default_suggestions
    asked = fold(question).strip(" ?!.")
    return [s for s in pool if fold(s).strip(" ?!.") != asked][:4]


def answer_question(request: ChatbotRequest) -> ChatbotResponse:
    analysis = analyze(request.message, request.offers)
    intent, confidence = _intent(analysis)
    kb = knowledge()

    if intent == SEARCH:
        reply = _search_reply(request, analysis)
    elif intent in OFFER_INTENTS:
        reply = _offer_reply(intent, request, analysis)
    elif intent == UNKNOWN:
        reply = Reply(kb.unknown_answer, [], 0, request.context_offer_id, [])
    else:
        context_offers = [o for o in request.offers if o.id == request.context_offer_id]
        shown = context_offers if intent == "FAQ_RECOMMENDATION" else []
        reply = Reply(kb.answers[intent], shown, len(shown), request.context_offer_id, kb.actions.get(intent, []))

    entities = ChatbotEntities(
        skills=(analysis.all_skills if intent == SEARCH else analysis.skills)[:20],
        experience_years=analysis.experience_years,
        education_level=analysis.education_level,
        contract_type=analysis.contract_type,
        location=analysis.location,
        keywords=[k[:40] for k in analysis.keywords] if intent == SEARCH and not analysis.all_skills else [],
    )
    return ChatbotResponse(
        chatbot_version=CHATBOT_VERSION,
        intent=intent,
        confidence=round(min(max(confidence, 0.0), 1.0), 2),
        answer=reply.answer,
        offer_ids=[o.id for o in reply.offers],
        total_matches=reply.total,
        context_offer_id=reply.context,
        entities=entities,
        suggestions=_suggestions(reply.context, analysis.text),
        actions=reply.actions,
    )
