"""Classification de l'intention d'une question par similarité avec des exemples (TF-IDF, sans modèle externe).

Chaque intention est décrite par des phrases d'exemple (``resources/chatbot_knowledge.json``). Une phrase est
représentée par ses mots (sans mots outils, pluriel simple retiré) et par les n-grammes de 3 à 5 caractères de
chaque mot : « candidater », « candidature » et « candidat » partagent ainsi une partie de leurs traits, et une
faute de frappe ne fait perdre que quelques n-grammes. Les traits sont pondérés par TF-IDF (un trait présent
dans peu d'exemples compte plus), puis la question est comparée à chaque exemple par similarité cosinus.

Score d'une intention = meilleure similarité avec un de ses exemples (entre 0 et 1). La question n'est pas
reconnue (le chatbot dit qu'il ne sait pas répondre) si ce score est inférieur au seuil ``THRESHOLD`` ou si la
moitié au moins de ses mots sont inconnus du vocabulaire des exemples (contrôle hors domaine : « Combien coûte un
abonnement ? »). Un mot proche d'un mot connu (faute de frappe, ressemblance ≥ 88 %) est considéré comme connu.
"""

import math
import re
from collections import Counter

from rapidfuzz import fuzz, process

from app.services.chatbot.nlp import SEARCH_WORDS, STOPWORDS
from app.services.cv_parser.text import fold

THRESHOLD = 0.30
MIN_COVERAGE = 0.5
TYPO_SIMILARITY = 88
NGRAM_SIZES = (3, 4, 5)

_TOKEN_RE = re.compile(r"[a-z0-9+#]+")
# Mots outils retirés, sauf ceux qui distinguent les intentions (« mon CV », « cette offre », « me correspondent »).
_KEPT = frozenset({"me", "moi", "mon", "ma", "mes", "ce", "cet", "cette", "pas"})
_IGNORED = STOPWORDS - _KEPT


def _stem(token: str) -> str:
    return token[:-1] if len(token) > 4 and token[-1] in "sx" else token


def tokens(text: str) -> list[str]:
    return [_stem(t) for t in _TOKEN_RE.findall(fold(text).replace("'", " ")) if t not in _IGNORED]


def features(text: str) -> Counter[str]:
    counts: Counter[str] = Counter()
    for token in tokens(text):
        counts["w:" + token] += 1
        padded = f" {token} "
        for size in NGRAM_SIZES:
            for i in range(len(padded) - size + 1):
                counts[padded[i:i + size]] += 1
    return counts


class IntentClassifier:
    def __init__(self, examples: dict[str, list[str]]):
        documents = [(intent, features(example)) for intent, phrases in examples.items() for example in phrases]
        document_frequency: Counter[str] = Counter()
        for _, counts in documents:
            document_frequency.update(counts.keys())
        self._unseen_idf = math.log(1 + len(documents)) + 1
        self._idf = {f: math.log((1 + len(documents)) / (1 + df)) + 1 for f, df in document_frequency.items()}
        self._examples = [(intent, self._vector(counts)) for intent, counts in documents]
        self._known = frozenset({f[2:] for f in document_frequency if f.startswith("w:")}
                                | {_stem(w) for w in SEARCH_WORDS})
        self._vocabulary = sorted(self._known)

    def coverage(self, text: str) -> float:
        """Part des mots de la question connus du vocabulaire (1.0 pour une question sans mot porteur de sens)."""
        words = [t for t in tokens(text) if t not in _KEPT]
        if not words:
            return 1.0
        known = sum(1 for w in words if w in self._known
                    or process.extractOne(w, self._vocabulary, scorer=fuzz.ratio, score_cutoff=TYPO_SIMILARITY))
        return known / len(words)

    def _vector(self, counts: Counter[str]) -> dict[str, float]:
        # Un trait inconnu des exemples garde le poids maximal : il éloigne la question de toutes les intentions.
        weights = {f: (1 + math.log(tf)) * self._idf.get(f, self._unseen_idf) for f, tf in counts.items()}
        norm = math.sqrt(sum(w * w for w in weights.values()))
        return {f: w / norm for f, w in weights.items()} if norm else {}

    def rank(self, text: str) -> list[tuple[str, float]]:
        """Intentions triées par score décroissant (à égalité : ordre alphabétique, donc déterministe)."""
        question = self._vector(features(text))
        best: dict[str, float] = {}
        for intent, example in self._examples:
            score = sum(w * example.get(f, 0.0) for f, w in question.items())
            best[intent] = max(best.get(intent, 0.0), score)
        return sorted(best.items(), key=lambda item: (-item[1], item[0]))
