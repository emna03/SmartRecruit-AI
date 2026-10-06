"""Entraîne, compare et évalue les modèles de risque de turnover, puis sauvegarde le modèle retenu.

Usage (depuis ai-service, environnement virtuel activé) : ``python -m ml.train``
Produit ``ml/artifacts/turnover_model.joblib`` + ``turnover_model.json`` (non versionnés) et le rapport
``ml/reports/turnover_evaluation.json`` (versionné : métriques réellement obtenues). Résultats reproductibles :
graines fixes, mêmes versions de bibliothèques (``requirements.txt``).
"""

import logging
import sys

from app.services.ml.dataset import DatasetError
from app.services.ml.training import ARTIFACTS_DIR, REPORT_PATH, train


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    try:
        report = train()
    except DatasetError as error:
        print(f"Entraînement impossible : {error}", file=sys.stderr)
        return 1
    chosen = report["selection"]["chosenModel"]
    print(f"Modèle retenu : {chosen} — {report['selection']['justification']}")
    for name, metrics in report["test"]["results"].items():
        matrix = metrics["confusionMatrix"]
        print(f"  {name} (test) : accuracy {metrics['accuracy']}, precision {metrics['precision']}, "
              f"recall {metrics['recall']}, F1 {metrics['f1']}, ROC-AUC {metrics['rocAuc']}, "
              f"TN {matrix['trueNegatives']} FP {matrix['falsePositives']} "
              f"FN {matrix['falseNegatives']} TP {matrix['truePositives']}")
    print(f"Modèle : {ARTIFACTS_DIR}\nRapport : {REPORT_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
