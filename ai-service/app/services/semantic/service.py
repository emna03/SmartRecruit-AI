"""Calcul de la similarité sémantique CV ↔ offre."""

from __future__ import annotations

import logging

from app.schemas.semantic import SemanticMatchingRequest, SemanticMatchingResponse
from app.services.semantic import MODEL_NAME, MODEL_VERSION, PROVIDER
from app.services.semantic.embedder import Embedder, SentenceTransformerEmbedder, cosine_similarity, similarity_to_score
from app.services.semantic.text import candidate_text, offer_text

logger = logging.getLogger("smartrecruit_ai.semantic")


class EmptyProfileError(ValueError):
    """Profil ou offre sans contenu textuel utilisable."""


class SemanticMatchingService:
    def __init__(self, embedder: Embedder | None = None):
        self._embedder = embedder or SentenceTransformerEmbedder()

    def match(self, request: SemanticMatchingRequest) -> SemanticMatchingResponse:
        cand = candidate_text(
            skills=list(request.candidate.skills),
            technologies=list(request.candidate.technologies),
            educations=[e.model_dump(by_alias=True) for e in request.candidate.educations],
            experiences=[e.model_dump(by_alias=True) for e in request.candidate.experiences],
            languages=[lang.model_dump(by_alias=True) for lang in request.candidate.languages],
            total_experience_months=request.candidate.total_experience_months,
            highest_education_level=request.candidate.highest_education_level,
        )
        offer = offer_text(
            title=request.offer.title,
            required_skills=list(request.offer.required_skills),
            description=request.offer.description,
            min_experience_years=request.offer.min_experience_years,
            education_level=request.offer.education_level,
        )
        if not cand or not offer:
            raise EmptyProfileError("Données professionnelles insuffisantes pour le matching sémantique")

        vectors = self._embedder.encode([cand, offer])
        similarity = cosine_similarity(vectors[0], vectors[1])
        # Cosinus entre embeddings normalisés ∈ [-1, 1] ; on expose la similarité bornée [0, 1] pour l'API.
        bounded = max(0.0, min(1.0, similarity))
        score = similarity_to_score(bounded)
        logger.info("Matching sémantique : similarité=%.4f score=%s modèle=%s", bounded, score, MODEL_NAME)
        return SemanticMatchingResponse(
            semantic_similarity=round(bounded, 4),
            semantic_score=score,
            model=MODEL_NAME,
            version=MODEL_VERSION,
            provider=PROVIDER,
        )
