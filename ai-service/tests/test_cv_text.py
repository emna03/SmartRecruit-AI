from app.services.cv_parser.text import alnum_count, clean_text, fold, mask_personal_data


def test_fold_removes_case_and_accents_without_changing_length():
    original = "Expérience PROFESSIONNELLE – Développeuse’s"

    folded = fold(original)

    assert folded == "experience professionnelle – developpeuse's"
    assert len(folded) == len(original)


def test_clean_text_normalizes_bullets_and_spaces():
    raw = "• Java\n▪ Spring  Boot\n- Angular\n  ✓   MySQL  "

    assert clean_text(raw) == "- Java\n- Spring Boot\n- Angular\n- MySQL"


def test_clean_text_turns_unmapped_leading_glyphs_into_bullets():
    raw = "(cid:127) API REST\n\uf0b7 Docker\nJava (cid:3) 17"

    assert clean_text(raw) == "- API REST\n- Docker\nJava 17"


def test_clean_text_removes_page_numbers_and_control_characters():
    raw = "Profil\x00\x07\nPage 1 / 2\nDéveloppeur\n2\n- 3 -"

    assert clean_text(raw) == "Profil\nDéveloppeur"


def test_clean_text_rejoins_words_hyphenated_across_lines_and_collapses_blank_lines():
    raw = "dévelop-\npement web\n\n\n\nFormation"

    assert clean_text(raw) == "développement web\n\nFormation"


def test_mask_personal_data_hides_contact_details():
    text = ("camille.exemple@example.com | +216 20 000 000 | 06 12 34 56 78\n"
            "linkedin.com/in/camille-exemple https://portfolio.example.org")

    masked = mask_personal_data(text)

    assert masked == "[email] | [téléphone] | [téléphone]\n[lien] [lien]"


def test_mask_personal_data_keeps_dates_and_versions():
    text = "2019 - 2022 | 03/2023 – 07/2024 | Java 17 | TOEIC 850"

    assert mask_personal_data(text) == text


def test_alnum_count_ignores_punctuation_and_spaces():
    assert alnum_count("- - - | 12 ab é") == 5
