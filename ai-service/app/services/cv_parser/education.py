"""Extraction des formations : diplôme, domaine, établissement, dates, niveau (BAC ... DOCTORAT).

Règles : une ligne contenant un mot-clé de diplôme (« Master », « Licence », « Diplôme d'ingénieur »,
« Bachelor », « BTS », ...) ouvre une formation ; une ligne contenant un mot-clé d'établissement
(« Université », « École », « Institut », ...) donne l'établissement ; une période ou une année isolée
donne les dates. Les mises en page « diplôme puis établissement » et « dates puis diplôme » sont gérées.
"""

import re
from dataclasses import dataclass
from datetime import date

from app.services.cv_parser.dates import DateRange, YearMonth, find_range, find_single_date, remove_span
from app.services.cv_parser.experience import _LOCATION_RE
from app.services.cv_parser.text import BULLET, fold

# Du plus élevé au plus bas : le premier motif trouvé donne le niveau.
DEGREE_LEVELS: list[tuple[re.Pattern, str]] = [
    (re.compile(r"\b(?:doctorat|ph\.?\s?d\b\.?|doctorate|these de doctorat)"), "DOCTORAT"),
    (re.compile(r"\b(?:master|mastere|m\.?sc\b|mba\b|diplome (?:national )?d'ingenieur|cycle (?:d')?ingenieur"
                r"|ingenieur en|ingenieur d'etat|engineering degree|engineer'?s degree|dess\b|dea\b|bac\s*\+\s*5)"),
     "BAC_PLUS_5"),
    (re.compile(r"\b(?:licence|bachelor|b\.?sc\b|bac\s*\+\s*3)"), "BAC_PLUS_3"),
    (re.compile(r"\b(?:bts\b|dut\b|deug\b|deust\b|classes? preparatoires?|cycle preparatoire|associate degree"
                r"|bac\s*\+\s*2)"), "BAC_PLUS_2"),
    (re.compile(r"\b(?:baccalaureat|bac\b(?!\s*\+)|high school diploma|a-levels?\b)"), "BAC"),
]
LEVEL_RANK = {"BAC": 0, "BAC_PLUS_2": 1, "BAC_PLUS_3": 2, "BAC_PLUS_5": 3, "DOCTORAT": 4}

_INSTITUTION_RE = re.compile(
    r"\b(?:universite|university|univ\.|ecole|school|institut|institute|faculte|faculty|college|lycee"
    r"|academie|academy|iset|insat|enit|ensi|esprit|isg|ihec|polytech\w*|centre de formation|campus)\b"
)
_FIELD_MARKERS = [re.compile(r"\b(?:en|in|specialite|option|filiere|major|parcours)\s*:?\s+"), re.compile(r"\bof\s+")]
_PART_SEPARATOR = re.compile(r"\s+[|–—-]\s+|\s*\|\s*|,\s+|\s+@\s+|\s+at\s+|\(|\)")

MAX_EDUCATIONS = 20


@dataclass
class Education:
    degree: str | None = None
    field_of_study: str | None = None
    institution: str | None = None
    start: YearMonth | None = None
    end: YearMonth | None = None
    level: str | None = None


_TRAILING_CONNECTOR = re.compile(r"(?:\s+(?:en|in|a|à|at|de|du|des|au|of|obtenu|obtenue))+\s*$", re.IGNORECASE)


def degree_level(folded: str) -> str | None:
    for pattern, level in DEGREE_LEVELS:
        if pattern.search(folded):
            return level
    return None


def _degree_match(folded: str) -> re.Match | None:
    for pattern, _ in DEGREE_LEVELS:
        match = pattern.search(folded)
        if match:
            return match
    return None


def _split_degree(part: str) -> tuple[str | None, str | None]:
    """« J'ai obtenu une Licence ... à l'ISET X » -> (« Licence ... », « ISET X »)."""
    match = _degree_match(fold(part))
    degree = part[match.start():] if match else part
    institution = None
    inst = _INSTITUTION_RE.search(fold(degree))
    if inst and inst.start() > 0:
        institution = _clean(degree[inst.start():])
        degree = degree[:inst.start()]
    degree = _TRAILING_CONNECTOR.sub("", re.sub(r"\s+l'$", "", degree.strip()))
    return _clean(degree), institution


def _clean(text: str | None, max_length: int = 150) -> str | None:
    if text is None:
        return None
    text = re.sub(r"\s+", " ", text).strip(" -–—|,;:.()")
    return text[:max_length] if len(text) >= 2 else None


def _field_of_study(degree: str) -> str | None:
    folded = fold(degree)
    for marker in _FIELD_MARKERS:
        match = marker.search(folded)
        if match:
            # Le domaine s'arrête à « en » / « in » suivant (« Master of Science in Data Science »).
            rest = degree[match.end():]
            inner = _FIELD_MARKERS[0].search(fold(rest)) if marker is _FIELD_MARKERS[1] else None
            return _clean(rest[inner.end():] if inner else rest)
    return None


def _parts(text: str) -> list[str]:
    return [p for p in (_clean(part) for part in _PART_SEPARATOR.split(text)) if p]


def _dates(line: str, today: date) -> DateRange | None:
    return find_range(line, today, future_years=6) or find_single_date(line, today)


def extract_educations(lines: list[str], today: date) -> list[Education]:
    entries: list[Education] = []
    current: Education | None = None

    def flush() -> None:
        if current is not None and current.degree:
            entries.append(current)

    for raw in lines:
        line = raw.removeprefix(BULLET).strip()
        if not line:
            continue
        dates = _dates(line, today)
        text = remove_span(line, dates.span) if dates else line
        parts = _parts(text)
        raw_degree = next((p for p in parts if degree_level(fold(p))), None)
        institution_part = next((p for p in parts if p is not raw_degree and _INSTITUTION_RE.search(fold(p))), None)
        degree_part = None
        if raw_degree:
            degree_part, embedded_institution = _split_degree(raw_degree)
            institution_part = institution_part or embedded_institution
            others = [p for p in parts if p is not raw_degree and not _LOCATION_RE.match(fold(p))]
            if not institution_part and len(others) == 1 and not raw.startswith(BULLET):
                institution_part = others[0]

        if degree_part:
            if current is not None and current.degree:
                flush()
                current = None
            current = current or Education()
            current.degree = degree_part
            current.level = degree_level(fold(degree_part))
            current.field_of_study = _field_of_study(degree_part)
        if institution_part:
            if current is not None and current.institution and current.degree:
                flush()
                current = None
            current = current or Education()
            current.institution = current.institution or institution_part
        if dates:
            if current is not None and (current.start or current.end) and current.degree:
                flush()
                current = None
            current = current or Education()
            if not (current.start or current.end):
                current.start, current.end = dates.start, dates.end
    flush()
    return entries[:MAX_EDUCATIONS]


def highest_level(educations: list[Education]) -> str | None:
    levels = [e.level for e in educations if e.level]
    return max(levels, key=LEVEL_RANK.__getitem__) if levels else None
