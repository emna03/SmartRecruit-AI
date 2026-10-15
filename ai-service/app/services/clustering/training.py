"""Entraînement K-Means : choix de K par score de silhouette (et inertie documentée)."""

from __future__ import annotations

import logging
from datetime import UTC, datetime

import joblib
import numpy as np
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score

from app.core.config import SERVICE_DIR
from app.services.clustering import ALGORITHM, K_MAX, K_MIN, MIN_PROFILES, MODEL_VERSION
from app.services.clustering.features import dominant_terms, embed_profiles
from app.services.semantic import MODEL_NAME as EMBEDDING_MODEL

logger = logging.getLogger("smartrecruit_ai.clustering")

ARTIFACTS_DIR = SERVICE_DIR / "ml" / "artifacts"
MODEL_FILE = "kmeans_model.joblib"
METADATA_FILE = "kmeans_model.json"


class InsufficientDataError(ValueError):
    """Pas assez de profils professionnels pour un clustering crédible."""


def choose_k(embeddings: np.ndarray, random_state: int = 42) -> dict:
    """Teste K dans [K_MIN, K_MAX] (borné par n_samples) ; retient le meilleur silhouette moyen."""
    n = embeddings.shape[0]
    max_k = min(K_MAX, n - 1)
    if n < MIN_PROFILES or max_k < K_MIN:
        raise InsufficientDataError(
            f"Dataset insuffisant pour choisir K de façon crédible "
            f"(n={n}, minimum={MIN_PROFILES}). Aucun K inventé."
        )

    trials: list[dict] = []
    best_k, best_score, best_model = K_MIN, -1.0, None
    for k in range(K_MIN, max_k + 1):
        model = KMeans(n_clusters=k, n_init=10, random_state=random_state)
        labels = model.fit_predict(embeddings)
        # Silhouette exige au moins 2 clusters et n > k.
        if len(set(labels)) < 2:
            continue
        sil = float(silhouette_score(embeddings, labels))
        inertia = float(model.inertia_)
        trials.append({"k": k, "silhouette": round(sil, 4), "inertia": round(inertia, 4)})
        if sil > best_score:
            best_k, best_score, best_model = k, sil, model

    if best_model is None:
        raise InsufficientDataError("Impossible de calculer un score de silhouette valide pour les K testés.")

    return {
        "chosenK": best_k,
        "silhouette": round(best_score, 4),
        "trials": trials,
        "model": best_model,
        "rule": "K maximisant le score de silhouette moyen ; inertie reportée (méthode du coude informative).",
    }


def train(profiles: list[dict], embedder, artifacts_dir=ARTIFACTS_DIR, random_state: int = 42) -> dict:
    """Entraîne K-Means sur les embeddings des profils anonymisés et sauvegarde l'artifact."""
    embeddings, documents = embed_profiles(profiles, embedder)
    if embeddings.shape[0] < MIN_PROFILES:
        raise InsufficientDataError(
            f"Seulement {embeddings.shape[0]} profil(s) utilisable(s) ; minimum {MIN_PROFILES}."
        )

    selection = choose_k(embeddings, random_state=random_state)
    model: KMeans = selection["model"]
    labels = model.labels_

    clusters = []
    for cluster_id in range(model.n_clusters):
        members = [i for i, label in enumerate(labels) if int(label) == cluster_id]
        terms = dominant_terms(profiles, labels, cluster_id)
        description = (
            "Profils proches autour de : " + ", ".join(terms)
            if terms
            else "Profils regroupés sans terme dominant clair"
        )
        clusters.append({
            "clusterId": int(cluster_id),
            "size": len(members),
            "dominantTerms": terms,
            "clusterDescription": description,
        })

    bundle = {
        "model": model,
        "algorithm": ALGORITHM,
        "version": MODEL_VERSION,
        "embeddingModel": EMBEDDING_MODEL,
        "nClusters": int(model.n_clusters),
        "clusters": clusters,
        "trainedAt": datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "nProfiles": int(embeddings.shape[0]),
        "selection": {
            "chosenK": selection["chosenK"],
            "silhouette": selection["silhouette"],
            "trials": selection["trials"],
            "rule": selection["rule"],
        },
    }

    artifacts_dir.mkdir(parents=True, exist_ok=True)
    model_path = artifacts_dir / MODEL_FILE
    joblib.dump(bundle, model_path)
    metadata = {
        "algorithm": ALGORITHM,
        "modelVersion": MODEL_VERSION,
        "embeddingModel": EMBEDDING_MODEL,
        "nClusters": bundle["nClusters"],
        "nProfiles": bundle["nProfiles"],
        "silhouette": selection["silhouette"],
        "chosenK": selection["chosenK"],
        "artifactFile": MODEL_FILE,
        "trainedAt": bundle["trainedAt"],
        "clusters": clusters,
    }
    import json

    (artifacts_dir / METADATA_FILE).write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    logger.info(
        "K-Means entraîné : K=%s silhouette=%.4f n=%s",
        selection["chosenK"], selection["silhouette"], embeddings.shape[0],
    )
    return metadata
