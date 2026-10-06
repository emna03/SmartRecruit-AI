"""Extraction des langues parlées et de leur niveau.

Dans la section « Langues » : toute langue connue est retenue. Ailleurs, une langue n'est retenue que
si un niveau l'accompagne (« Anglais : B2 », « English (fluent) ») pour éviter « entreprise française ».
Niveau : mots-clés (« langue maternelle », « courant », « intermediate », ...) et niveau CECRL (A1 à C2)
lus entre le nom de la langue et la langue suivante. Aucun niveau n'est déduit s'il n'est pas écrit.
"""

import re
from dataclasses import dataclass

from app.services.cv_parser.text import fold

LANGUAGES: dict[str, list[str]] = {
    "Français": ["francais", "french"],
    "Anglais": ["anglais", "english"],
    "Arabe": ["arabe", "arabic"],
    "Allemand": ["allemand", "german", "deutsch"],
    "Espagnol": ["espagnol", "spanish", "espanol"],
    "Italien": ["italien", "italian"],
    "Portugais": ["portugais", "portuguese"],
    "Chinois": ["chinois", "chinese", "mandarin"],
    "Japonais": ["japonais", "japanese"],
    "Russe": ["russe", "russian"],
    "Turc": ["turc", "turkish"],
    "Néerlandais": ["neerlandais", "dutch"],
    "Coréen": ["coreen", "korean"],
}
_ALIAS_TO_LANGUAGE = {alias: name for name, aliases in LANGUAGES.items() for alias in aliases}
_LANGUAGE_RE = re.compile(r"\b(" + "|".join(sorted(_ALIAS_TO_LANGUAGE, key=len, reverse=True)) + r")\b")

LEVELS: list[tuple[str, re.Pattern]] = [
    ("NATIF", re.compile(r"\b(?:langue maternelle|maternelle|natif|native|mother tongue|native speaker)\b")),
    ("BILINGUE", re.compile(r"\b(?:bilingue|bilingual)\b")),
    ("COURANT", re.compile(r"\b(?:courant|couramment|fluent|fluently|full professional)\b")),
    ("PROFESSIONNEL", re.compile(r"\b(?:professionnel|professional(?: working)?|operationnel)\b")),
    ("INTERMEDIAIRE", re.compile(r"\b(?:intermediaire|intermediate|conversational|moyen)\b")),
    ("DEBUTANT", re.compile(r"\b(?:notions?|debutant|beginner|basic|elementary|scolaire|basique)\b")),
]
_CEFR_RE = re.compile(r"\b([abc][12])\b")
_CERTIFICATION_RE = re.compile(r"\b(toeic|toefl|ielts|delf|dalf|tcf)\b\s*:?\s*([a-c]?[12]|\d{1,3}(?:[.,]\d)?)?")

MAX_LANGUAGES = 15


@dataclass
class Language:
    name: str
    level: str | None
    cefr: str | None
    certification: str | None


def _level(segment: str) -> tuple[str | None, str | None, str | None]:
    level = next((name for name, pattern in LEVELS if pattern.search(segment)), None)
    cefr_match = _CEFR_RE.search(segment)
    cert_match = _CERTIFICATION_RE.search(segment)
    certification = None
    if cert_match:
        certification = cert_match.group(1).upper() + (f" {cert_match.group(2).upper()}" if cert_match.group(2) else "")
    return level, cefr_match.group(1).upper() if cefr_match else None, certification


def extract_languages(section_lines: list[str], other_lines: list[str]) -> list[Language]:
    found: dict[str, Language] = {}
    for lines, require_level in ((section_lines, False), (other_lines, True)):
        for line in lines:
            folded = fold(line)
            matches = list(_LANGUAGE_RE.finditer(folded))
            for i, match in enumerate(matches):
                end = matches[i + 1].start() if i + 1 < len(matches) else len(folded)
                level, cefr, certification = _level(folded[match.end():end])
                if require_level and not (level or cefr or certification):
                    continue
                name = _ALIAS_TO_LANGUAGE[match.group(1)]
                known = found.get(name)
                if known is None:
                    found[name] = Language(name, level, cefr, certification)
                else:
                    known.level = known.level or level
                    known.cefr = known.cefr or cefr
                    known.certification = known.certification or certification
    return list(found.values())[:MAX_LANGUAGES]


_FRENCH_WORDS = {"le", "la", "les", "des", "et", "du", "de", "pour", "avec", "dans", "une", "un", "sur", "au",
                 "aux", "est", "par", "ou", "chez", "depuis", "mise", "gestion", "developpement"}
_ENGLISH_WORDS = {"the", "and", "of", "for", "with", "in", "to", "a", "an", "on", "at", "as", "by", "is",
                  "developed", "using", "built", "management", "development"}


def document_language(text: str) -> str:
    words = re.findall(r"[a-z']+", fold(text))
    french = sum(w in _FRENCH_WORDS for w in words)
    english = sum(w in _ENGLISH_WORDS for w in words)
    if french + english < 5:
        return "inconnu"
    if french >= 1.5 * english:
        return "fr"
    if english >= 1.5 * french:
        return "en"
    return "inconnu"
