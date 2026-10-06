"""Analyse d'une question : normalisation et extraction des informations utiles (entités).

Étapes, toutes déterministes :

1. normalisation : minuscules sans accents (``fold``, même longueur que le texte d'origine) ;
2. entités, chacune repérée avec sa position :
   - compétences / technologies : lexique du CV Parser (``skills.json``, alias compris : « springboot », « k8s »)
     complété par les compétences des offres reçues absentes du lexique ;
   - titre d'offre cité (exact, ou approché à 90 % près pour les titres longs) ;
   - nombre d'années d'expérience (« 3 ans », « deux années ») ou profil débutant (« junior », « sans expérience ») ;
   - niveau d'études (« master », « bac+3 », « licence »...) ;
   - type de contrat (« CDI », « stage », « alternance »...) ;
   - ville, si elle figure dans au moins une offre reçue ;
3. texte « masqué » pour la classification d'intention : chaque entité est remplacée par un marqueur
   (``xskill``, ``xnombre``, ``xdiplome``, ``xcontrat``, ``xville``, ``xtitre``) afin que « offres Java »
   et « offres Python » soient reconnues comme la même intention ;
4. mots-clés restants (hors mots outils et vocabulaire de recherche), utilisés pour chercher dans les offres.
"""

import re
from dataclasses import dataclass, field

from rapidfuzz import fuzz

from app.schemas.chatbot import ChatbotOffer
from app.services.cv_parser.lexicon import Lexicon, default_lexicon
from app.services.cv_parser.text import fold
from app.services.matching.skills import SkillRules, default_rules

SKILL, TITLE, EXPERIENCE, EDUCATION, CONTRACT, LOCATION = "xskill", "xtitre", "xnombre", "xdiplome", "xcontrat", "xville"

_NUMBER_WORDS = {"un": 1, "une": 1, "deux": 2, "trois": 3, "quatre": 4, "cinq": 5, "six": 6, "sept": 7, "huit": 8,
                 "neuf": 9, "dix": 10}
_YEARS_RE = re.compile(r"(?<![\w+])(\d{1,2}|" + "|".join(_NUMBER_WORDS) + r")\s*(?:\+\s*)?(?:ans|an|annees|annee|years|year)\b")
_BEGINNER_RE = re.compile(r"\b(?:debutante?s?|juniors?|jeunes? diplomee?s?|sans experience|aucune experience"
                          r"|premier emploi|pas d'experience|peu d'experience)\b")

# Du plus élevé au plus bas : le premier motif trouvé donne le niveau.
_EDUCATION_PATTERNS: list[tuple[re.Pattern, str]] = [
    (re.compile(r"\b(?:doctorat|phd|ph\.d|these)\b"), "DOCTORAT"),
    (re.compile(r"\b(?:master|mastere|msc|mba|bac\s*\+\s*5|diplome d'ingenieur|ingenieur diplome|cycle d'ingenieur)"),
     "BAC_PLUS_5"),
    (re.compile(r"\b(?:licence|bachelor|bac\s*\+\s*3)"), "BAC_PLUS_3"),
    (re.compile(r"\b(?:bts|dut|deug|bac\s*\+\s*2)\b"), "BAC_PLUS_2"),
    (re.compile(r"\b(?:baccalaureat|bac)\b(?!\s*\+)"), "BAC"),
]

_CONTRACT_PATTERNS: list[tuple[re.Pattern, str]] = [
    (re.compile(r"\bcdi\b"), "CDI"),
    (re.compile(r"\bcdd\b"), "CDD"),
    (re.compile(r"\b(?:stages?|stagiaires?|internships?|pfe)\b"), "STAGE"),
    (re.compile(r"\b(?:alternance|alternante?s?|apprentissage|apprentie?s?)\b"), "ALTERNANCE"),
    (re.compile(r"\b(?:freelances?|free-lance|freelancers?|independante?s?)\b"), "FREELANCE"),
]

# Titre cité explicitement : entre guillemets, ou après « l'offre » / « le poste » / « l'annonce ».
_QUOTED_RE = re.compile(r"[«\"“]\s*([^«»\"“”]{3,80}?)\s*[»\"”]")
_AFTER_OFFER_RE = re.compile(r"\b(?:l'offre|le poste|l'annonce)\s+(?:de\s+|d'|intitulee\s+|:\s*)?([^?!.;]{3,80})")
_NOT_A_TITLE = re.compile(r"^(?:en question|actuelle?|ci-dessus|selectionnee?|choisie?|que|qui|dont|ou|pour|est|a|en)\b")

STOPWORDS = frozenset("""
    a au aux avec c ca ce ces cet cette d de des du en et est etre il ils elle elles j je l la le les leur leurs m ma
    me mes moi mon n ne nos notre nous on ou par pas pour qu que qui s sa se ses si son sont sur t ta te tes toi ton
    tu un une vos votre vous y the an of to for in on is are do does i my me it be ai as avons avez ont suis es
    peux peut puis-je plait est-ce
""".split())

