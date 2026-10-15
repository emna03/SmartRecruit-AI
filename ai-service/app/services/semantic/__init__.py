"""Matching sémantique via Sentence Transformer pré-entraîné (embeddings + similarité cosinus).

Ce n'est PAS un modèle entraîné par l'équipe SmartRecruit : nous utilisons un modèle public
pré-entraîné pour produire des embeddings.
"""

MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
MODEL_VERSION = "1.0.0"
PROVIDER = "sentence-transformers"
