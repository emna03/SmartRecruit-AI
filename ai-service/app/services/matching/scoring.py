"""Sous-scores (0-100) et score global du matching.

- Compétences : part des compétences requises couvertes (crédits de ``skills.py``).
- Expérience : mois d'expérience du CV / mois demandés, plafonné à 100 (pas de bonus ni de pénalité de
  surqualification). Critère non applicable si l'offre ne demande aucune expérience.
- Formation : 100 si le niveau du CV atteint celui demandé, puis -40 points par niveau manquant
  (Bac < Bac+2 < Bac+3 < Bac+5 < Doctorat). Critère non applicable si l'offre ne précise pas de niveau.
- Score global : moyenne pondérée (compétences 60 %, expérience 25 %, formation 15 %) des seuls critères
  applicables ; les poids sont renormalisés pour que leur somme reste égale à 1.
"""

SKILLS = "skills"
EXPERIENCE = "experience"
EDUCATION = "education"

WEIGHTS = {SKILLS: 0.60, EXPERIENCE: 0.25, EDUCATION: 0.15}
EDUCATION_PENALTY_PER_LEVEL = 40.0
EDUCATION_LEVELS = ["BAC", "BAC_PLUS_2", "BAC_PLUS_3", "BAC_PLUS_5", "DOCTORAT"]


def experience_score(candidate_months: int | None, required_years: int) -> float | None:
    if required_years <= 0:
        return None
    return min(100.0, 100.0 * (candidate_months or 0) / (12 * required_years))


def education_gap(candidate_level: str | None, required_level: str) -> int:
    """Nombre de niveaux manquants (0 si le niveau demandé est atteint) ; niveau inconnu = tous manquants."""
    required = EDUCATION_LEVELS.index(required_level)
    if candidate_level is None:
        return required + 1
    return max(0, required - EDUCATION_LEVELS.index(candidate_level))


def education_score(candidate_level: str | None, required_level: str | None) -> float | None:
    if required_level is None:
        return None
    if candidate_level is None:
        return 0.0
    return max(0.0, 100.0 - EDUCATION_PENALTY_PER_LEVEL * education_gap(candidate_level, required_level))


def effective_weights(scores: dict[str, float | None]) -> dict[str, float | None]:
    applicable = {name: WEIGHTS[name] for name, value in scores.items() if value is not None}
    total = sum(applicable.values())
    return {name: (applicable[name] / total if name in applicable else None) for name in scores}


def global_score(scores: dict[str, float | None]) -> float:
    weights = effective_weights(scores)
    value = sum(scores[name] * weight for name, weight in weights.items() if weight is not None)
    return min(100.0, max(0.0, value))
