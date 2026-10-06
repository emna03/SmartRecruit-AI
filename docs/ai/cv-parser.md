# Module IA — CV Parser + NLP (étape 8)

Analyse automatique d'un CV PDF : le candidat importe son CV depuis Angular, Spring Boot le valide et le transmet
au service FastAPI, qui en extrait les compétences, technologies, formations, expériences et langues.
Le résultat, validé des deux côtés, est enregistré dans MySQL et affiché sur la page `/candidate/cv`.

## Choix de la méthode

**NLP symbolique déterministe** : lexique métier (gazetteer), segmentation par titres de section,
règles et expressions régulières. **Aucun modèle de langage (LLM)** n'est utilisé.

Pourquoi :

- **Testable et reproductible** : même PDF (et même date de référence) ⇒ même JSON. Les tests comparent
  le résultat à une vérité terrain annotée à la main (`tests/fixtures/cv/expected.json`).
- **Explicable** : chaque compétence détectée correspond à une entrée du lexique `app/resources/skills.json`
  (nom, alias, catégorie) ; chaque formation ou expérience vient d'une règle identifiable. On peut montrer
  pendant la démo *pourquoi* le parser a détecté « Spring Boot ».
- **Données personnelles** : le CV n'est envoyé à aucun service externe ; il reste en mémoire le temps
  de l'analyse. Un LLM aurait reçu le CV brut, ce que le cahier des charges demande d'éviter.
- **Pas de faux résultat** : si une information n'est pas écrite dans le CV, elle n'est pas déduite
  (pas de niveau de langue inventé, pas de date devinée) ; le parser renvoie des avertissements à la place.

### Bibliothèques

| Bibliothèque | Version | Rôle |
|---|---|---|
| **pdfplumber** (pdfminer.six, pypdfium2) | 0.11.10 | extraction du texte PDF page par page, en respectant la mise en page ; détection des PDF chiffrés ou endommagés |
| **rapidfuzz** | 3.14.6 | correspondance approchée des titres de section (`fuzz.ratio` ≥ 88) : « Expériences professionelles » (faute) → section Expérience |
| `re`, `unicodedata` (bibliothèque standard) | — | normalisation Unicode, repliement des accents, expressions régulières (dates, diplômes, niveaux de langue, masquage) |
| **Pydantic** 2 | 2.13.5 | schéma de sortie `CvParseResponse` (types, formats, longueurs maximales) |
| reportlab (tests uniquement) | 5.0.1 | génération reproductible des CV PDF fictifs de test |

**spaCy n'est pas utilisé** : sur le poste de développement (Windows 11), Smart App Control bloque le chargement
de la DLL compilée de spaCy (`thinc`/`blis`). Plutôt que de contourner une protection du système, le
`PhraseMatcher` de spaCy (reconnaissance d'entités par dictionnaire) est remplacé par un matcher équivalent
en Python pur (`lexicon.py`), complété par rapidfuzz. Pour ce type d'extraction (vocabulaire technique fermé,
CV courts et structurés), un modèle statistique NER généraliste n'apporterait pas de gain mesurable sans données
d'entraînement annotées, qui n'existent pas dans le projet.

## Pipeline

```
PDF (octets) ─► 1. extraction ─► 2. nettoyage ─► 3. masquage ─► 4. sections ─► 5. extraction ─► 6. JSON validé
               pdfplumber      Unicode, puces    emails, liens,   titres connus   lexique +        Pydantic
                               césures, n° page  téléphones       (+ rapidfuzz)   règles
```

1. **Extraction** (`pdf_text.py`) : signature `%PDF-` vérifiée, ouverture pdfplumber, au plus
   `AI_CV_MAX_PAGES` pages (10 par défaut). PDF vide, non-PDF, endommagé, protégé par mot de passe ou trop long
   ⇒ erreur métier avec un code (`EMPTY_FILE`, `INVALID_PDF`, `ENCRYPTED_PDF`, `TOO_MANY_PAGES`).
