"""Tests Sentence Transformer (embedder mocké — déterministes, sans téléchargement)."""

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.core.security import API_KEY_HEADER
from app.main import create_app
from app.services.semantic.embedder import cosine_similarity, similarity_to_score
from app.services.semantic.service import SemanticMatchingService
from app.schemas.semantic import SemanticMatchingRequest
from app.services.semantic.text import candidate_text, offer_text
from tests.conftest import TEST_KEY, make_settings


class FakeEmbedder:
    """Embeddings déterministes basés sur un hash lexique simple (pas de modèle réel)."""

    def encode(self, texts: list[str]) -> np.ndarray:
        rows = []
        for text in texts:
            tokens = set(text.lower().replace(",", " ").split())
            # Vecteur 8D stable : présence de mots-clés techniques.
            keys = ["java", "spring", "angular", "python", "data", "sql", "scrum", "api"]
            vec = np.array([1.0 if k in tokens else 0.0 for k in keys], dtype=np.float64)
            if vec.sum() == 0:
                vec[0] = 0.1
            vec = vec / np.linalg.norm(vec)
            rows.append(vec)
        return np.vstack(rows)


def test_cosine_and_score_bounds():
    a = np.array([1.0, 0.0])
    b = np.array([1.0, 0.0])
    assert cosine_similarity(a, b) == pytest.approx(1.0)
    assert similarity_to_score(1.0) == 100
    assert similarity_to_score(0.0) == 0
    assert similarity_to_score(-0.5) == 0
    assert 0 <= similarity_to_score(0.82) <= 100


def test_text_builders_exclude_identity_fields():
    cand = candidate_text(
        skills=["Java"], technologies=["Spring"],
        educations=[{"degree": "Master"}], experiences=[{"jobTitle": "Dev"}],
    )
    offer = offer_text(title="Backend", required_skills=["Java"], description="API REST")
    assert "Java" in cand and "Spring" in cand
    assert "email" not in cand.lower() and "nom" not in cand.lower()
    assert "Backend" in offer and "Java" in offer


def test_similar_profiles_score_higher_than_different():
    service = SemanticMatchingService(embedder=FakeEmbedder())
    similar = SemanticMatchingRequest.model_validate({
        "candidate": {
            "skills": ["Java", "Spring", "API"],
            "technologies": ["Java"],
            "educations": [{"degree": "Master informatique"}],
            "experiences": [{"jobTitle": "Développeur Java"}],
        },
        "offer": {
            "title": "Développeur Java Spring",
            "requiredSkills": ["Java", "Spring", "API"],
            "description": "Backend Java Spring API",
        },
    })
    different = SemanticMatchingRequest.model_validate({
        "candidate": {
            "skills": ["Java", "Spring"],
            "technologies": ["Java"],
            "educations": [{"degree": "Master"}],
            "experiences": [{"jobTitle": "Dev Java"}],
        },
        "offer": {
            "title": "Data Scientist Python",
            "requiredSkills": ["Python", "Data", "SQL"],
            "description": "Analyse de données Python SQL",
        },
    })
    sim = service.match(similar)
    diff = service.match(different)
    assert sim.semantic_score > diff.semantic_score
    assert 0 <= sim.semantic_similarity <= 1
    assert 0 <= sim.semantic_score <= 100
    assert "sentence-transformers" in sim.model


def test_semantic_endpoint_secured_and_works(tmp_path):
    app = create_app(make_settings(ai_ml_model_dir=tmp_path))
    app.state.semantic_embedder = FakeEmbedder()
    client = TestClient(app, raise_server_exceptions=False)
    payload = {
        "candidate": {"skills": ["Java"], "technologies": ["Spring"], "educations": [], "experiences": []},
        "offer": {"title": "Dev Java", "requiredSkills": ["Java", "Spring"], "description": "Java Spring"},
    }
    assert client.post("/api/ml/semantic-matching", json=payload).status_code == 401
    response = client.post(
        "/api/ml/semantic-matching", json=payload, headers={API_KEY_HEADER: TEST_KEY},
    )
    assert response.status_code == 200
    body = response.json()
    assert {"semanticSimilarity", "semanticScore", "model", "version"} <= body.keys()
    assert 0 <= body["semanticScore"] <= 100


def test_empty_profile_rejected():
    from app.services.semantic.service import EmptyProfileError

    service = SemanticMatchingService(embedder=FakeEmbedder())
    request = SemanticMatchingRequest.model_validate({
        "candidate": {"skills": [], "technologies": [], "educations": [], "experiences": []},
        "offer": {"title": "x", "requiredSkills": ["Java"]},
    })
    with pytest.raises(EmptyProfileError):
        service.match(request)
