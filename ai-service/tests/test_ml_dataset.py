import shutil

import pytest

from app.schemas.ml import TurnoverPredictionRequest
from app.services.ml.dataset import DATASET_PATH, DatasetError, load_dataset
from app.services.ml.features import (
    CATEGORICAL, DROPPED_CONSTANT, DROPPED_IDENTIFIER, DROPPED_SENSITIVE, DROPPED_UNDOCUMENTED, FEATURES, NUMERIC,
    row_to_api,
)

pytestmark = pytest.mark.skipif(not DATASET_PATH.is_file(), reason="Dataset IBM absent : python -m ml.download_dataset")


def test_dataset_is_loaded_with_the_documented_size_and_target():
    features, target = load_dataset()

    assert features.shape == (1470, 24)
    assert list(features.columns) == FEATURES
    assert int(target.sum()) == 237
    assert set(target.unique()) == {0, 1}


def test_sensitive_identifier_constant_and_undocumented_columns_are_never_used():
    features, _ = load_dataset()

    excluded = DROPPED_CONSTANT + DROPPED_IDENTIFIER + DROPPED_SENSITIVE + DROPPED_UNDOCUMENTED
    assert not set(excluded) & set(features.columns)
    assert {"Age", "Gender", "MaritalStatus"} <= set(DROPPED_SENSITIVE)
    assert "Attrition" not in features.columns


def test_a_modified_dataset_is_refused(tmp_path):
    copy = tmp_path / "emp_attrition.csv"
    shutil.copyfile(DATASET_PATH, copy)
    copy.write_bytes(copy.read_bytes().replace(b"Travel_Rarely", b"Travel_Raraly", 1))

    with pytest.raises(DatasetError, match="SHA-256"):
        load_dataset(copy)


def test_a_missing_dataset_is_refused(tmp_path):
    with pytest.raises(DatasetError, match="introuvable"):
        load_dataset(tmp_path / "absent.csv")


def test_every_dataset_row_is_accepted_by_the_api_and_converted_back_identically():
    """Le schéma de l'API et la conversion vers les colonnes du modèle couvrent exactement le dataset."""
    features, _ = load_dataset()

    for row in features.to_dict("records"):
        request = TurnoverPredictionRequest.model_validate(row_to_api(row))
        converted = request.to_row()
        assert list(converted) == FEATURES
        assert all(converted[c] == int(row[c]) for c in NUMERIC)
        assert all(converted[c] == row[c] for c in CATEGORICAL)
