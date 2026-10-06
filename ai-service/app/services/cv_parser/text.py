"""Nettoyage du texte extrait et normalisation pour la comparaison.

``fold`` produit une version minuscule et sans accents de même longueur que l'original :
les positions trouvées dans le texte normalisé s'appliquent telles quelles au texte d'origine.
"""

import re
import unicodedata

_BULLET_CHARS = "•●▪◦‣∙·■□◆◇►▶➢➤✓✔○*"
_BULLET_RE = re.compile(rf"^(?:[{re.escape(_BULLET_CHARS)}]|[-–—](?=\s))\s*")
_PAGE_NUMBER_RE = re.compile(r"^(?:page\s*)?\d{1,2}\s*(?:/|sur|of)\s*\d{1,2}$|^-?\s*\d{1,2}\s*-?$", re.IGNORECASE)
_HYPHENATED_BREAK_RE = re.compile(r"(\w)-\n([a-zà-ÿ])")
# Glyphes sans correspondance Unicode (polices symboles) : « (cid:127) » de pdfminer ou zone à usage privé.
# En début de ligne, ce sont presque toujours des puces.
_LEADING_GLYPH_RE = re.compile(r"(?m)^[ \t]*(?:\(cid:\d+\)|[\ue000-\uf8ff])")
_CID_RE = re.compile(r"\(cid:\d+\)")

_EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
_URL_RE = re.compile(r"(?:https?://|www\.)\S+|\b(?:linkedin\.com|github\.com|gitlab\.com)/\S*", re.IGNORECASE)
# Numéros internationaux / nationaux (+216 20 123 456, 06 12 34 56 78, (+33) 6 12...) ; jamais une plage d'années.
_PHONE_RE = re.compile(r"(?<![\w/])(?:\(?(?:\+|00)\d{1,3}\)?[ .-]?)?(?:\d[ .-]?){7,11}\d(?![\w/])")
_YEAR_RANGE_RE = re.compile(r"^\s*(?:19|20)\d{2}\s*[-–—]?\s*(?:(?:19|20)\d{2})?\s*$")

BULLET = "- "


def _fold_char(char: str) -> str:
    if char in "’‘`´":
        return "'"
    decomposed = "".join(c for c in unicodedata.normalize("NFKD", char) if not unicodedata.combining(c))
    folded = (decomposed or char).lower()
    return folded if len(folded) == 1 else char


def fold(text: str) -> str:
    """Minuscules sans accents, longueur identique à ``text``."""
    return "".join(_fold_char(c) for c in text)


def clean_text(raw: str) -> str:
    """Texte brut du PDF -> lignes propres (puces normalisées en "- ", numéros de page retirés)."""
    text = unicodedata.normalize("NFKC", raw)
    text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\u00a0", " ").replace("\t", " ")
    text = _CID_RE.sub(" ", _LEADING_GLYPH_RE.sub("•", text))
    text = "".join(c for c in text if c == "\n" or unicodedata.category(c)[0] != "C")
    text = _HYPHENATED_BREAK_RE.sub(r"\1\2", text)

    lines: list[str] = []
    for line in text.split("\n"):
        line = re.sub(r" {2,}", " ", line).strip()
        if _PAGE_NUMBER_RE.match(line):
            continue
        if _BULLET_RE.match(line):
            line = BULLET + _BULLET_RE.sub("", line)
        if line == BULLET.strip():
            continue
        if not line and (not lines or not lines[-1]):
            continue
        lines.append(line)
    return "\n".join(lines).strip()


def _mask_phone(match: re.Match) -> str:
    value = match.group(0)
    digits = sum(c.isdigit() for c in value)
    if digits < 8 or _YEAR_RANGE_RE.match(value):
        return value
    return "[téléphone]"


def mask_personal_data(text: str) -> str:
    """Masque emails, liens et numéros de téléphone (inutiles à l'analyse, données personnelles)."""
    text = _EMAIL_RE.sub("[email]", text)
    text = _URL_RE.sub("[lien]", text)
    return _PHONE_RE.sub(_mask_phone, text)


def alnum_count(text: str) -> int:
    return sum(c.isalnum() for c in text)
