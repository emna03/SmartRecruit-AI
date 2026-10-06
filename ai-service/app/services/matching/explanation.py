"""Explication lisible du score, construite uniquement à partir des valeurs du calcul (aucun texte généré)."""

from app.services.matching.scoring import education_gap
from app.services.matching.skills import IMPLIED, RELATED, SkillsOutcome

HIGH_THRESHOLD = 75
MEDIUM_THRESHOLD = 50
DISCLAIMER = ("Ce score est une aide indicative calculée automatiquement à partir du CV et de l'offre : "
              "il ne constitue pas une décision de recrutement.")
LEVEL_LABELS = {"BAC": "Bac", "BAC_PLUS_2": "Bac+2", "BAC_PLUS_3": "Bac+3", "BAC_PLUS_5": "Bac+5",
                "DOCTORAT": "Doctorat"}


def compatibility_label(score: float) -> str:
    if score >= HIGH_THRESHOLD:
        return "compatibilité élevée"
    if score >= MEDIUM_THRESHOLD:
        return "compatibilité partielle"
    return "compatibilité faible"


def _percent(value: float) -> str:
    return f"{round(value)} %"


def _duration(months: int) -> str:
    years, rest = divmod(months, 12)
    parts = []
    if years:
        parts.append(f"{years} an{'s' if years > 1 else ''}")
    if rest:
        parts.append(f"{rest} mois")
    return " et ".join(parts) or "0 mois"


def _years(years: int) -> str:
    return f"{years} an{'s' if years > 1 else ''}"


def skills_sentence(outcome: SkillsOutcome, weight: float) -> str:
    head = f"Compétences (poids {_percent(weight * 100)}) : {_percent(outcome.score)}"
    total = outcome.required_count
    found = [m.name if m.kind != IMPLIED else f"{m.name} (via {m.matched_by})"
             for m in outcome.matched if m.kind != RELATED]
    related = [f"{m.name} (le CV mentionne {m.matched_by})" for m in outcome.matched if m.kind == RELATED]
    if not found and not related:
        return f"{head} — aucune des {total} compétences requises n'a été trouvée dans le CV."
    if found:
        parts = [f"{head} — {len(found)} compétence(s) requise(s) sur {total} trouvée(s) dans le CV : "
                 f"{', '.join(found)}."]
    else:
        parts = [f"{head} — aucune des {total} compétences requises n'a été trouvée telle quelle dans le CV."]
    if related:
        parts.append(f" Proche(s), crédit partiel de 50 % : {', '.join(related)}.")
    if outcome.missing:
        parts.append(f" Manquantes : {', '.join(outcome.missing)}.")
    return "".join(parts)


def experience_sentence(score: float | None, weight: float | None, months: int | None, required_years: int) -> str:
    if score is None or weight is None:
        return "Expérience : aucune expérience minimale demandée par l'offre (critère non pris en compte)."
    head = f"Expérience (poids {_percent(weight * 100)}) : {_percent(score)}"
    if not months:
        return f"{head} — aucune expérience datée détectée dans le CV pour {_years(required_years)} demandé(s)."
    verdict = "exigence satisfaite" if score >= 100 else "en dessous de l'exigence"
    return (f"{head} — {_duration(months)} d'expérience détectée(s) dans le CV pour {_years(required_years)} "
            f"demandé(s) ({verdict}).")


def education_sentence(score: float | None, weight: float | None, level: str | None, required: str | None) -> str:
    if score is None or weight is None or required is None:
        return "Formation : aucun niveau d'études précisé par l'offre (critère non pris en compte)."
    head = f"Formation (poids {_percent(weight * 100)}) : {_percent(score)}"
    if level is None:
        return f"{head} — aucun diplôme reconnu dans le CV pour un niveau {LEVEL_LABELS[required]} demandé."
    gap = education_gap(level, required)
    verdict = "niveau atteint" if gap == 0 else f"{gap} niveau(x) en dessous"
    return f"{head} — niveau {LEVEL_LABELS[level]} détecté pour {LEVEL_LABELS[required]} demandé ({verdict})."


def build_explanation(score: float, lines: list[str]) -> str:
    header = f"Score global : {_percent(score)} — {compatibility_label(score)}."
    return "\n".join([header, *lines, DISCLAIMER])
