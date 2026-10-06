import pytest
from pydantic import ValidationError

from app.core.config import Settings
from tests.conftest import TEST_KEY, make_settings


def test_api_key_is_mandatory():
    with pytest.raises(ValidationError) as error:
        Settings(_env_file=None)

    assert "ai_internal_api_key" in str(error.value)


def test_short_api_key_is_rejected():
    with pytest.raises(ValidationError, match="au moins 16"):
        make_settings(ai_internal_api_key="short-key")


def test_example_api_key_is_rejected():
    with pytest.raises(ValidationError, match="valeur d'exemple"):
        make_settings(ai_internal_api_key="change_me_internal_key")


def test_api_key_is_trimmed_and_never_displayed():
    settings = make_settings(ai_internal_api_key=f"  {TEST_KEY}  ")

    assert settings.ai_internal_api_key.get_secret_value() == TEST_KEY
    assert TEST_KEY not in repr(settings)
    assert TEST_KEY not in str(settings.model_dump())


def test_secure_defaults():
    settings = make_settings()

    assert settings.ai_service_host == "127.0.0.1"
    assert settings.ai_docs_enabled is False
    assert settings.port == 8000


def test_port_follows_ai_service_url():
    assert make_settings(ai_service_url="http://localhost:8099").port == 8099
    assert make_settings(ai_service_url="http://ai-service").port == 8000


def test_explicit_port_wins_over_url():
    assert make_settings(ai_service_url="http://localhost:8099", ai_service_port=9000).port == 9000


def test_invalid_log_level_is_rejected():
    with pytest.raises(ValidationError, match="AI_LOG_LEVEL"):
        make_settings(ai_log_level="verbose")


def test_environment_variables_are_read(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_API_KEY", TEST_KEY)
    monkeypatch.setenv("AI_SERVICE_PORT", "8123")

    settings = Settings(_env_file=None)

    assert settings.ai_internal_api_key.get_secret_value() == TEST_KEY
    assert settings.port == 8123


def test_shared_env_file_is_read_and_unrelated_keys_ignored(tmp_path):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "# Fichier partagé avec Spring Boot\n"
        "DB_PASSWORD=mysql-secret\n"
        "JWT_SECRET=jwt-secret-value-0123456789abcdef0123\n"
        f"AI_INTERNAL_API_KEY={TEST_KEY}\n"
        "AI_SERVICE_URL=http://localhost:8001\n",
        encoding="utf-8",
    )

    settings = Settings(_env_file=env_file)

    assert settings.ai_internal_api_key.get_secret_value() == TEST_KEY
    assert settings.port == 8001
    assert not hasattr(settings, "db_password")


def test_environment_variable_overrides_env_file(tmp_path, monkeypatch):
    env_file = tmp_path / ".env"
    env_file.write_text(f"AI_INTERNAL_API_KEY={TEST_KEY}\nAI_LOG_LEVEL=debug\n", encoding="utf-8")
    monkeypatch.setenv("AI_LOG_LEVEL", "warning")

    assert Settings(_env_file=env_file).ai_log_level == "warning"
