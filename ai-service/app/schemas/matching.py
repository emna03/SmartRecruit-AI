"""Entrée et sortie du matching CV ↔ offre (JSON en camelCase).

L'entrée ne contient que ce qui sert au calcul : noms de compétences, durée d'expérience et niveau d'études.
Aucune donnée d'identité ni texte du CV ; tout champ supplémentaire est refusé.
"""

from typing import Annotated, Literal

from pydantic import ConfigDict, Field

from app.schemas.cv import CamelModel, EducationLevel

SkillName = Annotated[str, Field(min_length=1, max_length=100)]
Score = Annotated[float, Field(ge=0, le=100)]
Weight = Annotated[float, Field(ge=0, le=1)]
MatchType = Literal["EXACT", "IMPLIED", "RELATED"]


class StrictCamelModel(CamelModel):
    model_config = ConfigDict(extra="forbid")


class MatchingCandidate(StrictCamelModel):
    skills: Annotated[list[SkillName], Field(max_length=400)]
    experience_months: Annotated[int, Field(ge=0, le=720)] | None = None
    education_level: EducationLevel | None = None


class MatchingOffer(StrictCamelModel):
    required_skills: Annotated[list[SkillName], Field(min_length=1, max_length=50)]
    min_experience_years: Annotated[int, Field(ge=0, le=50)] = 0
    education_level: EducationLevel | None = None


class MatchingRequest(StrictCamelModel):
    candidate: MatchingCandidate
    offer: MatchingOffer


class MatchedSkill(CamelModel):
    name: SkillName
    matched_by: SkillName
    match_type: MatchType
    credit: Annotated[float, Field(gt=0, le=1)]


class MatchingWeights(CamelModel):
    skills: Weight
    experience: Weight | None
    education: Weight | None


class MatchingResponse(CamelModel):
    algorithm_version: Annotated[str, Field(min_length=1, max_length=20)]
    score: Score
    skills_score: Score
    experience_score: Score | None
    education_score: Score | None
    weights: MatchingWeights
    required_skills_count: Annotated[int, Field(ge=1, le=50)]
    matched_skills: Annotated[list[MatchedSkill], Field(max_length=50)]
    missing_skills: Annotated[list[SkillName], Field(max_length=50)]
    explanation: Annotated[str, Field(min_length=1, max_length=4000)]
