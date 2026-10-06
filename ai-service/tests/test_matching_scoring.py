"""Sous-scores et pondérations du matching."""

import pytest

from app.services.matching.scoring import (EDUCATION, EXPERIENCE, SKILLS, WEIGHTS, education_score,
                                           effective_weights, experience_score, global_score)


def test_weights_sum_to_one():
    assert sum(WEIGHTS.values()) == pytest.approx(1.0)
    assert WEIGHTS == {SKILLS: 0.60, EXPERIENCE: 0.25, EDUCATION: 0.15}


@pytest.mark.parametrize("months, years, expected", [
    (36, 3, 100.0),
    (60, 3, 100.0),  # surqualification : plafonné, sans bonus
    (18, 3, 50.0),
    (27, 3, 75.0),
    (0, 2, 0.0),
    (None, 2, 0.0),  # aucune expérience datée dans le CV
])
def test_experience_score_is_proportional_and_capped(months, years, expected):
    assert experience_score(months, years) == pytest.approx(expected)


def test_experience_not_applicable_when_offer_requires_none():
    assert experience_score(24, 0) is None
    assert experience_score(None, 0) is None


@pytest.mark.parametrize("candidate, required, expected", [
    ("BAC_PLUS_5", "BAC_PLUS_5", 100.0),
    ("DOCTORAT", "BAC_PLUS_3", 100.0),
    ("BAC_PLUS_3", "BAC_PLUS_5", 60.0),
    ("BAC_PLUS_2", "BAC_PLUS_5", 20.0),
    ("BAC", "BAC_PLUS_5", 0.0),
    (None, "BAC_PLUS_3", 0.0),
])
def test_education_score_loses_40_points_per_missing_level(candidate, required, expected):
    assert education_score(candidate, required) == expected


def test_education_not_applicable_when_offer_does_not_specify_a_level():
    assert education_score("BAC_PLUS_5", None) is None
    assert education_score(None, None) is None


def test_global_score_is_the_weighted_average_of_applicable_criteria():
    scores = {SKILLS: 80.0, EXPERIENCE: 60.0, EDUCATION: 100.0}

    assert global_score(scores) == pytest.approx(0.6 * 80 + 0.25 * 60 + 0.15 * 100)


def test_weights_are_renormalized_when_a_criterion_is_not_applicable():
    scores = {SKILLS: 80.0, EXPERIENCE: 40.0, EDUCATION: None}

    weights = effective_weights(scores)
    assert weights[EDUCATION] is None
    assert weights[SKILLS] == pytest.approx(0.60 / 0.85)
    assert weights[EXPERIENCE] == pytest.approx(0.25 / 0.85)
    assert global_score(scores) == pytest.approx((0.6 * 80 + 0.25 * 40) / 0.85)


def test_only_skills_count_when_the_offer_has_no_other_requirement():
    assert global_score({SKILLS: 42.0, EXPERIENCE: None, EDUCATION: None}) == pytest.approx(42.0)


@pytest.mark.parametrize("scores", [
    {SKILLS: 0.0, EXPERIENCE: 0.0, EDUCATION: 0.0},
    {SKILLS: 100.0, EXPERIENCE: 100.0, EDUCATION: 100.0},
    {SKILLS: 100.0, EXPERIENCE: None, EDUCATION: None},
])
def test_global_score_stays_between_0_and_100(scores):
    assert 0.0 <= global_score(scores) <= 100.0
