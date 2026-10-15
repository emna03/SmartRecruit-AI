# Selenium IDE — Membre 1 (modèle IA : Logistic Regression)

**Statut :** PASS / VALIDÉ (exécution réelle Selenium IDE standalone 4.0.1-beta.14)

## Compatibilité

| Élément | Réalité |
|---------|---------|
| Extension Chrome officielle | Retirée du Chrome Web Store |
| **Selenium IDE officiel** | Application standalone Electron (SeleniumHQ) |
| Version | `4.0.1-beta.14` |
| Installateur Windows | [Selenium-IDE-Setup-4.0.1-beta.14.exe](https://github.com/SeleniumHQ/selenium-ide/releases/download/v4.0.1-beta.14/Selenium-IDE-Setup-4.0.1-beta.14.exe) |

## Scénario validé

Login candidat → Mon CV → Offres → recherche « Java » → ouverture d’une offre.

Compte de test : voir `compte-test.txt` (fictif, local uniquement).

## Fichiers

| Fichier | Rôle |
|---------|------|
| `smartrecruit-candidat.side` | Projet SIDE exécuté dans Selenium IDE |
| `compte-test.txt` | Identifiants fictifs locaux |
| `captures/` | Captures d’exécution (si déposées) |
| `resultat-execution.md` | Preuve PASS / VALIDÉ |

## Notes techniques (corrections appliquées)

- Attentes SPA Angular (`waitForElementVisible`) + sélecteurs `data-testid`
- Reset session via `open` `/` puis `executeScript` (IDE 4 ne reconnaît pas `deleteAllCookies`)
