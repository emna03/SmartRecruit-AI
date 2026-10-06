"""Reconnaissance des dates et périodes (français / anglais) par expressions régulières.

Formats : « 03/2023 », « mars 2023 », « Mar. 2023 », « 2023 », périodes « 2019 - 2022 », « 03/2023 – Présent »,
« depuis 2021 », « Jan 2021 to Present ». Les recherches se font sur le texte replié (``fold``) :
les positions renvoyées valent aussi pour le texte d'origine.
"""

import re
from dataclasses import dataclass
from datetime import date

from app.services.cv_parser.text import fold

MONTHS = {
    "janvier": 1, "janv": 1, "jan": 1, "january": 1,
    "fevrier": 2, "fevr": 2, "fev": 2, "feb": 2, "february": 2,
    "mars": 3, "mar": 3, "march": 3,
    "avril": 4, "avr": 4, "apr": 4, "april": 4,
    "mai": 5, "may": 5,
    "juin": 6, "jun": 6, "june": 6,
    "juillet": 7, "juil": 7, "jul": 7, "july": 7,
    "aout": 8, "aug": 8, "august": 8,
    "septembre": 9, "sept": 9, "sep": 9, "september": 9,
    "octobre": 10, "oct": 10, "october": 10,
    "novembre": 11, "nov": 11, "november": 11,
    "decembre": 12, "dec": 12, "december": 12,
}
_MONTH_NAMES = "|".join(sorted(MONTHS, key=len, reverse=True))
_YEAR = r"(?:19|20)\d{2}"
_PRESENT = (r"(?:present|aujourd'hui|aujourd hui|actuellement|actuel|en cours|current|now|today|"
            r"a ce jour|ce jour|maintenant|ongoing)")
_SEPARATOR = r"\s*(?:-|–|—|->|→|a|au|to|until|jusqu'a|jusqu'au)\s*"


def _date(suffix: str) -> str:
    return (rf"(?:(?P<mn{suffix}>{_MONTH_NAMES})\.?\s+(?P<yn{suffix}>{_YEAR})"
            rf"|(?P<mm{suffix}>0?[1-9]|1[0-2])\s*[/.-]\s*(?P<ym{suffix}>{_YEAR})"
            rf"|(?P<y{suffix}>{_YEAR}))")


_RANGE_RE = re.compile(
    rf"(?<![\w/])(?:(?:depuis|since|de|du|from)\s+)?{_date('s')}{_SEPARATOR}"
    rf"(?:{_date('e')}|(?P<present>{_PRESENT}))(?![\w/])"
)
_SINCE_RE = re.compile(rf"(?<![\w/])(?:depuis|since)\s+{_date('s')}(?![\w/])")
_SINGLE_RE = re.compile(rf"(?<![\w/]){_date('s')}(?![\w/])")


@dataclass(frozen=True, order=True)
class YearMonth:
    year: int
    month: int | None = None

    def iso(self) -> str:
        return f"{self.year:04d}" if self.month is None else f"{self.year:04d}-{self.month:02d}"

    def index(self, default_month: int = 6) -> int:
        """Rang en mois ; mois inconnu : milieu d'année (estimation neutre, documentée)."""
        return self.year * 12 + (self.month or default_month) - 1


@dataclass(frozen=True)
class DateRange:
    start: YearMonth | None
    end: YearMonth | None
    current: bool
    span: tuple[int, int]


def _extract(match: re.Match, suffix: str) -> YearMonth | None:
    groups = match.groupdict()
    if groups.get(f"mn{suffix}"):
        return YearMonth(int(groups[f"yn{suffix}"]), MONTHS[groups[f"mn{suffix}"]])
    if groups.get(f"mm{suffix}"):
        return YearMonth(int(groups[f"ym{suffix}"]), int(groups[f"mm{suffix}"]))
    if groups.get(f"y{suffix}"):
        return YearMonth(int(groups[f"y{suffix}"]))
    return None


def _plausible(value: YearMonth | None, today: date, future_years: int) -> bool:
    return value is None or 1950 <= value.year <= today.year + future_years


def find_range(line: str, today: date, future_years: int = 1) -> DateRange | None:
    """Première période de la ligne (début - fin / présent, ou « depuis ... »)."""
    folded = fold(line)
    for match in _RANGE_RE.finditer(folded):
        start, end = _extract(match, "s"), _extract(match, "e")
        current = match.group("present") is not None
        if not (_plausible(start, today, future_years) and _plausible(end, today, future_years)):
            continue
        if start and end and end.index(12) < start.index(1):
            continue
        return DateRange(start, end, current, match.span())
    match = _SINCE_RE.search(folded)
    if match:
        start = _extract(match, "s")
        if _plausible(start, today, future_years):
            return DateRange(start, None, True, match.span())
    return None


def find_single_date(line: str, today: date, future_years: int = 6) -> DateRange | None:
    """Date isolée (« 2022 », « 06/2022 ») : interprétée comme une date de fin."""
    for match in _SINGLE_RE.finditer(fold(line)):
        value = _extract(match, "s")
        if _plausible(value, today, future_years):
            return DateRange(None, value, False, match.span())
    return None


def has_date(line: str, today: date) -> bool:
    return find_range(line, today) is not None or find_single_date(line, today) is not None


def remove_span(line: str, span: tuple[int, int]) -> str:
    return (line[:span[0]] + " " + line[span[1]:]).strip()


def months_between(start: YearMonth, end: YearMonth) -> int:
    return max(0, end.index() - start.index() + 1)