2. **Nettoyage** (`text.py`) : normalisation Unicode (NFKC), espaces insécables, puces (`•`, `▪`, glyphes sans
   équivalent Unicode comme `(cid:127)`) ramenées à `- `, mots coupés en fin de ligne recollés, numéros de page
   supprimés. Moins de 30 caractères alphanumériques ⇒ `NO_TEXT` (CV scanné ou image : pas d'OCR).
3. **Masquage des données personnelles** : emails → `[email]`, liens (LinkedIn, GitHub, URL) → `[lien]`,
   numéros de téléphone (8 chiffres et plus, jamais une plage d'années) → `[téléphone]`. Seul ce texte masqué
   est renvoyé à Spring Boot.
4. **Segmentation** (`sections.py`) : une ligne courte sans chiffre égale (après repliement des accents) à un
   titre connu, ou proche à 88 % (rapidfuzz), ouvre une section : profil, expérience, formation, compétences,
   langues, projets, certifications, intérêts, contact. Titres français et anglais (86 variantes).
5. **Extraction** :
   - **Technologies et compétences** (`lexicon.py`) : 155 technologies (12 catégories : Langage, Framework
     back-end, Base de données, Cloud / DevOps…) et 44 compétences (8 catégories : Méthodologie, Architecture et
     conception…), 165 alias. Recherche dans tout le CV ; mots entiers uniquement (« Java » ne correspond pas à
     « JavaScript », « C » pas à « C++ », « SQL » pas à « MySQL ») ; motif le plus long d'abord (« Spring Boot »
     avant « Spring ») ; casse et accents ignorés sauf sigles ambigus (« Go », « IA »). Tri par nombre
     d'occurrences.
   - **Formations** (`education.py`) : section Formation (sinon toutes les lignes contenant un diplôme, avec un
     avertissement). Mot-clé de diplôme ⇒ nouvelle formation et niveau (`DOCTORAT`, `BAC_PLUS_5` pour master /
     ingénieur, `BAC_PLUS_3` pour licence / bachelor, `BAC_PLUS_2`, `BAC`) ; mot-clé d'établissement
     (Université, École, Institut, ISET…) ⇒ établissement ; « en / in / spécialité … » ⇒ domaine ; période ⇒ dates.
   - **Expériences** (`experience.py`) : section Expérience uniquement. Découpage en blocs (lignes datées,
     listes à puces) ; dans l'en-tête, la partie contenant un intitulé de poste connu (Développeur, Stagiaire,
     Engineer, Data Scientist…) est le poste, une ville connue le lieu, le reste l'entreprise ; les puces forment
     la description. Durée calculée, « aujourd'hui / present » ⇒ poste actuel.
   - **Dates** (`dates.py`) : « 03/2023 », « mars 2023 », « Sept. 2021 », « 2019 – 2022 », « depuis 2023 »,
     « 03/2023 – Présent », « Jan 2021 to Present »… en français et en anglais ; périodes futures ou
     incohérentes ignorées.
   - **Langues** (`languages.py`) : 13 langues FR/EN. Dans la section Langues, toute langue est retenue ;
     ailleurs, seulement si un niveau l'accompagne (« Anglais : B2 ») pour ignorer « entreprise française ».
     Niveau par mots-clés (natif, bilingue, courant, professionnel, intermédiaire, débutant), CECRL A1–C2,
     certification (TOEIC 850, IELTS, DELF B2…).
   - **Synthèse** : expérience cumulée en mois (périodes qui se chevauchent comptées une seule fois),
     plus haut niveau d'études, langue du document (fr / en), avertissements.
6. **Validation** : la réponse est un modèle Pydantic `CvParseResponse` (`app/schemas/cv.py`) : dates partielles
   `AAAA` ou `AAAA-MM`, niveaux énumérés, listes et textes bornés. Spring Boot la **revalide** (Bean Validation
   sur `CvParseResult`) avant toute écriture en base : une réponse invalide est rejetée (« réponse rejetée »).

Exemple de réponse (extrait, CV fictif) :

```json
{
  "parserVersion": "1.0.0",
  "documentLanguage": "fr",
  "pageCount": 1,
  "sectionsDetected": ["profil", "experience", "formation", "competences", "langues", "interets"],
  "technologies": [{"name": "Java", "category": "Langage", "occurrences": 3}, {"name": "Spring Boot", "category": "Framework back-end", "occurrences": 3}],
  "skills": [{"name": "API REST", "category": "Développement", "occurrences": 2}],
  "educations": [{"degree": "Diplôme d'ingénieur en Génie Logiciel", "fieldOfStudy": "Génie Logiciel",
                  "institution": "École Supérieure Fictive d'Ingénieurs", "startDate": "2019", "endDate": "2022", "level": "BAC_PLUS_5"}],
  "experiences": [{"jobTitle": "Développeuse Full Stack", "company": "Société Exemple", "location": "Tunis",
                   "startDate": "2023-03", "endDate": null, "current": true, "durationMonths": 44}],
  "languages": [{"name": "Anglais", "level": "COURANT", "cefr": "B2", "certification": "TOEIC 850"}],
  "totalExperienceMonths": 50,
  "highestEducationLevel": "BAC_PLUS_5",
  "warnings": []
}
```

