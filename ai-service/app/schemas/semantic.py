"""Schémas du matching sémantique (Sentence Transformer). Aucune donnée d'identité."""

from typing import Annotated

from pydantic import Field

from app.schemas.matching import StrictCamelModel
from app.schemas.cv import CamelModel


class SemanticEducation(StrictCamelModel):
    degree: Annotated[str, Field(min_length=1, max_length=150)]
    field_of_study: Annotated[str, Field(default="", max_length=150)] = ""
    institution: Annotated[str, Field(default="", max_length=150)] = ""
    level: Annotated[str, Field(default="", max_length=30)] = ""


class SemanticExperience(StrictCamelModel):
    job_title: Annotated[str, Field(min_length=1, max_length=150)]
    company: Annotated[str, Field(default="", max_length=150)] = ""
    duration_months: Annotated[int, Field(default=0, ge=0, le=720)] = 0
    description: Annotated[str, Field(default="", max_length=2000)] = ""


class SemanticLanguage(StrictCamelModel):
    name: Annotated[str, Field(min_length=1, max_length=50)]
    level: Annotated[str, Field(default="", max_length=30)] = ""
    cefr: Annotated[str, Field(default="", max_length=5)] = ""


class SemanticCandidate(StrictCamelModel):
    skills: Annotated[list[str], Field(default_factory=list, max_length=200)]
    technologies: Annotated[list[str], Field(default_factory=list, max_length=200)]
    educations: Annotated[list[SemanticEducation], Field(default_factory=list, max_length=10)]
    experiences: Annotated[list[SemanticExperience], Field(default_factory=list, max_length=15)]
    languages: Annotated[list[SemanticLanguage], Field(default_factory=list, max_length=10)]
    total_experience_months: Annotated[int, Field(default=0, ge=0, le=720)] = 0
    highest_education_level: Annotated[str, Field(default="", max_length=30)] = ""


class SemanticOffer(StrictCamelModel):
    title: Annotated[str, Field(min_length=1, max_length=200)]
    required_skills: Annotated[list[str], Field(min_length=1, max_length=50)]
    description: Annotated[str, Field(default="", max_length=5000)] = ""
    min_experience_years: Annotated[int, Field(default=0, ge=0, le=50)] = 0
    education_level: Annotated[str, Field(default="", max_length=30)] = ""


class SemanticMatchingRequest(StrictCamelModel):
    candidate: SemanticCandidate
    offer: SemanticOffer


class SemanticMatchingResponse(CamelModel):
    semantic_similarity: Annotated[float, Field(ge=0, le=1)]
    semantic_score: Annotated[int, Field(ge=0, le=100)]
    model: Annotated[str, Field(min_length=1, max_length=120)]
    version: Annotated[str, Field(min_length=1, max_length=20)]
    provider: Annotated[str, Field(min_length=1, max_length=40)]
