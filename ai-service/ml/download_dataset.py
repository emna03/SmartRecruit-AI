"""Télécharge le dataset IBM HR Analytics Employee Attrition & Performance et vérifie son empreinte SHA-256.

Usage (depuis ai-service, environnement virtuel activé) : ``python -m ml.download_dataset``
Source : dépôt GitHub d'IBM ``IBM/employee-attrition-aif360`` (même fichier que le dataset Kaggle
« IBM HR Analytics Employee Attrition & Performance »). Voir ``ml/data/README.md``.
"""

import sys
import urllib.request

from app.services.ml.dataset import DATASET_PATH, DATASET_SHA256, DATASET_URL, sha256_of


def main() -> int:
    if DATASET_PATH.is_file() and sha256_of(DATASET_PATH) == DATASET_SHA256:
        print(f"Dataset déjà présent et vérifié : {DATASET_PATH}")
        return 0
    DATASET_PATH.parent.mkdir(parents=True, exist_ok=True)
    partial = DATASET_PATH.with_suffix(".part")
    print(f"Téléchargement de {DATASET_URL}")
    with urllib.request.urlopen(DATASET_URL, timeout=60) as response:
        partial.write_bytes(response.read())
    if (actual := sha256_of(partial)) != DATASET_SHA256:
        partial.unlink()
        print(f"Empreinte SHA-256 inattendue ({actual}) : fichier supprimé.", file=sys.stderr)
        return 1
    partial.replace(DATASET_PATH)
    print(f"Dataset téléchargé et vérifié : {DATASET_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
