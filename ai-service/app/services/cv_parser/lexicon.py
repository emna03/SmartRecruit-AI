"""Reconnaissance des technologies et compétences par dictionnaire (gazetteer).

Méthode : chaque nom et alias du lexique ``resources/skills.json`` devient un motif. Les espaces et tirets
internes sont optionnels (« Spring Boot », « spring-boot », « springboot »). Un motif ne correspond qu'à un
mot entier : ni lettre, chiffre, « + », « # » ou « . » collé avant, ni lettre, chiffre, « + » ou « # » après
(« Java » ne correspond pas à « JavaScript », « C » ne correspond pas à « C++ », « SQL » pas à « MySQL »).
Les motifs sont essayés du plus long au plus court : « Spring Boot » l'emporte sur « Spring ».
La casse et les accents sont ignorés, sauf pour les sigles ambigus déclarés ``caseSensitive`` (« Go », « IA »).
"""

import json
import re
from collections import Counter
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from app.services.cv_parser.text import fold

LEXICON_PATH = Path(__file__).resolve().parents[2] / "resources" / "skills.json"

TECHNOLOGY = "technology"
SKILL = "skill"


@dataclass(frozen=True)
class LexiconEntry:
    name: str
    category: str
    kind: str


def _key(text: str) -> str:
    return re.sub(r"[\s\-]+", "", text)


def _pattern(alias: str) -> str:
    return r"[\s\-]*".join(re.escape(part) for part in re.split(r"[\s\-]+", alias.strip()) if part)


def _compile(aliases: list[str], case_sensitive: bool) -> re.Pattern | None:
    if not aliases:
        return None
    alternatives = "|".join(_pattern(a) for a in sorted(set(aliases), key=len, reverse=True))
    after = r"(?![\w+#.])" if case_sensitive else r"(?![\w+#])"
    return re.compile(rf"(?<![\w+#.])(?:{alternatives}){after}")


def _strip_accents(text: str) -> str:
    folded = fold(text)
    return "".join(f if f.lower() != o.lower() else o for f, o in zip(folded, text))


class Lexicon:
    def __init__(self, data: dict):
        self.version = data.get("version", "?")
        self._by_key: dict[str, LexiconEntry] = {}
        self._by_case_key: dict[str, LexiconEntry] = {}
        insensitive: list[str] = []
        sensitive: list[str] = []
        groups = [(TECHNOLOGY, data.get("technologies", {})), (SKILL, data.get("competences", {}))]
        for kind, categories in groups:
            for category, items in categories.items():
                for item in items:
                    entry = LexiconEntry(item["name"], category, kind)
                    case_sensitive = item.get("caseSensitive", [])
                    for alias in [item["name"], *item.get("aliases", [])]:
                        if alias in case_sensitive:
                            continue
                        folded = fold(alias)
                        self._by_key.setdefault(_key(folded), entry)
                        insensitive.append(folded)
                    for alias in case_sensitive:
                        stripped = _strip_accents(alias)
                        self._by_case_key.setdefault(_key(stripped), entry)
                        sensitive.append(stripped)
        self._insensitive = _compile(insensitive, case_sensitive=False)
        self._sensitive = _compile(sensitive, case_sensitive=True)

    @property
    def size(self) -> int:
        return len(set(self._by_key.values()) | set(self._by_case_key.values()))

    def find(self, text: str) -> Counter[LexiconEntry]:
        """Nombre d'occurrences de chaque entrée du lexique dans ``text``."""
        counts: Counter[LexiconEntry] = Counter()
        if self._insensitive:
            for match in self._insensitive.finditer(fold(text)):
                entry = self._by_key.get(_key(match.group(0)))
                if entry:
                    counts[entry] += 1
        if self._sensitive:
            for match in self._sensitive.finditer(_strip_accents(text)):
                entry = self._by_case_key.get(_key(match.group(0)))
                if entry:
                    counts[entry] += 1
        return counts


@lru_cache
def default_lexicon() -> Lexicon:
    return Lexicon(json.loads(LEXICON_PATH.read_text(encoding="utf-8")))
