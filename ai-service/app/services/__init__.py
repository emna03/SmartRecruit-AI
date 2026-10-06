"""Modules métier du service IA.

Chaque sous-package est implémenté dans une étape dédiée et publie ses routes sous ``/api``
(donc protégées par la clé interne). ``MODULES`` indique ce qui est réellement disponible.
"""

MODULES: dict[str, bool] = {
    "cv_parser": True,  # étape 8
    "matching": True,  # étape 9
    "chatbot": False,  # étape 10
    "ml": False,  # étape 11
}
