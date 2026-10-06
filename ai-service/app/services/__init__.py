"""Modules métier du service IA.

Chaque sous-package est implémenté dans une étape dédiée et publie ses routes sous ``/api``
(donc protégées par la clé interne). ``MODULES`` indique ce qui est réellement disponible.
"""

MODULES: dict[str, bool] = {
    "cv_parser": True,  # étape 8
    "matching": True,  # étape 9
    "chatbot": True,  # étape 10
    "ml": True,  # étape 11 : annoncé disponible seulement si le modèle entraîné est chargé (voir /api/status)
}