## Parcours complet et stockage

```
Angular /candidate/cv ──multipart──► Spring Boot POST /api/candidate/cv ──application/pdf + clé interne──► FastAPI POST /api/cv/parse
   (contrôle taille + %PDF-)          JWT CANDIDAT, validation, stockage          (pipeline ci-dessus)
                                      ◄────────────── JSON validé (Pydantic) ─────────────────────────────┘
                                      Bean Validation ─► MySQL ─► réponse JSON (sans le texte) ─► affichage
```

Spring Boot (`com.smartrecruit.cv`) :

- seul le **candidat connecté** gère **son** CV (`/api/candidate/**` réservé au rôle `CANDIDAT`, CV retrouvé
  par l'utilisateur du jeton : aucun identifiant de CV dans l'URL) ;
- validation sans faire confiance au navigateur : fichier non vide, ≤ `MAX_CV_SIZE_MB` (5 Mo), **signature
  `%PDF-` lue dans le contenu** ; le type MIME et le nom envoyés sont ignorés : `content_type` vaut toujours
  `application/pdf`, le fichier est stocké sous un nom aléatoire (32 caractères hexadécimaux + `.pdf`) dans `UPLOAD_DIR/cvs`, le nom
  d'origine est seulement affiché après nettoyage (chemins et caractères spéciaux retirés) ;
- enregistrement **sans nouvelle migration** (le schéma V1 suffit) :

| Donnée | Table / colonne |
|---|---|
| fichier, taille, statut `TERMINE` / `ECHEC`, message d'erreur, date d'analyse | `cvs` |
| résultat JSON validé (sans le texte) | `cvs.extracted_data` |
| texte extrait **masqué** (emails, liens, téléphones) | `cvs.extracted_text` |
| technologies et compétences détectées, avec catégorie | `skills` + `cv_skills` |
| formations, expériences, compétences, niveau d'études, années d'expérience — **uniquement sur action du candidat** (« Ajouter au profil ») | `educations`, `experiences`, `candidate_skills`, `candidate_profiles` |

- remplacement : nouveau fichier analysé d'abord ; l'ancien n'est supprimé qu'après l'enregistrement réussi ;
  un PDF refusé par le parser laisse l'ancien CV intact ;
- suppression du CV ou du compte : la ligne et le fichier disque sont supprimés.

### Gestion des erreurs

| Cas | Où | Réponse au candidat |
|---|---|---|
| fichier vide | Angular, Spring Boot | 400 « Le fichier est vide. » |
| pas un PDF (signature absente, quel que soit le nom ou le type MIME) | Angular, Spring Boot | 400 « Le fichier n'est pas un PDF valide… » |
| fichier trop volumineux | Angular, Spring Boot (FastAPI aussi) | 413 « Le fichier dépasse la taille maximale de 5 Mo. » |
| PDF endommagé, protégé par mot de passe, trop de pages | FastAPI → 422 | message du parser, ancien CV conservé |
| PDF vide ou scanné (pas de texte exploitable) | FastAPI → 422 | « Le PDF ne contient pas de texte exploitable… » |
| service IA arrêté, délai `AI_TIMEOUT_SECONDS` dépassé, réponse invalide | Spring Boot | CV enregistré avec le statut `ECHEC` et un message clair ; bouton « Relancer l'analyse » (`POST /api/candidate/cv/analyze`) |

## Endpoints

| Service | Méthode | Chemin | Description |
|---|---|---|---|
| FastAPI (interne, clé `X-Internal-Api-Key`) | POST | `/api/cv/parse` | corps `application/pdf` brut → `CvParseResponse` ; 400 / 413 / 415 / 422 |
| Spring Boot (`CANDIDAT`) | GET | `/api/candidate/cv` | CV et analyse (404 si aucun CV) |
| | POST | `/api/candidate/cv` | multipart, champ `file` : import ou remplacement + analyse |
| | POST | `/api/candidate/cv/analyze` | relance l'analyse du CV enregistré |
| | POST | `/api/candidate/cv/import` | `{skills, educations, experiences}` : ajoute les éléments extraits au profil (doublons ignorés) |
| | DELETE | `/api/candidate/cv` | supprime le CV et son fichier (204) |

