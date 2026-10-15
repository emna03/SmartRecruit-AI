-- Étape 12.6 : scores sémantiques (Sentence Transformer) + cluster K-Means du candidat.
-- Aucune donnée personnelle supplémentaire ; colonnes nullable pour non-régression.

ALTER TABLE matching_results
    ADD COLUMN semantic_similarity DECIMAL(6, 4) NULL,
    ADD COLUMN semantic_score DECIMAL(5, 2) NULL,
    ADD COLUMN semantic_model VARCHAR(120) NULL;

ALTER TABLE candidate_profiles
    ADD COLUMN cluster_id INT NULL,
    ADD COLUMN cluster_description VARCHAR(255) NULL,
    ADD COLUMN cluster_assigned_at DATETIME(6) NULL;
