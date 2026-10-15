"""Schémas Pydantic du module LLM (résumé professionnel)."""

from pydantic import BaseModel, ConfigDict, Field, field_validator


class LlmEducation(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    degree: str = Field(min_length=1, max_length=150)
    field_of_study: str | None = Field(default=None, max_length=150, alias="fieldOfStudy")
    institution: str | None = Field(default=None, max_length=150)
    level: str | None = Field(default=None, max_length=30)


class LlmExperience(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    job_title: str = Field(min_length=1, max_length=150, alias="jobTitle")
    company: str | None = Field(default=None, max_length=150)
    duration_months: int | None = Field(default=None, ge=0, le=720, alias="durationMonths")


class LlmLanguage(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    name: str = Field(min_length=1, max_length=50)
    level: str | None = Field(default=None, max_length=30)
    cefr: str | None = Field(default=None, max_length=5)


class ProfessionalSummaryRequest(BaseModel):
    """Données professionnelles anonymisées uniquement. Aucun champ d'identité n'est accepté."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    skills: list[str] = Field(default_factory=list, max_length=50)
    technologies: list[str] = Field(default_factory=list, max_length=80)
    educations: list[LlmEducation] = Field(default_factory=list, max_length=10)
    experiences: list[LlmExperience] = Field(default_factory=list, max_length=15)
    languages: list[LlmLanguage] = Field(default_factory=list, max_length=10)
    total_experience_months: int | None = Field(default=None, ge=0, le=720, alias="totalExperienceMonths")
    highest_education_level: str | None = Field(default=None, max_length=30, alias="highestEducationLevel")

    @field_validator("skills", "technologies", mode="before")
    @classmethod
    def _clean_names(cls, value: object) -> object:
        if not isinstance(value, list):
            return value
        cleaned: list[str] = []
        for item in value:
            if isinstance(item, str):
                text = " ".join(item.split()).strip()
                if text:
                    cleaned.append(text[:100])
        return cleaned


class ProfessionalSummaryResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    summary: str = Field(min_length=40, max_length=1200)
    keywords: list[str] = Field(min_length=1, max_length=12)
    model: str = Field(min_length=1, max_length=80)
    provider: str = Field(min_length=1, max_length=40)
    prompt_version: str = Field(min_length=1, max_length=20, alias="promptVersion")
    source: str = Field(default="llm", pattern="^llm$")

    @field_validator("keywords")
    @classmethod
    def _keywords_not_blank(cls, value: list[str]) -> list[str]:
        cleaned = [" ".join(item.split()).strip() for item in value if isinstance(item, str)]
        cleaned = [item for item in cleaned if item]
        if not cleaned:
            raise ValueError("Au moins un mot-clé non vide est requis")
        return cleaned[:12]
