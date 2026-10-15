"""Chargement du modèle K-Means et attribution d'un profil à un cluster."""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from pathlib import Path

import joblib
import numpy as np

from app.services.clustering.features import embed_profiles, profile_document
from app.services.clustering.training import ARTIFACTS_DIR, METADATA_FILE, MODEL_FILE

logger = logging.getLogger("smartrecruit_ai.clustering")


class ClusteringUnavailableError(Exception):
    """Modèle K-Means absent ou invalide."""


@dataclass(frozen=True)
class ClusterAssignment:
    cluster_id: int
    cluster_description: str
    dominant_terms: list[str]
    distance_to_centroid: float
    model_version: str
    n_clusters: int


class KMeansModel:
    def __init__(self, bundle: dict, metadata: dict):
        self._bundle = bundle
        self.metadata = metadata

    @classmethod
    def load(cls, directory: Path = ARTIFACTS_DIR) -> "KMeansModel":
        model_path, meta_path = directory / MODEL_FILE, directory / METADATA_FILE
        if not model_path.is_file() or not meta_path.is_file():
            raise ClusteringUnavailableError("Modèle K-Means absent : entraîner via POST /api/ml/clustering/train")
        try:
            metadata = json.loads(meta_path.read_text(encoding="utf-8"))
            bundle = joblib.load(model_path)
        except (OSError, ValueError) as error:
            raise ClusteringUnavailableError(f"Modèle K-Means illisible : {error}") from error
        if bundle.get("model") is None:
            raise ClusteringUnavailableError("Artifact K-Means incohérent")
        return cls(bundle, metadata)

    def assign(self, profile: dict, embedder) -> ClusterAssignment:
        text = profile_document(profile)
        if not text.strip():
            raise ValueError("Profil professionnel vide : clustering impossible")
        vectors, _ = embed_profiles([profile], embedder)
        if vectors.shape[0] == 0:
            raise ValueError("Impossible de construire un embedding pour ce profil")
        model = self._bundle["model"]
        cluster_id = int(model.predict(vectors)[0])
        centroid = model.cluster_centers_[cluster_id]
        distance = float(np.linalg.norm(vectors[0] - centroid))
        info = next(
            (c for c in self._bundle.get("clusters", []) if int(c["clusterId"]) == cluster_id),
            {"clusterDescription": f"Cluster {cluster_id}", "dominantTerms": []},
        )
        return ClusterAssignment(
            cluster_id=cluster_id,
            cluster_description=info.get("clusterDescription", f"Cluster {cluster_id}"),
            dominant_terms=list(info.get("dominantTerms") or []),
            distance_to_centroid=round(distance, 4),
            model_version=str(self._bundle.get("version") or self.metadata.get("modelVersion")),
            n_clusters=int(self._bundle.get("nClusters") or model.n_clusters),
        )

    def summary(self) -> dict:
        return {
            "algorithm": self.metadata.get("algorithm"),
            "modelVersion": self.metadata.get("modelVersion"),
            "embeddingModel": self.metadata.get("embeddingModel"),
            "nClusters": self.metadata.get("nClusters"),
            "nProfiles": self.metadata.get("nProfiles"),
            "silhouette": self.metadata.get("silhouette"),
            "chosenK": self.metadata.get("chosenK"),
            "trainedAt": self.metadata.get("trainedAt"),
            "clusters": self.metadata.get("clusters") or self._bundle.get("clusters") or [],
            "note": (
                "Les descriptions de clusters sont des interprétations basées sur les termes dominants, "
                "pas des labels certains."
            ),
        }


def load_clustering_model(directory: Path = ARTIFACTS_DIR) -> KMeansModel | None:
    try:
        model = KMeansModel.load(directory)
    except ClusteringUnavailableError as error:
        logger.info("Module clustering indisponible : %s", error)
        return None
    except Exception:
        logger.exception("Module clustering indisponible")
        return None
    logger.info("Modèle K-Means chargé : K=%s", model.metadata.get("nClusters"))
    return model
