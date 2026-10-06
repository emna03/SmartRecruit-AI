from datetime import date

import pytest

from app.services.cv_parser.dates import YearMonth, find_range, find_single_date, months_between, remove_span

TODAY = date(2026, 10, 6)


@pytest.mark.parametrize(("line", "start", "end", "current"), [
    ("Développeur | 03/2023 – 07/2024", YearMonth(2023, 3), YearMonth(2024, 7), False),
    ("2019 - 2022", YearMonth(2019), YearMonth(2022), False),
    ("Mars 2021 à Juin 2022", YearMonth(2021, 3), YearMonth(2022, 6), False),
    ("Jan 2021 - Present", YearMonth(2021, 1), None, True),
    ("Sept. 2020 – aujourd’hui", YearMonth(2020, 9), None, True),
    ("Depuis février 2024", YearMonth(2024, 2), None, True),
    ("From 2018 to 2020", YearMonth(2018), YearMonth(2020), False),
    ("de 01.2022 au 12.2022", YearMonth(2022, 1), YearMonth(2022, 12), False),
])
def test_find_range_formats(line, start, end, current):
    found = find_range(line, TODAY)

    assert found is not None
    assert (found.start, found.end, found.current) == (start, end, current)


def test_find_range_rejects_reversed_or_implausible_periods():
    assert find_range("2022 - 2019", TODAY) is None
    assert find_range("2031 - 2034", TODAY) is None
    assert find_range("1850 - 1860", TODAY) is None


def test_find_range_ignores_numbers_that_are_not_periods():
    assert find_range("Java 17, TOEIC 850, 2 ans", TODAY) is None
    assert find_range("Référence 12/20234", TODAY) is None


def test_find_single_date_allows_future_graduation_years():
    found = find_single_date("Master en Data Science (prévu 2028)", TODAY)

    assert found is not None and found.end == YearMonth(2028) and found.start is None
    assert find_single_date("Diplôme 2040", TODAY) is None


def test_remove_span_keeps_the_rest_of_the_line():
    line = "Stagiaire — Entreprise Démo | 02/2022 – 07/2022"
    found = find_range(line, TODAY)

    assert remove_span(line, found.span) == "Stagiaire — Entreprise Démo |"


def test_months_between_is_inclusive_and_uses_mid_year_for_unknown_months():
    assert months_between(YearMonth(2022, 2), YearMonth(2022, 7)) == 6
    assert months_between(YearMonth(2023, 3), YearMonth(2026, 10)) == 44
    assert months_between(YearMonth(2019), YearMonth(2022)) == 37
    assert months_between(YearMonth(2024, 5), YearMonth(2024, 1)) == 0
