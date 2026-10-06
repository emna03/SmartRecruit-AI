# Backend — Spring Boot

API REST principale de SmartRecruit AI : Java 21, Spring Boot 4.1, Maven Wrapper (aucune installation de Maven nécessaire).

Responsabilités : sécurité JWT et rôles, logique métier, persistance MySQL (JPA + Flyway),
appels au service IA FastAPI.

Structure des packages : voir `docs/architecture.md` (section 3.1).

## Configuration

`application.yml` ne contient aucun secret : les valeurs viennent des variables d'environnement,
ou du fichier `.env` (cherché dans `backend/` puis à la racine du dépôt). Modèle : `.env.example`.

## Commandes

```bash
# depuis backend/ (Windows : mvnw.cmd, Linux/macOS : ./mvnw)
mvnw.cmd test              # tests (base H2 en mémoire, profil "test", pas besoin de MySQL)
mvnw.cmd spring-boot:run   # lance l'API (MySQL doit être démarré : docker compose up -d mysql)
```

Vérification : `GET http://localhost:${SERVER_PORT}/actuator/health` → `{"status":"UP"}`
(le statut inclut la connexion à la base).
