"""Pipeline du CV Parser : PDF -> texte -> nettoyage -> sections -> extraction (règles + lexique) -> JSON validé.

Entièrement déterministe : même PDF (et même date du jour pour les durées « à ce jour ») => même résultat.
Aucun modèle de langage (LLM) n'est utilisé ; aucune donnée n'est conservée après la réponse.
"""

import time
from datetime import date

from app.schemas.cv import CvParseResponse, DetectedSkill, EducationEntry, ExperienceEntry, LanguageEntry
from app.services.cv_parser import errors
from app.services.cv_parser.education import degree_level, extract_educations, highest_level
from app.services.cv_parser.experience import extract_experiences, total_experience_months
from app.services.cv_parser.languages import document_language, extract_languages
from app.services.cv_parser.lexicon import SKILL, TECHNOLOGY, default_lexicon
from app.services.cv_parser.pdf_text import extract_pages
from app.services.cv_parser.sections import HEADER, lines_of, split_sections
from app.services.cv_parser.text import alnum_count, clean_text, fold, mask_personal_data

PARSER_VERSION = "1.0.0"
METHOD = ("Extraction pdfplumber (pdfminer.six) ; nettoyage Unicode ; segmentation en sections par titres "
          "(correspondance approchée rapidfuzz) ; reconnaissance des technologies et compétences par lexique "
          "(plus longue correspondance, mots entiers) ; règles et expressions régulières pour diplômes, "
          "périodes, postes et langues. Déterministe, sans LLM.")
MIN_TEXT_CHARACTERS = 30
MAX_TEXT_LENGTH = 100_000


def _skills(counts, kind: str) -> list[DetectedSkill]:
    items = [DetectedSkill(name=e.name, category=e.category, occurrences=n) for e, n in counts.items() if e.kind == kind]
    return sorted(items, key=lambda s: (-s.occurrences, s.name.lower()))


def parse_cv(data: bytes, max_pages: int, today: date | None = None) -> CvParseResponse:
    started = time.perf_counter()
    today = today or date.today()

    pages = extract_pages(data, max_pages)
    cleaned = clean_text("\n".join(pages))
    if alnum_count(cleaned) < MIN_TEXT_CHARACTERS:
        raise errors.no_text()
    text = mask_personal_data(cleaned)
    lines = text.split("\n")
    sections = split_sections(lines)
    detected = [s.key for s in sections if s.key != HEADER]
    warnings: list[str] = []

    counts = default_lexicon().find(text)
    skills, technologies = _skills(counts, SKILL), _skills(counts, TECHNOLOGY)

    education_lines = lines_of(sections, "formation")
    if not education_lines:
        education_lines = [line for line in lines if degree_level(fold(line))]
        warnings.append("Section « Formation » non détectée : diplômes recherchés dans tout le CV.")
    educations = extract_educations(education_lines, today)

    experience_lines = lines_of(sections, "experience")
    if not experience_lines:
        warnings.append("Section « Expérience » non détectée : aucune expérience extraite.")
    experiences = extract_experiences(experience_lines, today)

    language_lines = lines_of(sections, "langues")
    other_lines = [line for s in sections if s.key != "langues" for line in s.lines]
    languages = extract_languages(language_lines, other_lines)

    if not skills and not technologies:
        warnings.append("Aucune technologie ni compétence technique reconnue par le lexique.")

    return CvParseResponse(
        parser_version=PARSER_VERSION,
        method=METHOD,
        document_language=document_language(text),
        page_count=len(pages),
        text_length=len(cleaned),
        sections_detected=list(dict.fromkeys(detected)),
        skills=skills,
        technologies=technologies,
        educations=[EducationEntry(
            degree=e.degree, field_of_study=e.field_of_study, institution=e.institution,
            start_date=e.start.iso() if e.start else None, end_date=e.end.iso() if e.end else None, level=e.level,
        ) for e in educations],
        experiences=[ExperienceEntry(
            job_title=x.job_title, company=x.company, location=x.location,
            start_date=x.start.iso() if x.start else None, end_date=x.end.iso() if x.end else None,
            current=x.current, duration_months=x.duration_months, description=x.description,
        ) for x in experiences],
        languages=[LanguageEntry(name=lang.name, level=lang.level, cefr=lang.cefr, certification=lang.certification)
                   for lang in languages],
        total_experience_months=total_experience_months(experiences, today),
        highest_education_level=highest_level(educations),
        warnings=warnings,
        text=text[:MAX_TEXT_LENGTH],
        processing_time_ms=round((time.perf_counter() - started) * 1000),
    )
