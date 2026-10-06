"""Évalue le CV Parser sur les CV fictifs annotés (tests/fixtures/cv/expected.json).

    .\\.venv\\Scripts\\python.exe scripts\\evaluate_cv_parser.py

Pour chaque champ : précision = éléments corrects / éléments extraits, rappel = éléments corrects / éléments
attendus, F1 = moyenne harmonique (micro-moyenne sur tous les CV). Les rejets attendus (PDF vide, scanné,
chiffré, invalide) sont vérifiés à part.
"""

import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services.cv_parser.errors import CvParsingError  # noqa: E402
from app.services.cv_parser.pipeline import parse_cv  # noqa: E402

FIXTURES = ROOT / "tests" / "fixtures" / "cv"
MAX_PAGES = 10

KEYS = {
    "technologies": lambda item: item["name"].lower(),
    "skills": lambda item: item["name"].lower(),
    "educations": lambda item: (item["level"], item["institution"], item["startDate"], item["endDate"]),
    "experiences": lambda item: (item["jobTitle"], item["company"], item["startDate"], item["endDate"],
                                 item["current"]),
    "languages": lambda item: (item["name"], item["level"]),
}


def _expected_key(field: str, item):
    return item.lower() if isinstance(item, str) else KEYS[field](item)


def evaluate() -> dict:
    truth = json.loads((FIXTURES / "expected.json").read_text(encoding="utf-8"))
    today = date.fromisoformat(truth["referenceDate"])
    totals = {field: {"tp": 0, "extracted": 0, "expected": 0} for field in KEYS}
    scalars = {"correct": 0, "total": 0}
    errors: list[str] = []

    for name, expected in truth["parsed"].items():
        result = parse_cv((FIXTURES / name).read_bytes(), MAX_PAGES, today).model_dump(by_alias=True)
        for field in KEYS:
            got = {KEYS[field](item) for item in result[field]}
            want = {_expected_key(field, item) for item in expected[field]}
            totals[field]["tp"] += len(got & want)
            totals[field]["extracted"] += len(got)
            totals[field]["expected"] += len(want)
            for missing in sorted(map(str, want - got)):
                errors.append(f"{name} / {field} : manquant {missing}")
            for extra in sorted(map(str, got - want)):
                errors.append(f"{name} / {field} : en trop {extra}")
        for field in ("documentLanguage", "highestEducationLevel", "totalExperienceMonths"):
            scalars["total"] += 1
            if result[field] == expected[field]:
                scalars["correct"] += 1
            else:
                errors.append(f"{name} / {field} : {result[field]!r} au lieu de {expected[field]!r}")

    rejected_ok = 0
    for name, code in truth["rejected"].items():
        try:
            parse_cv((FIXTURES / name).read_bytes(), MAX_PAGES, today)
            errors.append(f"{name} : accepté au lieu d'être rejeté ({code})")
        except CvParsingError as exc:
            if exc.code == code:
                rejected_ok += 1
            else:
                errors.append(f"{name} : rejeté avec {exc.code} au lieu de {code}")

    metrics = {}
    for field, t in totals.items():
        precision = t["tp"] / t["extracted"] if t["extracted"] else 1.0
        recall = t["tp"] / t["expected"] if t["expected"] else 1.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        metrics[field] = {"precision": precision, "recall": recall, "f1": f1, **t}
    return {
        "fields": metrics,
        "scalarAccuracy": scalars["correct"] / scalars["total"],
        "rejected": {"correct": rejected_ok, "total": len(truth["rejected"])},
        "errors": errors,
    }


def main() -> None:
    report = evaluate()
    print(f"{'Champ':<14}{'Précision':>10}{'Rappel':>9}{'F1':>7}   (corrects / extraits / attendus)")
    for field, m in report["fields"].items():
        print(f"{field:<14}{m['precision']:>10.2f}{m['recall']:>9.2f}{m['f1']:>7.2f}"
              f"   ({m['tp']} / {m['extracted']} / {m['expected']})")
    print(f"Langue du document, niveau max, durée totale : {report['scalarAccuracy']:.0%} corrects")
    print(f"Rejets attendus (vide, scanné, chiffré, invalide) : "
          f"{report['rejected']['correct']} / {report['rejected']['total']}")
    if report["errors"]:
        print("\nÉcarts :")
        for line in report["errors"]:
            print(f"  - {line}")


if __name__ == "__main__":
    main()
