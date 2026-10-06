"""Découpage du CV en sections à partir des titres (« Expérience professionnelle », « Skills », ...).

Un titre est une ligne courte, sans chiffre, égale (après repliement) à un titre connu ou proche
(similarité ``rapidfuzz.fuzz.ratio`` >= 88, pour tolérer pluriels, fautes et variantes).
Une ligne « Langues : Français, Anglais » ouvre la section et garde son contenu.
"""

import re
from dataclasses import dataclass, field

from rapidfuzz import fuzz, process

from app.services.cv_parser.text import BULLET, fold

HEADER = "entete"

SECTION_TITLES: dict[str, list[str]] = {
    "profil": ["profil", "profile", "resume", "summary", "a propos", "a propos de moi", "about me", "objectif",
               "objective", "profil professionnel", "professional summary", "presentation"],
    "experience": ["experience", "experiences", "experience professionnelle", "experiences professionnelles",
                   "parcours professionnel", "work experience", "professional experience", "employment history",
                   "work history", "experience et stages", "experiences et stages", "stages et experiences",
                   "emplois", "employment"],
    "formation": ["formation", "formations", "education", "etudes", "diplomes", "diplomes et formations",
                  "cursus", "parcours academique", "parcours scolaire", "academic background",
                  "formation academique", "cursus universitaire", "education and training"],
    "competences": ["competences", "competences techniques", "skills", "technical skills", "hard skills",
                    "technologies", "outils", "competences informatiques", "stack technique",
                    "connaissances techniques", "it skills", "expertise", "outils et technologies",
                    "competences cles", "key skills"],
    "langues": ["langues", "languages", "competences linguistiques", "language skills", "langues etrangeres"],
    "projets": ["projets", "projects", "projets academiques", "projets personnels", "academic projects",
                "personal projects", "realisations", "projets realises"],
    "certifications": ["certifications", "certificats", "certificates", "licenses and certifications",
                       "certifications et formations"],
    "interets": ["centres d'interet", "loisirs", "interests", "hobbies", "activites",
                 "vie associative", "activites extra-professionnelles", "activites associatives"],
    "contact": ["contact", "coordonnees", "informations personnelles", "personal information",
                "contact information", "informations"],
}
_TITLE_TO_SECTION = {title: key for key, titles in SECTION_TITLES.items() for title in titles}
_ALL_TITLES = list(_TITLE_TO_SECTION)
_FUZZY_CUTOFF = 88


@dataclass
class Section:
    key: str
    lines: list[str] = field(default_factory=list)


def _normalize_title(text: str) -> str:
    text = fold(text).replace("&", " and ")
    text = re.sub(r"[^a-z0-9' -]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def match_title(text: str, fuzzy: bool = True) -> str | None:
    candidate = _normalize_title(text.strip().removeprefix(BULLET).strip(" :#"))
    if not candidate or len(candidate) > 45 or len(candidate.split()) > 5 or re.search(r"\d", candidate):
        return None
    if candidate in _TITLE_TO_SECTION:
        return _TITLE_TO_SECTION[candidate]
    if fuzzy:
        best = process.extractOne(candidate, _ALL_TITLES, scorer=fuzz.ratio, score_cutoff=_FUZZY_CUTOFF)
        if best:
            return _TITLE_TO_SECTION[best[0]]
    return None


def _heading(line: str) -> tuple[str | None, str | None]:
    """(section, contenu restant) si la ligne est un titre, sinon (None, None)."""
    if line.startswith(BULLET):
        return None, None
    key = match_title(line)
    if key:
        return key, None
    if ":" in line:
        label, rest = line.split(":", 1)
        if len(label.split()) <= 4:
            key = match_title(label, fuzzy=False)
            if key:
                return key, rest.strip() or None
    return None, None


def split_sections(lines: list[str]) -> list[Section]:
    sections = [Section(HEADER)]
    for line in lines:
        key, rest = _heading(line)
        if key:
            sections.append(Section(key, [rest] if rest else []))
        else:
            sections[-1].lines.append(line)
    return [s for s in sections if s.lines or s.key != HEADER]


def lines_of(sections: list[Section], key: str) -> list[str]:
    return [line for s in sections if s.key == key for line in s.lines]