## Données personnelles

- Le service IA ne stocke rien ; le CV n'est envoyé à aucun service tiers ni à aucun LLM.
- Journaux : uniquement des compteurs et des codes (« CV analysé : 1 page(s), 2 157 caractères,
  24 technologies… », « CV refusé : NO_TEXT (1 308 octets) ») et l'identifiant utilisateur côté Spring Boot ;
  jamais le contenu, le nom du fichier, un nom, un email ou un téléphone (vérifié par les tests et `e2e-step8.ps1`).
- Le texte conservé en base est masqué ; le navigateur ne reçoit jamais le texte brut.
- Les CV de test sont **entièrement fictifs** (Camille Exemple, Alex Sample, Société Exemple…) et générés par
  `tests/fixtures/cv/generate_fixtures.py`.

## Évaluation

`python scripts/evaluate_cv_parser.py` compare le parser à la vérité terrain annotée à la main
(`tests/fixtures/cv/expected.json`, 3 CV analysables + 5 fichiers à rejeter) :

| Champ | Précision | Rappel | F1 |
|---|---|---|---|
| technologies (42) | 1.00 | 1.00 | 1.00 |
| compétences (18) | 1.00 | 1.00 | 1.00 |
| formations (5) | 1.00 | 1.00 | 1.00 |
| expériences (4) | 1.00 | 1.00 | 1.00 |
| langues (8) | 1.00 | 0.88 | 0.93 |

Langue du document, niveau d'études maximal, durée totale : 100 % ; rejets attendus : 5 / 5.
Écart : « Français » sans niveau, hors section Langues, est volontairement ignoré (règle anti faux positifs).

**Ces scores sont optimistes** : les CV de test ont été conçus en même temps que le parser. Ils valident le
comportement attendu, pas la performance sur des CV réels variés, qui sera inférieure (voir limites).

## Limites connues

- **Pas d'OCR** : un CV scanné (image) est refusé avec un message invitant à exporter un PDF texte.
- **Mises en page complexes** : CV sur deux colonnes, tableaux, zones de texte flottantes : pdfplumber peut
  mélanger l'ordre des lignes, ce qui dégrade surtout les formations et expériences (les compétences restent
  détectées car recherchées dans tout le texte).
- **Lexique fermé** : une technologie absente de `skills.json` n'est pas détectée (l'enrichir suffit, sans
  réentraînement) ; une compétence citée dans un contexte négatif (« pas de Java ») est quand même comptée.
- **Expériences** : extraites seulement si une section Expérience est reconnue ; un intitulé de poste hors de la
  liste connue ou une entreprise sans séparateur clair peut être mal attribué ; la liste des villes est limitée.
- **Langues** : 13 langues (FR / EN) ; le niveau n'est jamais deviné s'il n'est pas écrit.
- **Langues du CV** : français et anglais seulement.
- **Durées** : les postes « en cours » sont calculés à la date du jour ; mois absent ⇒ juin (milieu d'année)
  pour le calcul de durée, estimation à un an près ; en base, une date sans mois est enregistrée au 1er janvier
  (`educations` / `experiences` exigent une date complète).
- **Évaluation** limitée à 3 CV fictifs (voir ci-dessus).

## Tests

- FastAPI (`ai-service/tests/test_cv_*.py`, 85 tests au total) : nettoyage, dates, sections, lexique, formations,
  expériences, langues, pipeline complet sur la vérité terrain, déterminisme, masquage, tous les rejets,
  route HTTP (clé, 413 y compris en flux, 415, 422, aucun contenu de CV dans les journaux).
- Spring Boot : `CandidateCvApiTest` (12 scénarios avec un faux service IA : MIME et nom ignorés, sécurité,
  rejet, panne, délai dépassé, réponse invalide, remplacement, suppression, import), `CvFileValidatorTest` (13),
  `AiServiceClientTest`.
- Angular : `cv.service.spec.ts`, `candidate-cv-page.spec.ts` (Vitest).
- Bout en bout réels : `scripts/e2e-step8.ps1` (47 vérifications API + MySQL + disque + journaux) et
  `frontend/e2e/cv-flow.e2e.ts` (Playwright : connexion, upload, affichage, MySQL, erreurs, remplacement,
  import dans le profil, suppression).
