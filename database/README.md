# Base de données — MySQL

## Démarrage (Docker)

La base MySQL 8.4 est définie dans `docker-compose.yml` (racine). Les identifiants sont lus dans `.env`.

```bash
cp .env.example .env          # puis remplir DB_PASSWORD, DB_ROOT_PASSWORD, ...
docker compose up -d mysql
docker compose ps             # attendre l'état "healthy"
```

Le port est exposé uniquement sur `127.0.0.1:${DB_PORT}` (3306 par défaut).
Si le port 3306 est déjà utilisé sur ta machine, change `DB_PORT` dans ton `.env` (ex : 3310).

Réinitialiser complètement la base (supprime toutes les données) :

```bash
docker compose down -v
```

## Migrations

Le schéma est géré par **Flyway**, exécuté automatiquement au démarrage du backend.
Les scripts sont dans `backend/src/main/resources/db/migration/` :

```
V1__xxx.sql, V2__xxx.sql, ...
```

Règles : un nouveau fichier par changement, numéro croissant, ne jamais modifier un script déjà mergé.
Hibernate est en mode `validate` : il ne modifie jamais le schéma.

Modèle de données détaillé (diagramme, règles d'intégrité) : `docs/database.md`.

## Scripts de vérification

- `tests/verify_schema.sql` : liste les tables, clés étrangères, contraintes d'unicité et CHECK.
- `tests/verify_constraints.sql` : teste les contraintes et les cascades dans une transaction annulée.

Commandes : voir `docs/database.md`.

## Données de démonstration

Le dossier `seed/` contiendra les données fictives de démonstration (comptes, entreprises, offres).