# Vocabulaire de la recherche d'offres : ne constitue pas un critère de recherche.
SEARCH_WORDS = frozenset("""
    offre offres poste postes emploi emplois job jobs travail travailler annonce annonces opportunite opportunites
    mission missions recrutement recrute recrutent cherche cherches recherche recherches chercher rechercher
    trouve trouver trouves montre montrer affiche afficher liste lister donne donner propose proposes proposez
    proposees existe existent avez disponible disponibles ouvert ouverts ouverte ouvertes publiee publiees actuelle
    actuelles actuel nouvelles nouvelle dernieres derniere recentes recente quel quels quelle quelles combien
    correspond correspondent correspondant correspondre demande demandent demandant exige exigent requiert
    requierent necessite necessitent utilise utilisent veux voudrais aimerais souhaite faire profil poste type
    contrat ville entreprise entreprises societe chez tout toutes tous moins plus experience annees ans an niveau
    xskill xtitre xnombre xdiplome xcontrat xville developpeur developpeurs developpeuse developer dev ingenieur
    ingenieurs engineer y-a-t-il a-t-il t-il dans qui debutant debutants debutante debutantes junior juniors jeune
    jeunes diplome diplomes diplomee sans aucune premier peu ayant avoir connais connaissant maitrise maitrisant
    competence competences technologie technologies techno langage outil outils domaine secteur metier metiers
    svp stp merci bien bonjour salut
""".split())

_TOKEN_RE = re.compile(r"[a-z0-9][a-z0-9+#.\-]*")
_MIN_FUZZY_TITLE = 10
_FUZZY_THRESHOLD = 90


@dataclass(frozen=True)
class Span:
    start: int
    end: int
    kind: str
    value: str


@dataclass
class Analysis:
    text: str
    folded: str
    masked: str
    skills: list[str] = field(default_factory=list)
    """Compétences citées hors titre d'offre (ordre d'apparition, sans doublon)."""
    all_skills: list[str] = field(default_factory=list)
    """Compétences citées, y compris dans un titre d'offre (utilisées pour la recherche)."""
    experience_years: int | None = None
    education_level: str | None = None
    contract_type: str | None = None
    location: str | None = None
    mentioned_offer_ids: list[int] = field(default_factory=list)
    explicit_title: str | None = None
    keywords: list[str] = field(default_factory=list)


def _boundary_pattern(folded_phrase: str) -> re.Pattern:
    parts = [re.escape(p) for p in folded_phrase.split()]
    return re.compile(r"(?<![\w+#])" + r"\s+".join(parts) + r"(?![\w+#])")


def _non_overlapping(spans: list[Span]) -> list[Span]:
    """Garde les entités les plus longues quand elles se chevauchent."""
    kept: list[Span] = []
    for span in sorted(spans, key=lambda s: (-(s.end - s.start), s.start)):
        if all(span.end <= k.start or span.start >= k.end for k in kept):
            kept.append(span)
    return sorted(kept, key=lambda s: s.start)


def _skill_spans(text: str, folded: str, offers: list[ChatbotOffer], lexicon: Lexicon,
                 rules: SkillRules) -> list[Span]:
    spans = []
    for start, end, entry in lexicon.find_spans(text):
        # « C'est », « D'abord » : une lettre suivie d'une apostrophe est une élision, pas le langage C.
        if end - start == 1 and folded[end:end + 1] == "'":
            continue
        spans.append(Span(start, end, SKILL, entry.name))
    known = {rules.key(span.value) for span in spans}
    for name in sorted({s for offer in offers for s in offer.required_skills}):
        if lexicon.canonical(name) is not None or rules.key(name) in known or len(name.strip()) < 2:
            continue
        for match in _boundary_pattern(fold(" ".join(name.split()))).finditer(folded):
            spans.append(Span(match.start(), match.end(), SKILL, " ".join(name.split())))
    return spans


def _title_spans(folded: str, offers: list[ChatbotOffer]) -> list[Span]:
    spans = []
    for offer in offers:
        title = " ".join(fold(offer.title).split())
        if len(title) < 4:
            continue
        for match in _boundary_pattern(title).finditer(folded):
            spans.append(Span(match.start(), match.end(), TITLE, str(offer.id)))
    if spans:
        return spans
    # Titre légèrement différent (faute de frappe, mot manquant) : seulement pour les titres assez longs.
    for offer in offers:
        title = " ".join(fold(offer.title).split())
        # Le titre doit tenir dans la question (sinon « Java » seul correspondrait à « Développeur Java »).
        if len(title) < _MIN_FUZZY_TITLE or len(title) > len(folded):
            continue
        alignment = fuzz.partial_ratio_alignment(title, folded)
        if (alignment is not None and alignment.score >= _FUZZY_THRESHOLD
                and alignment.dest_end - alignment.dest_start >= 0.8 * len(title)):
            spans.append(Span(alignment.dest_start, alignment.dest_end, TITLE, str(offer.id)))
    return spans


