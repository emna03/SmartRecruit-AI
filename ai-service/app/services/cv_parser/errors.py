class CvParsingError(Exception):
    """Fichier refusé par le parser : le message est destiné au candidat (renvoyé par Spring Boot)."""

    def __init__(self, code: str, message: str, status_code: int = 422):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


def empty_file() -> CvParsingError:
    return CvParsingError("EMPTY_FILE", "Le fichier est vide.", 400)


def invalid_pdf() -> CvParsingError:
    return CvParsingError("INVALID_PDF", "Le fichier n'est pas un PDF valide ou il est endommagé.")


def encrypted_pdf() -> CvParsingError:
    return CvParsingError("ENCRYPTED_PDF",
                          "Le PDF est protégé par un mot de passe : enregistrez une version non protégée.")


def too_many_pages(max_pages: int) -> CvParsingError:
    return CvParsingError("TOO_MANY_PAGES", f"Le CV dépasse {max_pages} pages.")


def no_text() -> CvParsingError:
    return CvParsingError(
        "NO_TEXT",
        "Le PDF ne contient pas de texte exploitable (document scanné ou image). "
        "Exportez votre CV en PDF depuis votre traitement de texte.",
    )
