"""Construction des vecteurs de clustering à partir des profils anonymisés."""

from __future__ import annotations

import numpy as np

from app.services.semantic.text import candidate_text


def profile_document(profile: dict) -> str:
    """Texte professionnel d'un profil (même construction que le matching sémantique)."""
    return candidate_text(
        skills=list(profile.get("skills") or []),
        technologies=list(profile.get("technologies") or []),
        educations=list(profile.get("educations") or []),
        experiences=list(profile.get("experiences") or []),
        languages=list(profile.get("languages") or []),
        total_experience_months=profile.get("totalExperienceMonths") or profile.get("total_experience_months"),
        highest_education_level=profile.get("highestEducationLevel") or profile.get("highest_education_level"),
    )


def embed_profiles(profiles: list[dict], embedder) -> tuple[np.ndarray, list[str]]:
    """Retourne la matrice d'embeddings et les textes utilisés (sans identité)."""
    documents = [profile_document(p) for p in profiles]
    usable = [(i, doc) for i, doc in enumerate(documents) if doc.strip()]
    if not usable:
        return np.empty((0, 0)), []
    indices, texts = zip(*usable)
    vectors = np.asarray(embedder.encode(list(texts)), dtype=np.float64)
    # Réindexer dans l'ordre d'origine en ignorant les vides n'est pas nécessaire : on ne garde que les utilisables.
    _ = indices
    return vectors, list(texts)


def dominant_terms(profiles: list[dict], labels: np.ndarray, cluster_id: int, limit: int = 5) -> list[str]:
    """Termes les plus fréquents (compétences/technologies) dans un cluster — interprétation, pas vérité."""
    from collections import Counter

    counter: Counter[str] = Counter()
    for profile, label in zip(profiles, labels):
        if int(label) != cluster_id:
            continue
        for key in ("skills", "technologies"):
            for item in profile.get(key) or []:
                if isinstance(item, str) and item.strip():
                    counter[item.strip()] += 1
    return [name for name, _ in counter.most_common(limit)]