def _experience(folded: str) -> tuple[int | None, list[Span]]:
    match = _YEARS_RE.search(folded)
    if match:
        raw = match.group(1)
        years = int(raw) if raw.isdigit() else _NUMBER_WORDS[raw]
        if years <= 50:
            return years, [Span(match.start(), match.end(), EXPERIENCE, str(years))]
    if _BEGINNER_RE.search(folded):
        return 0, []
    return None, []


def _first(patterns: list[tuple[re.Pattern, str]], folded: str, kind: str) -> tuple[str | None, list[Span]]:
    for pattern, value in patterns:
        match = pattern.search(folded)
        if match:
            return value, [Span(match.start(), match.end(), kind, value)]
    return None, []


def _location(folded: str, offers: list[ChatbotOffer]) -> tuple[str | None, list[Span]]:
    names: dict[str, str] = {}
    for offer in offers:
        if not offer.location:
            continue
        full = " ".join(offer.location.split())
        for candidate in {full, re.split(r"[,(\-/]", full)[0].strip()}:
            if len(candidate) >= 2:
                names.setdefault(" ".join(fold(candidate).split()), candidate)
    for key in sorted(names, key=len, reverse=True):
        match = _boundary_pattern(key).search(folded)
        if match:
            return names[key], [Span(match.start(), match.end(), LOCATION, names[key])]
    return None, []


def _explicit_title(text: str) -> str | None:
    folded = fold(text)
    for regex in (_QUOTED_RE, _AFTER_OFFER_RE):
        match = regex.search(folded)
        if match:
            start, end = match.span(1)
            candidate = text[start:end].strip(" '\"«»“”:")
            if len(candidate) >= 3 and not _NOT_A_TITLE.match(fold(candidate)):
                return " ".join(candidate.split())
    return None


def _mask(folded: str, spans: list[Span]) -> str:
    parts, cursor = [], 0
    for span in spans:
        parts.append(folded[cursor:span.start])
        parts.append(f" {span.kind} ")
        cursor = span.end
    parts.append(folded[cursor:])
    return " ".join("".join(parts).split())


def _unique(values: list[str], rules: SkillRules) -> list[str]:
    seen, result = set(), []
    for value in values:
        key = rules.key(value)
        if key not in seen:
            seen.add(key)
            result.append(value)
    return result


def _keywords(masked: str) -> list[str]:
    result = []
    for token in _TOKEN_RE.findall(masked.replace("'", " ")):
        token = token.strip(".-")
        if len(token) < 2 or token.isdigit() or token in STOPWORDS or token in SEARCH_WORDS:
            continue
        if token not in result:
            result.append(token)
    return result[:5]


def analyze(text: str, offers: list[ChatbotOffer], lexicon: Lexicon | None = None,
            rules: SkillRules | None = None) -> Analysis:
    lexicon = lexicon or default_lexicon()
    rules = rules or default_rules()
    text = " ".join(text.split())
    folded = fold(text)

    titles = _title_spans(folded, offers)
    skills = _skill_spans(text, folded, offers, lexicon, rules)
    experience_years, experience_spans = _experience(folded)
    education_level, education_spans = _first(_EDUCATION_PATTERNS, folded, EDUCATION)
    contract_type, contract_spans = _first(_CONTRACT_PATTERNS, folded, CONTRACT)
    location, location_spans = _location(folded, offers)

    spans = _non_overlapping(titles + skills + experience_spans + education_spans + contract_spans + location_spans)
    title_spans = [s for s in spans if s.kind == TITLE]
    outside_titles = [s for s in skills if all(s.end <= t.start or s.start >= t.end for t in title_spans)]
    masked = _mask(folded, spans)

    mentioned: list[int] = []
    for span in title_spans:
        if int(span.value) not in mentioned:
            mentioned.append(int(span.value))
    # Plusieurs offres portant le même titre cité : toutes sont signalées (l'utilisateur choisira).
    if title_spans:
        cited = {" ".join(fold(o.title).split()) for o in offers if o.id in mentioned}
        mentioned += [o.id for o in offers if o.id not in mentioned and " ".join(fold(o.title).split()) in cited]

    return Analysis(
        text=text,
        folded=folded,
        masked=masked,
        skills=_unique([s.value for s in sorted(outside_titles, key=lambda s: s.start)], rules),
        all_skills=_unique([s.value for s in sorted(skills, key=lambda s: s.start)], rules),
        experience_years=experience_years,
        education_level=education_level,
        contract_type=contract_type,
        location=location,
        mentioned_offer_ids=mentioned,
        explicit_title=None if title_spans else _explicit_title(text),
        keywords=_keywords(masked),
    )
