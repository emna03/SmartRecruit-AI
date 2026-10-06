"""Résultat du CV Parser, validé par Pydantic avant d'être renvoyé à Spring Boot (JSON en camelCase)."""

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

EducationLevel = Literal["BAC", "BAC_PLUS_2", "BAC_PLUS_3", "BAC_PLUS_5", "DOCTORAT"]
LanguageLevel = Literal["NATIF", "BILINGUE", "COURANT", "PROFESSIONNEL", "INTERMEDIAIRE", "DEBUTANT"]
Cefr = Literal["A1", "A2", "B1", "B2", "C1", "C2"]
# « 2023 » (année seule) ou « 2023-03 » (année et mois)
PartialDate = Annotated[str, Field(pattern=r"^(19|20)\d{2}(-(0[1-9]|1[0-2]))?$")]
ShortText = Annotated[str, Field(min_length=1, max_length=150)]


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class DetectedSkill(CamelModel):
    name: Annotated[str, Field(min_length=1, max_length=100)]
    category: Annotated[str, Field(min_length=1, max_length=50)]
    occurrences: Annotated[int, Field(ge=1)]


class EducationEntry(CamelModel):
    degree: ShortText
    field_of_study: ShortText | None = None
    institution: ShortText | None = None
    start_date: PartialDate | None = None
    end_date: PartialDate | None = None
    level: EducationLevel | None = None


class ExperienceEntry(CamelModel):
    job_title: ShortText
    company: ShortText | None = None
    location: ShortText | None = None
    start_date: PartialDate | None = None
    end_date: PartialDate | None = None
    current: bool = False
    duration_months: Annotated[int, Field(ge=0, le=720)] | None = None
    description: Annotated[str, Field(max_length=1500)] | None = None


class LanguageEntry(CamelModel):
    name: Annotated[str, Field(min_length=1, max_length=50)]
    level: LanguageLevel | None = None
    cefr: Cefr | None = None
    certification: Annotated[str, Field(max_length=30)] | None = None


class CvParseResponse(CamelModel):
    parser_version: str
    method: str
    document_language: Literal["fr", "en", "inconnu"]
    page_count: Annotated[int, Field(ge=1)]
    text_length: Annotated[int, Field(ge=0)]
    sections_detected: list[str]
    skills: Annotated[list[DetectedSkill], Field(max_length=200)]
    technologies: Annotated[list[DetectedSkill], Field(max_length=200)]
    educations: Annotated[list[EducationEntry], Field(max_length=20)]
    experiences: Annotated[list[ExperienceEntry], Field(max_length=30)]
    languages: Annotated[list[LanguageEntry], Field(max_length=15)]
    total_experience_months: Annotated[int, Field(ge=0, le=720)] | None = None
    highest_education_level: EducationLevel | None = None
    warnings: list[str]
    # Texte nettoyé, emails / liens / téléphones masqués.
    text: Annotated[str, Field(max_length=100_000)]
    processing_time_ms: Annotated[int, Field(ge=0)]
