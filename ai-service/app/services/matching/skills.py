"""Correspondance entre les compétences requises par l'offre et celles extraites du CV.

Chaque nom est d'abord ramené à son nom de référence grâce au lexique du CV Parser (« springboot », « Spring-Boot »
-> « Spring Boot » ; « k8s » -> « Kubernetes »), puis comparé sans tenir compte de la casse, des accents, des espaces
et des tirets. Pour chaque compétence requise, la meilleure correspondance est retenue :

- ``EXACT`` (crédit 1) : le CV contient la compétence ;
- ``IMPLIED`` (crédit 1) : le CV contient une compétence qui l'implique (« Spring Boot » => « Java »,
  « MySQL » => « SQL »), règles ``implies`` de ``resources/matching_rules.json`` appliquées de proche en proche ;
- ``RELATED`` (crédit 0,5) : le CV contient une technologie de la même famille (« PostgreSQL » pour « MySQL ») ;
- sinon la compétence est manquante (crédit 0).
"""

import json
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from app.services.cv_parser.lexicon import Lexicon, _key, default_lexicon
from app.services.cv_parser.text import fold

RULES_PATH = Path(__file__).resolve().parents[2] / "resources" / "matching_rules.json"

EXACT = "EXACT"
IMPLIED = "IMPLIED"
RELATED = "RELATED"
CREDITS = {EXACT: 1.0, IMPLIED: 1.0, RELATED: 0.5}


@dataclass(frozen=True)
class SkillMatch:
    name: str
    matched_by: str
    kind: str

    @property
    def credit(self) -> float:
        return CREDITS[self.kind]


@dataclass(frozen=True)
class SkillsOutcome:
    matched: list[SkillMatch]
    missing: list[str]
    required_count: int

    @property
    def score(self) -> float:
        return 100.0 * sum(m.credit for m in self.matched) / self.required_count


class SkillRules:
    def __init__(self, data: dict, lexicon: Lexicon):
        self.version = data.get("version", "?")
        self._lexicon = lexicon
        direct: dict[str, set[str]] = {}
        for source, targets in data.get("implies", {}).items():
            direct.setdefault(self.key(source), set()).update(self.key(t) for t in targets)
        self._implies = {source: self._closure(source, direct) for source in direct}
        self._families: dict[str, set[str]] = {}
        for family, members in data.get("families", {}).items():
            for member in members:
                self._families.setdefault(self.key(member), set()).add(family)

    def label(self, name: str) -> str:
        """Nom affiché : nom de référence du lexique, sinon le nom reçu nettoyé."""
        cleaned = " ".join(name.split())
        return self._lexicon.canonical(cleaned) or cleaned

    def key(self, name: str) -> str:
        return _key(fold(self.label(name)))

    def implied_by(self, key: str) -> set[str]:
        return self._implies.get(key, set())

    def families_of(self, key: str) -> set[str]:
        return self._families.get(key, set())

    @staticmethod
    def _closure(source: str, direct: dict[str, set[str]]) -> set[str]:
        seen: set[str] = set()
        pending = list(direct.get(source, ()))
        while pending:
            current = pending.pop()
            if current not in seen and current != source:
                seen.add(current)
                pending.extend(direct.get(current, ()))
        return seen


@lru_cache
def default_rules() -> SkillRules:
    return SkillRules(json.loads(RULES_PATH.read_text(encoding="utf-8")), default_lexicon())


def match_skills(required: list[str], candidate: list[str], rules: SkillRules | None = None) -> SkillsOutcome:
    rules = rules or default_rules()
    owned: dict[str, str] = {}
    for name in candidate:
        owned.setdefault(rules.key(name), rules.label(name))
    # Parcours trié : résultat identique quel que soit l'ordre des compétences reçues.
    implied: dict[str, str] = {}
    for key in sorted(owned):
        for target in sorted(rules.implied_by(key)):
            implied.setdefault(target, owned[key])

    matched: list[SkillMatch] = []
    missing: list[str] = []
    seen: set[str] = set()
    for name in required:
        key, label = rules.key(name), rules.label(name)
        if key in seen:
            continue
        seen.add(key)
        if key in owned:
            matched.append(SkillMatch(label, owned[key], EXACT))
        elif key in implied:
            matched.append(SkillMatch(label, implied[key], IMPLIED))
        elif related := _related(key, owned, rules):
            matched.append(SkillMatch(label, related, RELATED))
        else:
            missing.append(label)
    return SkillsOutcome(matched, missing, len(seen))


def _related(key: str, owned: dict[str, str], rules: SkillRules) -> str | None:
    families = rules.families_of(key)
    if not families:
        return None
    for candidate_key in sorted(owned):
        if rules.families_of(candidate_key) & families:
            return owned[candidate_key]
    return None
