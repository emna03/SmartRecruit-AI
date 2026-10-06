"""Calcul du score de compatibilité CV ↔ offre : entièrement déterministe, sans aléa ni modèle entraîné.

Même entrée => même score et même explication. La formule et les pondérations sont décrites dans
``scoring.py`` et dans docs/ai/matching.md ; toute modification de la formule ou des règles doit
incrémenter ``ALGORITHM_VERSION`` (enregistrée avec chaque résultat dans MySQL).
"""

from app.schemas.matching import MatchedSkill, MatchingRequest, MatchingResponse, MatchingWeights
from app.services.matching import explanation, scoring
from app.services.matching.skills import default_rules, match_skills

ALGORITHM_VERSION = "1.0.0"


def _round(value: float | None) -> float | None:
    return None if value is None else round(value, 1)


def compute_match(request: MatchingRequest) -> MatchingResponse:
    candidate, offer = request.candidate, request.offer
    skills = match_skills(offer.required_skills, candidate.skills, default_rules())

    scores = {
        scoring.SKILLS: skills.score,
        scoring.EXPERIENCE: scoring.experience_score(candidate.experience_months, offer.min_experience_years),
        scoring.EDUCATION: scoring.education_score(candidate.education_level, offer.education_level),
    }
    weights = scoring.effective_weights(scores)
    score = round(scoring.global_score(scores), 1)

    lines = [
        explanation.skills_sentence(skills, weights[scoring.SKILLS]),
        explanation.experience_sentence(scores[scoring.EXPERIENCE], weights[scoring.EXPERIENCE],
                                        candidate.experience_months, offer.min_experience_years),
        explanation.education_sentence(scores[scoring.EDUCATION], weights[scoring.EDUCATION],
                                       candidate.education_level, offer.education_level),
    ]
    return MatchingResponse(
        algorithm_version=ALGORITHM_VERSION,
        score=score,
        skills_score=_round(scores[scoring.SKILLS]),
        experience_score=_round(scores[scoring.EXPERIENCE]),
        education_score=_round(scores[scoring.EDUCATION]),
        weights=MatchingWeights(**{name: _round_weight(w) for name, w in weights.items()}),
        required_skills_count=skills.required_count,
        matched_skills=[MatchedSkill(name=m.name, matched_by=m.matched_by, match_type=m.kind, credit=m.credit)
                        for m in skills.matched],
        missing_skills=skills.missing,
        explanation=explanation.build_explanation(score, lines),
    )


def _round_weight(value: float | None) -> float | None:
    return None if value is None else round(value, 4)
