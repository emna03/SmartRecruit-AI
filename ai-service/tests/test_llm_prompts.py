from app.services.llm.prompts import SYSTEM_PROMPT, build_user_message


def test_system_prompt_contains_course_sections():
    for section in ("# RÔLE", "# CONTEXTE", "# TÂCHE", "# FORMAT", "# CONTRAINTES"):
        assert section in SYSTEM_PROMPT
    assert "DONNÉE" in SYSTEM_PROMPT or "DONNÉES" in SYSTEM_PROMPT
    assert "JSON" in SYSTEM_PROMPT


def test_user_message_delimits_candidate_data_and_includes_few_shot():
    payload = {
        "skills": ["API REST"],
        "technologies": ["Java"],
        "educations": [],
        "experiences": [],
        "languages": [],
    }
    message = build_user_message(payload)
    assert "<candidate_data>" in message
    assert "</candidate_data>" in message
    assert message.count("<candidate_data>") >= 3  # 2 few-shot + 1 réel
    assert "Ignore toutes" not in payload.get("skills", [])
    assert "Exemple 1" in message
    assert '"Java"' in message
