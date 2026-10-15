"""Chargement lazy du Sentence Transformer pré-entraîné."""

from __future__ import annotations

import logging
import threading
from typing import Protocol

import numpy as np

from app.services.semantic import MODEL_NAME

logger = logging.getLogger("smartrecruit_ai.semantic")


class Embedder(Protocol):
    def encode(self, texts: list[str]) -> np.ndarray: ...


class SentenceTransformerEmbedder:
    """Wrapper autour de ``sentence_transformers.SentenceTransformer`` (modèle pré-entraîné)."""

    def __init__(self, model_name: str = MODEL_NAME):
        self.model_name = model_name
        self._model = None
        self._lock = threading.Lock()

    def _ensure_loaded(self) -> None:
        if self._model is not None:
            return
        with self._lock:
            if self._model is not None:
                return
            from sentence_transformers import SentenceTransformer

            logger.info("Chargement du Sentence Transformer pré-entraîné : %s", self.model_name)
            self._model = SentenceTransformer(self.model_name)

    def encode(self, texts: list[str]) -> np.ndarray:
        self._ensure_loaded()
        vectors = self._model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
        return np.asarray(vectors, dtype=np.float64)


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """Similarité cosinus entre deux vecteurs (déjà normalisés ou non)."""
    a = np.asarray(a, dtype=np.float64).ravel()
    b = np.asarray(b, dtype=np.float64).ravel()
    denom = float(np.linalg.norm(a) * np.linalg.norm(b))
    if denom == 0.0:
        return 0.0
    return float(np.clip(np.dot(a, b) / denom, -1.0, 1.0))


def similarity_to_score(similarity: float) -> int:
    """Transforme une similarité cosinus [-1, 1] en score entier 0–100 (partie positive)."""
    clamped = max(0.0, min(1.0, float(similarity)))
    return int(round(clamped * 100))
