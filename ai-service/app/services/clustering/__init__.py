"""Clustering non supervisé des profils candidats (K-Means).

Entraîné sur des représentations professionnelles (embeddings Sentence Transformer),
sans donnée d'identité.
"""

MODEL_VERSION = "1.0.0"
ALGORITHM = "KMEANS"
MIN_PROFILES = 6
K_MIN = 2
K_MAX = 8
