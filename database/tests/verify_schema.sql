-- Inspection du schéma créé par Flyway (lecture seule).
-- Usage : Get-Content database/tests/verify_schema.sql | docker exec -i smartrecruit-mysql sh -c 'mysql -t -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" smartrecruit'

SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
ORDER BY table_name;

SELECT COUNT(*) AS nb_tables_metier
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history';

-- Clés étrangères et règle de suppression
SELECT k.table_name AS table_enfant, k.column_name AS colonne, k.referenced_table_name AS table_parent,
       r.delete_rule AS on_delete
FROM information_schema.key_column_usage k
JOIN information_schema.referential_constraints r
  ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
WHERE k.table_schema = DATABASE()
ORDER BY k.table_name, k.column_name;

-- Contraintes d'unicité (hors clés primaires)
SELECT table_name, constraint_name
FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND constraint_type = 'UNIQUE' AND table_name <> 'flyway_schema_history'
ORDER BY table_name;

-- Contraintes CHECK
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
ORDER BY constraint_name;

SELECT id, name FROM roles ORDER BY id;
