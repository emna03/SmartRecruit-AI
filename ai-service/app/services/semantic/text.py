"""Construction de textes professionnels anonymisés pour les embeddings."""

from __future__ import annotations


def candidate_text(
    skills: list[str],
    technologies: list[str],
    educations: list[dict],
    experiences: list[dict],
    languages: list[dict] | None = None,
    total_experience_months: int | None = None,
    highest_education_level: str | None = None,
) -> str:
    """Représentation textuelle du profil : compétences, techno, formation, expérience uniquement."""
    parts: list[str] = []
    skill_bits = [s.strip() for s in skills + technologies if isinstance(s, str) and s.strip()]
    if skill_bits:
        parts.append("Compétences et technologies : " + ", ".join(dict.fromkeys(skill_bits)))
    for edu in educations:
        degree = (edu.get("degree") or "").strip()
        field = (edu.get("fieldOfStudy") or edu.get("field_of_study") or "").strip()
        level = (edu.get("level") or "").strip()
        institution = (edu.get("institution") or "").strip()
        bit = " ".join(x for x in (degree, field, level, institution) if x)
        if bit:
            parts.append("Formation : " + bit)
    if highest_education_level:
        parts.append("Niveau d'études : " + highest_education_level)
    for exp in experiences:
        title = (exp.get("jobTitle") or exp.get("job_title") or "").strip()
        company = (exp.get("company") or "").strip()
        months = exp.get("durationMonths", exp.get("duration_months"))
        desc = (exp.get("description") or "").strip()
        bit = " ".join(x for x in (title, company) if x)
        if months is not None:
            bit = f"{bit} ({months} mois)".strip()
        if desc:
            bit = f"{bit}. {desc}".strip()
        if bit:
            parts.append("Expérience : " + bit)
    if total_experience_months is not None and total_experience_months > 0:
        parts.append(f"Expérience totale : {total_experience_months} mois")
    for lang in languages or []:
        name = (lang.get("name") or "").strip()
        level = (lang.get("level") or lang.get("cefr") or "").strip()
        if name:
            parts.append("Langue : " + (f"{name} ({level})" if level else name))
    return "\n".join(parts).strip()


def offer_text(
    title: str,
    required_skills: list[str],
    description: str | None = None,
    min_experience_years: int | None = None,
    education_level: str | None = None,
) -> str:
    """Représentation textuelle de l'offre : titre, compétences, description, exigences."""
    parts: list[str] = []
    if title and title.strip():
        parts.append("Poste : " + title.strip())
    skills = [s.strip() for s in required_skills if isinstance(s, str) and s.strip()]
    if skills:
        parts.append("Compétences requises : " + ", ".join(skills))
    if description and description.strip():
        parts.append("Description : " + " ".join(description.split()))
    if min_experience_years is not None and min_experience_years > 0:
        parts.append(f"Expérience demandée : {min_experience_years} ans")
    if education_level:
        parts.append("Formation demandée : " + education_level)
    return "\n".join(parts).strip()
