"""Tests K-Means (embedder mocké, données fictives)."""

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.core.security import API_KEY_HEADER
from app.main import create_app
from app.services.clustering.model import KMeansModel, load_clustering_model
from app.services.clustering.training import InsufficientDataError, choose_k, train
from tests.conftest import TEST_KEY, make_settings
from tests.test_semantic import FakeEmbedder


def _profiles(n: int = 12) -> list[dict]:
    """Profils fictifs polarisés Java / Data / Frontend pour un clustering crédible."""
    templates = [
        {"skills": ["Java", "Spring", "API"], "technologies": ["Java", "Spring Boot"],
         "educations": [{"degree": "Master informatique"}], "experiences": [{"jobTitle": "Dev backend Java"}],
         "totalExperienceMonths": 24, "highestEducationLevel": "BAC_PLUS_5"},
        {"skills": ["Python", "Data", "SQL"], "technologies": ["Python", "SQL"],
         "educations": [{"degree": "Master data"}], "experiences": [{"jobTitle": "Data analyst"}],
         "totalExperienceMonths": 18, "highestEducationLevel": "BAC_PLUS_5"},
        {"skills": ["Angular", "TypeScript"], "technologies": ["Angular"],
         "educations": [{"degree": "Licence"}], "experiences": [{"jobTitle": "Dev frontend"}],
         "totalExperienceMonths": 12, "highestEducationLevel": "BAC_PLUS_3"},
    ]
    return [templates[i % 3] for i in range(n)]


def test_insufficient_data_is_refused_not_invented(tmp_path):
    with pytest.raises(InsufficientDataError):
        train(_profiles(2), FakeEmbedder(), artifacts_dir=tmp_path)


def test_choose_k_uses_silhouette(tmp_path):
    # Vecteurs 2D clairement séparés en 2 groupes.
    embeddings = np.vstack([
        np.random.default_rng(0).normal([0, 0], 0.1, size=(10, 2)),
        np.random.default_rng(1).normal([5, 5], 0.1, size=(10, 2)),
    ])
    result = choose_k(embeddings, random_state=42)
    assert result["chosenK"] >= 2
    assert "trials" in result and len(result["trials"]) >= 1
    assert result["silhouette"] > 0.5


def test_train_save_load_assign(tmp_path):
    metadata = train(_profiles(12), FakeEmbedder(), artifacts_dir=tmp_path, random_state=42)
    assert metadata["nClusters"] >= 2
    assert metadata["chosenK"] == metadata["nClusters"]
    model = KMeansModel.load(tmp_path)
    assignment = model.assign(_profiles(1)[0], FakeEmbedder())
    assert 0 <= assignment.cluster_id < assignment.n_clusters
    assert assignment.cluster_description
    summary = model.summary()
    assert summary["nClusters"] == metadata["nClusters"]
    assert "interprétation" in summary["note"].lower() or "interpret" in summary["note"].lower() or "interpretations" in summary["note"].lower() or "interprétations" in summary["note"].lower() or "Descriptions" in summary["note"] or "descriptions" in summary["note"].lower()


def test_clustering_endpoints(tmp_path):
    app = create_app(make_settings(ai_ml_model_dir=tmp_path))
    app.state.semantic_embedder = FakeEmbedder()
    client = TestClient(app, raise_server_exceptions=False)
    headers = {API_KEY_HEADER: TEST_KEY}

    assert client.post("/api/ml/clustering/train", json={"profiles": _profiles(12)}).status_code == 401

    train_resp = client.post("/api/ml/clustering/train", json={"profiles": _profiles(12)}, headers=headers)
    assert train_resp.status_code == 200, train_resp.text
    body = train_resp.json()
    assert body["nClusters"] >= 2
    assert body["chosenK"] == body["nClusters"]

    assign = client.post(
        "/api/ml/clustering/assign",
        json={"profile": _profiles(1)[0]},
        headers=headers,
    )
    assert assign.status_code == 200
    assert "clusterId" in assign.json()

    summary = client.get("/api/ml/clustering/summary", headers=headers)
    assert summary.status_code == 200
    assert summary.json()["available"] is True


def test_empty_profile_assign_fails(tmp_path):
    train(_profiles(12), FakeEmbedder(), artifacts_dir=tmp_path)
    model = load_clustering_model(tmp_path)
    assert model is not None
    with pytest.raises(ValueError):
        model.assign({"skills": [], "technologies": [], "educations": [], "experiences": []}, FakeEmbedder())
