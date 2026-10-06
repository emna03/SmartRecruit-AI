"""Extraction des expériences professionnelles (section « Expérience » uniquement).

1. Découpage en blocs : un nouveau bloc commence après une liste à puces, ou quand une ligne datée
   (ou contenant un intitulé de poste) suit un bloc déjà daté.
2. Dans chaque bloc : lignes d'en-tête (courtes, sans puce) -> période, poste, entreprise, lieu ;
   puces et phrases longues -> description.
3. L'en-tête est découpé sur les séparateurs (« — », « | », « , », « chez », « at ») ; la partie contenant
   un intitulé connu (« Développeur », « Stagiaire », « Engineer », ...) est le poste, une ville connue est
   le lieu, le reste l'entreprise.
"""

import re
from dataclasses import dataclass
from datetime import date

from app.services.cv_parser.dates import YearMonth, find_range, find_single_date, months_between, remove_span
from app.services.cv_parser.text import BULLET, fold

_TITLE_RE = re.compile(
    r"\b(?:developpeu(?:r|se)|developer|dev|ingenieu(?:r|e)|engineer|stagiaire|stage|intern|internship|alternant"
    r"|alternance|apprenti|chef de projet|project manager|consultant|consultante|analyste|analyst|architecte"
    r"|architect|technicien(?:ne)?|technician|data scientist|data analyst|data engineer|lead|manager|responsable"
    r"|administrat(?:eur|rice|or)|devops|designer|cto|directeu(?:r|rice)|director|assistant|scrum master"
    r"|product owner|testeu(?:r|se)|tester|qa|formateu(?:r|rice)|enseignant|teacher|freelance|fondat(?:eur|rice)"
    r"|founder|support|programmeu(?:r|se)|programmer|integrat(?:eur|rice)|webmaster)\b"
)
_LOCATION_RE = re.compile(
    r"^(?:tunis|ariana|sfax|sousse|monastir|nabeul|bizerte|gabes|ben arous|la marsa|lac \d|paris|lyon|marseille"
    r"|toulouse|lille|nantes|bordeaux|montreal|bruxelles|brussels|geneve|london|londres|berlin|madrid|casablanca"
    r"|rabat|alger|remote|teletravail|hybride|full remote|tunisie|tunisia|france|canada|maroc|morocco)\b"
)
_PART_SEPARATOR = re.compile(r"\s+[|–—-]\s+|\s*\|\s*|,\s+|\s+@\s+|\s+(?:chez|at)\s+|\(|\)")
_PREFIX_RE = re.compile(r"^(?:poste|stage|role|intitule|position)\s*:\s*", re.IGNORECASE)

MAX_EXPERIENCES = 30
MAX_HEADER_LENGTH = 100
MAX_DESCRIPTION_LENGTH = 1500


@dataclass
class Experience:
    job_title: str
    company: str | None
    location: str | None
    start: YearMonth | None
    end: YearMonth | None
    current: bool
    duration_months: int | None
    description: str | None


def _has_dates(line: str, today: date) -> bool:
    return find_range(line, today) is not None


def _blocks(lines: list[str], today: date) -> list[list[str]]:
    blocks: list[list[str]] = []
    current: list[str] = []
    dated = previous_bullet = blank_seen = False
    for line in lines:
        if not line.strip():
            blank_seen = True
            continue
        bullet = line.startswith(BULLET)
        line_dated = _has_dates(line, today)
        if current and not bullet and (
                previous_bullet
                or (dated and (line_dated or blank_seen or _TITLE_RE.search(fold(line))))):
            blocks.append(current)
            current, dated = [], False
        current.append(line)
        dated = dated or line_dated
        previous_bullet, blank_seen = bullet, False
    if current:
        blocks.append(current)
    return blocks


def _clean(text: str | None, max_length: int = 150) -> str | None:
    if text is None:
        return None
    text = re.sub(r"\s+", " ", text).strip(" -–—|,;:.()")
    return text[:max_length] if len(text) >= 2 else None


def _parse_block(block: list[str], today: date) -> Experience | None:
    header: list[str] = []
    description: list[str] = []
    for line in block:
        if line.startswith(BULLET) or len(line) > MAX_HEADER_LENGTH or len(header) >= 3:
            description.append(line.removeprefix(BULLET).strip())
        else:
            header.append(line)

    dates = None
    parts: list[str] = []
    for line in header:
        found = find_range(line, today)
        if found and dates is None:
            dates = found
            line = remove_span(line, found.span)
        elif dates is None and (single := find_single_date(line, today, future_years=1)):
            dates = single
            line = remove_span(line, single.span)
        line = _PREFIX_RE.sub("", line)
        parts.extend(p for p in (_clean(part) for part in _PART_SEPARATOR.split(line)) if p)

    title = next((p for p in parts if _TITLE_RE.search(fold(p))), None)
    location = next((p for p in parts if p is not title and _LOCATION_RE.match(fold(p))), None)
    others = [p for p in parts if p is not title and p is not location]
    if title is None and others:
        title = others.pop(0)
    if title is None:
        return None
    company = others[0] if others else None

    start = dates.start if dates else None
    end = dates.end if dates else None
    current = bool(dates and dates.current)
    if start is None and end is not None and not current:
        start = end
    duration = None
    if start is not None:
        last = YearMonth(today.year, today.month) if current else end
        duration = months_between(start, last) if last else None

    text = "\n".join(d for d in description if d)
    return Experience(
        job_title=title,
        company=company,
        location=location,
        start=start,
        end=None if current else end,
        current=current,
        duration_months=duration,
        description=text[:MAX_DESCRIPTION_LENGTH] or None,
    )


def extract_experiences(lines: list[str], today: date) -> list[Experience]:
    entries = [e for e in (_parse_block(b, today) for b in _blocks(lines, today)) if e]
    return entries[:MAX_EXPERIENCES]


def total_experience_months(experiences: list[Experience], today: date) -> int | None:
    """Durée cumulée sans double compte des périodes qui se chevauchent."""
    intervals = []
    for exp in experiences:
        if exp.start is None:
            continue
        end = YearMonth(today.year, today.month) if exp.current else (exp.end or exp.start)
        intervals.append((exp.start.index(), max(exp.start.index(), end.index())))
    if not intervals:
        return None
    total, current_start, current_end = 0, None, None
    for start, end in sorted(intervals):
        if current_end is None or start > current_end + 1:
            if current_end is not None:
                total += current_end - current_start + 1
            current_start, current_end = start, end
        else:
            current_end = max(current_end, end)
    total += current_end - current_start + 1
    return total
