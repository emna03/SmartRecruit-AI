"""Recherche dans le catalogue des offres publiées reçu de Spring Boot : seule source des offres citées.

Critères (tous facultatifs, combinés par « et ») :

- compétences : l'offre doit demander toutes les compétences citées (comparaison sur le nom de référence :
  « springboot » = « Spring Boot ») ; si aucune offre ne les demande toutes et que plusieurs compétences sont
  citées, les offres demandant au moins l'une d'elles sont proposées, en le disant explicitement ;
- type de contrat, ville ;
- expérience : offres accessibles avec le nombre d'années indiqué (expérience minimale demandée ≤ années) ;
- niveau d'études : offres sans exigence ou exigeant un niveau inférieur ou égal ;
- mots-clés restants (sans compétence citée) : chaque mot doit apparaître dans le titre, l'entreprise, la ville
  ou les compétences de l'offre (« data scientist », « Acme »).

Ordre des résultats : nombre de compétences trouvées, puis ordre du catalogue (offres les plus récentes d'abord).
"""

import re
from dataclasses import dataclass, field

from app.schemas.chatbot import ChatbotOffer
from app.services.chatbot.nlp import Analysis
from app.services.cv_parser.education import LEVEL_RANK
from app.services.cv_parser.text import fold
from app.services.matching.skills import SkillRules

ALL, ANY, TITLE, FILTERED, LATEST = "ALL", "ANY", "TITLE", "FILTERED", "LATEST"

_WORD_RE = re.compile(r"[a-z0-9+#]+")


@dataclass
class SearchOutcome:
    mode: str
    offers: list[ChatbotOffer]
    skills: list[str] = field(default_factory=list)
    keywords: list[str] = field(default_factory=list)
    unmatched_keywords: list[str] = field(default_factory=list)


def _stem(word: str) -> str:
    return word[:-1] if len(word) > 4 and word[-1] in "sx" else word


def _words(offer: ChatbotOffer) -> set[str]:
    text = " ".join([offer.title, offer.company, offer.location or "", *offer.required_skills])
    return {_stem(w) for w in _WORD_RE.findall(fold(text))}


def _keyword_matches(keyword: str, words: set[str]) -> bool:
    parts = [_stem(w) for w in _WORD_RE.findall(keyword)]
    return bool(parts) and all(p in words for p in parts)


def _passes_filters(offer: ChatbotOffer, analysis: Analysis) -> bool:
    if analysis.contract_type and offer.contract_type != analysis.contract_type:
        return False
    if analysis.location and fold(analysis.location) not in fold(offer.location or ""):
        return False
    if analysis.experience_years is not None and offer.min_experience_years > analysis.experience_years:
        return False
    if (analysis.education_level and offer.education_level
            and LEVEL_RANK[offer.education_level] > LEVEL_RANK[analysis.education_level]):
        return False
    return True


def has_filters(analysis: Analysis) -> bool:
    return bool(analysis.contract_type or analysis.location or analysis.experience_years is not None
                or analysis.education_level)


def search(offers: list[ChatbotOffer], analysis: Analysis, rules: SkillRules) -> SearchOutcome:
    candidates = [o for o in offers if _passes_filters(o, analysis)]

    if analysis.all_skills:
        wanted = [rules.key(s) for s in analysis.all_skills]
        counted = []
        for offer in candidates:
            owned = {rules.key(s) for s in offer.required_skills}
            counted.append((sum(k in owned for k in wanted), offer))
        complete = [o for n, o in counted if n == len(wanted)]
        if complete or len(wanted) == 1:
            return SearchOutcome(ALL, complete, skills=analysis.all_skills)
        partial = [o for n, o in sorted(counted, key=lambda item: -item[0]) if n > 0]
        return SearchOutcome(ANY, partial, skills=analysis.all_skills)

    if analysis.mentioned_offer_ids:
        return SearchOutcome(TITLE, [o for o in candidates if o.id in analysis.mentioned_offer_ids])

    if analysis.keywords:
        indexed = [(o, _words(o)) for o in offers]
        unmatched = [k for k in analysis.keywords if not any(_keyword_matches(k, words) for _, words in indexed)]
        if unmatched:
            return SearchOutcome(FILTERED, [], keywords=analysis.keywords, unmatched_keywords=unmatched)
        matching = [o for o in candidates if all(_keyword_matches(k, _words(o)) for k in analysis.keywords)]
        return SearchOutcome(FILTERED, matching, keywords=analysis.keywords)

    return SearchOutcome(FILTERED if has_filters(analysis) else LATEST, candidates)
