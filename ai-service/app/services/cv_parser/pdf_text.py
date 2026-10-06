"""Extraction du texte d'un PDF avec pdfplumber (moteur pdfminer.six), page par page, en mémoire."""

import io
import logging

import pdfplumber

from app.services.cv_parser import errors

PDF_SIGNATURE = b"%PDF-"
SIGNATURE_WINDOW = 1024

# pdfminer journalise des avertissements de structure très verbeux (jamais le contenu) : on ne garde que les erreurs.
logging.getLogger("pdfminer").setLevel(logging.ERROR)


def has_pdf_signature(data: bytes) -> bool:
    return PDF_SIGNATURE in data[:SIGNATURE_WINDOW]


def _is_password_error(exc: BaseException) -> bool:
    seen: set[int] = set()
    stack: list[BaseException] = [exc]
    while stack:
        current = stack.pop()
        if id(current) in seen:
            continue
        seen.add(id(current))
        name = type(current).__name__
        if "Password" in name or "Encryption" in name:
            return True
        stack.extend(a for a in current.args if isinstance(a, BaseException))
        if current.__cause__:
            stack.append(current.__cause__)
        if current.__context__:
            stack.append(current.__context__)
    return False


def extract_pages(data: bytes, max_pages: int) -> list[str]:
    """Texte de chaque page. Lève CvParsingError si le fichier est vide, invalide, chiffré ou trop long."""
    if not data:
        raise errors.empty_file()
    if not has_pdf_signature(data):
        raise errors.invalid_pdf()
    try:
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            if not pdf.pages:
                raise errors.invalid_pdf()
            if len(pdf.pages) > max_pages:
                raise errors.too_many_pages(max_pages)
            return [page.extract_text() or "" for page in pdf.pages]
    except errors.CvParsingError:
        raise
    except Exception as exc:
        if _is_password_error(exc):
            raise errors.encrypted_pdf() from None
        raise errors.invalid_pdf() from None
