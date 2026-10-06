"""Génère les CV PDF de test (données 100 % fictives : noms, entreprises, écoles, emails et numéros inventés).

    .\\.venv\\Scripts\\python.exe tests\\fixtures\\cv\\generate_fixtures.py

Sortie reproductible (``invariant=1``) : relancer le script ne modifie pas les fichiers versionnés.
Les valeurs attendues de chaque CV sont dans ``expected.json`` (vérité terrain utilisée par les tests
et par ``scripts/evaluate_cv_parser.py``).
"""

from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

HERE = Path(__file__).resolve().parent
WIDTH, HEIGHT = A4
MARGIN = 50

H = "heading"
T = "title"
B = "bullet"

CV_DEV_FR = [
    (T, "Camille Exemple"),
    "Développeuse Full Stack Java / Angular",
    "camille.exemple@example.com | +216 20 000 000 | Tunis | linkedin.com/in/camille-exemple",
    (H, "PROFIL"),
    "Développeuse full stack avec 3 ans d'expérience dans la conception d'applications web et d'API REST.",
    (H, "EXPÉRIENCE PROFESSIONNELLE"),
    "Développeuse Full Stack — Société Exemple, Tunis | 03/2023 – Présent",
    (B, "Développement d'API REST avec Java 17 et Spring Boot, sécurisées par JWT."),
    (B, "Création d'interfaces Angular et TypeScript, tests unitaires avec JUnit et Mockito."),
    (B, "Déploiement avec Docker et Jenkins (intégration continue)."),
    "Stagiaire développeuse — Entreprise Démo, Ariana | 02/2022 – 07/2022",
    (B, "Application de gestion des stocks : Spring Boot, MySQL, Hibernate."),
    (B, "Méthode agile Scrum, suivi des tâches dans Jira."),
    (H, "FORMATION"),
    "Diplôme d'ingénieur en Génie Logiciel — École Supérieure Fictive d'Ingénieurs | 2019 – 2022",
    "Licence en Informatique — Université Exemple de Tunis | 2016 – 2019",
    (H, "COMPÉTENCES TECHNIQUES"),
    "Langages : Java, TypeScript, JavaScript, SQL, HTML, CSS",
    "Frameworks : Spring Boot, Spring Security, Angular, Hibernate, Bootstrap",
    "Bases de données : MySQL, PostgreSQL, MongoDB",
    "Outils : Git, GitHub, Docker, Jenkins, Maven, Postman",
    "Méthodes : Agile, Scrum, UML, Programmation orientée objet",
    (H, "LANGUES"),
    "Français : langue maternelle",
    "Anglais : courant (B2) – TOEIC 850",
    "Allemand : notions",
    (H, "CENTRES D'INTÉRÊT"),
    "Lecture, randonnée, échecs",
]

CV_DATA_EN = [
    (T, "Alex Sample"),
    "Data Scientist",
    "alex.sample@example.org · +33 6 00 00 00 00 · github.com/alex-sample",
    (H, "SUMMARY"),
    "Data scientist with 4 years of experience in machine learning and natural language processing.",
    (H, "WORK EXPERIENCE"),
    "Data Scientist",
    "Sample Analytics Ltd, London",
    "Jan 2021 - Present",
    (B, "Built NLP pipelines with Python, spaCy and scikit-learn to classify customer feedback."),
    (B, "Deployed models as REST APIs with FastAPI and Docker on AWS."),
    (B, "Data visualization dashboards with Power BI."),
    "Data Analyst Intern",
    "Demo Corp, Paris",
    "Jun 2020 - Dec 2020",
    (B, "Data analysis with pandas, NumPy and SQL on PostgreSQL."),
    (H, "EDUCATION"),
    "Master of Science in Data Science",
    "Example University",
    "2018 - 2020",
    "Bachelor of Science in Computer Science",
    "Sample Institute of Technology",
    "2015 - 2018",
    (H, "SKILLS"),
    "Python, SQL, pandas, NumPy, scikit-learn, TensorFlow, PyTorch, Machine Learning, Deep Learning, Statistics, Git",
    (H, "LANGUAGES"),
    "English (native), French (C1), Spanish (intermediate)",
]

CV_MINIMAL_FR = [
    "Profil junior",
    "Je recherche un premier emploi de développeur web.",
    "Je maîtrise PHP, Laravel, MySQL et JavaScript.",
    "J'ai obtenu une Licence professionnelle en développement web en 2024 à l'ISET Exemple.",
    "Je parle français et anglais (niveau intermédiaire).",
]


def write_cv(path: Path, lines: list, **canvas_options) -> None:
    pdf = canvas.Canvas(str(path), pagesize=A4, invariant=1, **canvas_options)
    pdf.setTitle("CV fictif de test")
    pdf.setAuthor("SmartRecruit AI - jeu de test")
    y = HEIGHT - MARGIN
    for item in lines:
        kind, text = item if isinstance(item, tuple) else ("text", item)
        if kind == H:
            y -= 10
            pdf.setFont("Helvetica-Bold", 12)
        elif kind == T:
            pdf.setFont("Helvetica-Bold", 16)
        else:
            pdf.setFont("Helvetica", 10)
        if kind == B:
            text = "•  " + text
        pdf.drawString(MARGIN + (12 if kind == B else 0), y, text)
        y -= 20 if kind in (H, T) else 15
        if y < MARGIN:
            pdf.showPage()
            y = HEIGHT - MARGIN
    pdf.save()


def write_image_only(path: Path) -> None:
    """CV « scanné » : uniquement des formes graphiques, aucun texte."""
    pdf = canvas.Canvas(str(path), pagesize=A4, invariant=1)
    pdf.setFillGray(0.85)
    for i in range(12):
        pdf.rect(MARGIN, HEIGHT - MARGIN - 40 - i * 55, WIDTH - 2 * MARGIN - (i % 3) * 60, 30, fill=1, stroke=0)
    pdf.circle(WIDTH - 90, HEIGHT - 90, 35, fill=1)
    pdf.save()


def write_blank(path: Path) -> None:
    pdf = canvas.Canvas(str(path), pagesize=A4, invariant=1)
    pdf.showPage()
    pdf.save()


def write_pages(path: Path, count: int) -> None:
    lines = []
    for page in range(1, count + 1):
        lines += [(H, f"PAGE {page}")] + ["Texte de remplissage pour un document volumineux."] * 45
    write_cv(path, lines)


def main() -> None:
    write_cv(HERE / "cv_dev_fullstack_fr.pdf", CV_DEV_FR)
    write_cv(HERE / "cv_data_scientist_en.pdf", CV_DATA_EN)
    write_cv(HERE / "cv_minimal_fr.pdf", CV_MINIMAL_FR)
    write_image_only(HERE / "cv_image_only.pdf")
    write_blank(HERE / "cv_blank.pdf")
    write_cv(HERE / "cv_encrypted.pdf", CV_DEV_FR, encrypt="mot-de-passe-de-test")
    (HERE / "not_a_pdf.pdf").write_bytes("Ceci n'est pas un PDF : simple fichier texte renommé.\n".encode())
    (HERE / "cv_corrupted.pdf").write_bytes(b"%PDF-1.4\n" + bytes(range(256)) * 8 + b"\n%%EOF\n")
    for path in sorted(HERE.glob("*.pdf")):
        print(f"{path.name}: {path.stat().st_size} octets")


if __name__ == "__main__":
    main()
