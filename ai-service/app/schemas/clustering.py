"""Schémas K-Means : profils anonymisés uniquement."""

from typing import Annotated

from pydantic import Field

from app.schemas.cv import CamelModel
from app.schemas.matching import StrictCamelModel
from app.schemas.semantic import SemanticEducation, SemanticExperience, SemanticLanguage


class ClusterProfile(StrictCamelModel):
    skills: Annotated[list[str], Field(default_factory=list, max_length=200)]
    technologies: Annotated[list[str], Field(default_factory=list, max_length=200)]
    educations: Annotated[list[SemanticEducation], Field(default_factory=list, max_length=10)]
    experiences: Annotated[list[SemanticExperience], Field(default_factory=list, max_length=15)]
    languages: Annotated[list[SemanticLanguage], Field(default_factory=list, max_length=10)]
    total_experience_months: Annotated[int, Field(default=0, ge=0, le=720)] = 0
    highest_education_level: Annotated[str, Field(default="", max_length=30)] = ""


class ClusteringTrainRequest(StrictCamelModel):
    profiles: Annotated[list[ClusterProfile], Field(min_length=1, max_length=500)]


class ClusterSummaryItem(CamelModel):
    cluster_id: Annotated[int, Field(ge=0)]
    size: Annotated[int, Field(ge=0)]
    dominant_terms: list[str]
    cluster_description: str


class ClusteringTrainResponse(CamelModel):
    algorithm: str
    model_version: str
    embedding_model: str
    n_clusters: int
    n_profiles: int
    silhouette: float
    chosen_k: int
    trained_at: str
    clusters: list[ClusterSummaryItem]
    note: str = (
        "Les descriptions sont des interprétations (termes dominants), pas des labels certains."
    )


class ClusteringAssignRequest(StrictCamelModel):
    profile: ClusterProfile


class ClusteringAssignResponse(CamelModel):
    cluster_id: Annotated[int, Field(ge=0)]
    cluster_description: str
    dominant_terms: list[str]
    distance_to_centroid: Annotated[float, Field(ge=0)]
    model_version: str
    n_clusters: Annotated[int, Field(ge=1)]


class ClusteringSummaryResponse(CamelModel):
    algorithm: str | None = None
    model_version: str | None = None
    embedding_model: str | None = None
    n_clusters: int | None = None
    n_profiles: int | None = None
    silhouette: float | None = None
    chosen_k: int | None = None
    trained_at: str | None = None
    clusters: list[ClusterSummaryItem] = []
    note: str = ""
    available: bool = False
