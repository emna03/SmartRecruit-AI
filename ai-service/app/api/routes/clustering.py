"""Endpoints K-Means : entraînement, attribution, résumé."""

import logging

from fastapi import APIRouter, HTTPException, Request

from app.schemas.clustering import (
    ClusteringAssignRequest,
    ClusteringAssignResponse,
    ClusteringSummaryResponse,
    ClusteringTrainRequest,
    ClusteringTrainResponse,
    ClusterSummaryItem,
)
from app.schemas.common import ApiErrorResponse
from app.services.clustering.model import ClusteringUnavailableError, KMeansModel, load_clustering_model
from app.services.clustering.training import InsufficientDataError, train
from app.services.semantic.embedder import SentenceTransformerEmbedder

router = APIRouter(prefix="/ml/clustering", tags=["ml", "clustering"])
logger = logging.getLogger("smartrecruit_ai.clustering")


def _embedder(request: Request):
    embedder = getattr(request.app.state, "semantic_embedder", None)
    return embedder if embedder is not None else SentenceTransformerEmbedder()


@router.post(
    "/train",
    response_model=ClusteringTrainResponse,
    summary="Entraîne K-Means sur des profils professionnels anonymisés",
    responses={400: {"model": ApiErrorResponse}, 503: {"model": ApiErrorResponse}},
)
def train_clustering(payload: ClusteringTrainRequest, request: Request) -> ClusteringTrainResponse:
    profiles = [p.model_dump(by_alias=True) for p in payload.profiles]
    try:
        metadata = train(profiles, _embedder(request))
    except InsufficientDataError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from None
    except Exception:
        logger.exception("Échec entraînement K-Means")
        raise HTTPException(status_code=503, detail="Entraînement K-Means impossible.") from None

    request.app.state.clustering_model = load_clustering_model()
    return ClusteringTrainResponse(
        algorithm=metadata["algorithm"],
        model_version=metadata["modelVersion"],
        embedding_model=metadata["embeddingModel"],
        n_clusters=metadata["nClusters"],
        n_profiles=metadata["nProfiles"],
        silhouette=metadata["silhouette"],
        chosen_k=metadata["chosenK"],
        trained_at=metadata["trainedAt"],
        clusters=[
            ClusterSummaryItem(
                cluster_id=c["clusterId"],
                size=c["size"],
                dominant_terms=c["dominantTerms"],
                cluster_description=c["clusterDescription"],
            )
            for c in metadata["clusters"]
        ],
    )


@router.post(
    "/assign",
    response_model=ClusteringAssignResponse,
    summary="Attribue un profil anonymisé à un cluster K-Means",
    responses={400: {"model": ApiErrorResponse}, 503: {"model": ApiErrorResponse}},
)
def assign_cluster(payload: ClusteringAssignRequest, request: Request) -> ClusteringAssignResponse:
    model: KMeansModel | None = getattr(request.app.state, "clustering_model", None)
    if model is None:
        raise HTTPException(status_code=503, detail="Modèle K-Means indisponible : entraîner d'abord.")
    try:
        result = model.assign(payload.profile.model_dump(by_alias=True), _embedder(request))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from None
    except ClusteringUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from None
    return ClusteringAssignResponse(
        cluster_id=result.cluster_id,
        cluster_description=result.cluster_description,
        dominant_terms=result.dominant_terms,
        distance_to_centroid=result.distance_to_centroid,
        model_version=result.model_version,
        n_clusters=result.n_clusters,
    )


@router.get(
    "/summary",
    response_model=ClusteringSummaryResponse,
    summary="Résumé des clusters (sans données personnelles)",
)
def clustering_summary(request: Request) -> ClusteringSummaryResponse:
    model: KMeansModel | None = getattr(request.app.state, "clustering_model", None)
    if model is None:
        return ClusteringSummaryResponse(available=False, note="Aucun modèle K-Means chargé.")
    data = model.summary()
    return ClusteringSummaryResponse(
        available=True,
        algorithm=data.get("algorithm"),
        model_version=data.get("modelVersion"),
        embedding_model=data.get("embeddingModel"),
        n_clusters=data.get("nClusters"),
        n_profiles=data.get("nProfiles"),
        silhouette=data.get("silhouette"),
        chosen_k=data.get("chosenK"),
        trained_at=data.get("trainedAt"),
        clusters=[
            ClusterSummaryItem(
                cluster_id=c["clusterId"],
                size=c["size"],
                dominant_terms=c.get("dominantTerms") or [],
                cluster_description=c.get("clusterDescription") or f"Cluster {c['clusterId']}",
            )
            for c in data.get("clusters") or []
        ],
        note=data.get("note") or "",
    )
