# Prompts — SmartRecruit AI

Ce fichier sépare clairement deux usages des prompts, conformément au cours
« Prompt Engineering » et à la séance sur la génération de code assistée.

## A. Prompts de développement (Cursor)

Ces prompts ont servi à **développer** SmartRecruit avec un assistant de code.
Ils ne sont **pas** exécutés par l'application en production.

| Objectif | Résumé fidèle du prompt | Résultat | Verdict | Validation |
|---|---|---|---|---|
| Intégrer un LLM Gemini pour un résumé professionnel (étape 12.5) | Spécifier architecture Angular→Spring→FastAPI→Gemini, Prompt Engineering Rôle/Contexte/Tâche/Format/Contraintes, few-shot, anti-hallucination, prompt injection, tests mockés + un appel réel, documentation | Module `services/llm/`, endpoints, UI, tests, docs | Accepté après itérations (filtre mots-clés trop permissif corrigé) | pytest LLM, JUnit `ProfessionalSummaryApiTest`, Vitest page CV, appel Gemini réel |
| Audit du projet avant LLM | Vérifier dans le code (pas seulement les docs) ML, NLP, matching, chatbot, absence de LLM | Rapport d'audit | Accepté | Lecture code + exécution des suites |

Aucun historique inventé : seules les demandes réellement utilisées dans cette phase sont listées.
D'autres prompts de développement des étapes 1–13 existent dans l'historique Cursor mais ne sont
pas reconstitués ici faute de journal versionné dans le dépôt.

## B. Prompt exécuté par SmartRecruit (Gemini)

**Unique prompt runtime** : génération du résumé professionnel.

- **Fichier** : `ai-service/app/services/llm/prompts.py`
- **Version** : `1.0.0` (`PROMPT_VERSION`)
- **Modèle** : `gemini-3.5-flash-lite`
- **Structure** : Rôle + Contexte + Tâche + Format + Contraintes (system instruction) ;
  few-shot fictif (2 exemples) + données candidat dans `<candidate_data>` (user message)
- **Paramètres** : `temperature=0.2`, `max_output_tokens=512`, JSON schema structuré
- **Protections** : données = données (anti prompt-injection) ; filtre anti-hallucination des mots-clés ;
  revalidation Pydantic + Bean Validation Spring

Le chatbot RH, le matching et le CV Parser **n'utilisent aucun prompt LLM**.
