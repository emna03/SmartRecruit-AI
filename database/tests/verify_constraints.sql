-- Test du comportement des relations et contraintes dans MySQL.
-- Tout est exécuté dans une transaction annulée à la fin : la base reste inchangée.
-- Les lignes "ERROR" affichées sont ATTENDUES (contraintes qui refusent des données invalides).
-- Usage : Get-Content database/tests/verify_constraints.sql | docker exec -i smartrecruit-mysql sh -c 'mysql -t --force -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" smartrecruit'

START TRANSACTION;

SET @now = UTC_TIMESTAMP(6);
SET @role_candidat = (SELECT id FROM roles WHERE name = 'CANDIDAT');
SET @role_recruteur = (SELECT id FROM roles WHERE name = 'RECRUTEUR');

-- Jeu de données minimal
INSERT INTO users (email, password_hash, first_name, last_name, role_id, created_at, updated_at)
VALUES ('cand@test.local', 'x', 'Alice', 'Test', @role_candidat, @now, @now);
SET @user_cand = LAST_INSERT_ID();
INSERT INTO users (email, password_hash, first_name, last_name, role_id, created_at, updated_at)
VALUES ('recr@test.local', 'x', 'Bob', 'Test', @role_recruteur, @now, @now);
SET @user_recr = LAST_INSERT_ID();

INSERT INTO candidate_profiles (user_id, created_at, updated_at) VALUES (@user_cand, @now, @now);
SET @cand = LAST_INSERT_ID();
INSERT INTO companies (name, created_at, updated_at) VALUES ('TestCorp', @now, @now);
SET @company = LAST_INSERT_ID();
INSERT INTO recruiter_profiles (user_id, company_id, created_at, updated_at) VALUES (@user_recr, @company, @now, @now);
SET @recr = LAST_INSERT_ID();
INSERT INTO job_offers (company_id, recruiter_id, title, description, contract_type, status, created_at, updated_at)
VALUES (@company, @recr, 'Dev Java', 'desc', 'CDI', 'PUBLIEE', @now, @now);
SET @offer = LAST_INSERT_ID();
INSERT INTO skills (name) VALUES ('TestSkill');
SET @skill = LAST_INSERT_ID();
INSERT INTO job_offer_skills (job_offer_id, skill_id) VALUES (@offer, @skill);
INSERT INTO candidate_skills (candidate_id, skill_id) VALUES (@cand, @skill);
INSERT INTO cvs (candidate_id, original_file_name, stored_file_name, content_type, file_size, parsing_status, created_at, updated_at)
VALUES (@cand, 'cv.pdf', 'test-uuid.pdf', 'application/pdf', 1000, 'TERMINE', @now, @now);
SET @cv = LAST_INSERT_ID();
INSERT INTO cv_skills (cv_id, skill_id) VALUES (@cv, @skill);
INSERT INTO educations (candidate_id, degree, institution) VALUES (@cand, 'Ingénieur', 'ESPRIT');
INSERT INTO experiences (candidate_id, job_title, company_name, start_date) VALUES (@cand, 'Stagiaire', 'X', '2025-01-01');
INSERT INTO applications (candidate_id, job_offer_id, created_at, updated_at) VALUES (@cand, @offer, @now, @now);
INSERT INTO matching_results (candidate_id, job_offer_id, score, algorithm_version, computed_at)
VALUES (@cand, @offer, 72.00, 'test', @now);

SELECT 'T01 statut par défaut EN_ATTENTE' AS test,
       IF((SELECT status FROM applications WHERE candidate_id = @cand) = 'EN_ATTENTE', 'OK', 'KO') AS resultat;

-- ---------- Refus attendus (chaque instruction doit afficher une ERROR) ----------
SELECT 'T02-T10 : 9 erreurs attendues ci-dessous' AS info;
-- T02 email dupliqué
INSERT INTO users (email, password_hash, first_name, last_name, role_id, created_at, updated_at)
VALUES ('cand@test.local', 'x', 'A', 'B', @role_candidat, @now, @now);
-- T03 rôle inexistant
INSERT INTO users (email, password_hash, first_name, last_name, role_id, created_at, updated_at)
VALUES ('ghost@test.local', 'x', 'A', 'B', 999999, @now, @now);
-- T04 deuxième candidature à la même offre
INSERT INTO applications (candidate_id, job_offer_id, created_at, updated_at) VALUES (@cand, @offer, @now, @now);
-- T05 statut de candidature invalide
UPDATE applications SET status = 'INCONNU' WHERE candidate_id = @cand;
-- T06 score hors de [0, 100]
UPDATE matching_results SET score = 120 WHERE candidate_id = @cand;
-- T07 deuxième CV pour le même candidat
INSERT INTO cvs (candidate_id, original_file_name, stored_file_name, content_type, file_size, parsing_status, created_at, updated_at)
VALUES (@cand, 'cv2.pdf', 'other.pdf', 'application/pdf', 10, 'TERMINE', @now, @now);
-- T08 offre rattachée à une entreprise inexistante
INSERT INTO job_offers (company_id, title, description, contract_type, status, created_at, updated_at)
VALUES (999999, 't', 'd', 'CDI', 'PUBLIEE', @now, @now);
-- T09 salaire max < salaire min
UPDATE job_offers SET salary_min = 3000, salary_max = 1000 WHERE id = @offer;
-- T10 suppression d'un rôle utilisé (RESTRICT)
DELETE FROM roles WHERE id = @role_candidat;

-- ---------- Règles de suppression ----------
-- Suppression du recruteur : l'offre reste, recruiter_id passe à NULL
DELETE FROM users WHERE id = @user_recr;
SELECT 'T11 suppression recruteur -> offre conservée, recruiter_id NULL' AS test,
       IF((SELECT COUNT(*) FROM job_offers WHERE id = @offer AND recruiter_id IS NULL) = 1, 'OK', 'KO') AS resultat;

-- Suppression de l'utilisateur candidat : cascade sur profil, CV, compétences, formations, expériences,
-- candidatures et résultats de matching
DELETE FROM users WHERE id = @user_cand;
SELECT 'T12 suppression candidat -> cascade complète' AS test,
       IF((SELECT COUNT(*) FROM candidate_profiles WHERE id = @cand)
        + (SELECT COUNT(*) FROM cvs WHERE id = @cv)
        + (SELECT COUNT(*) FROM cv_skills WHERE cv_id = @cv)
        + (SELECT COUNT(*) FROM candidate_skills WHERE candidate_id = @cand)
        + (SELECT COUNT(*) FROM educations WHERE candidate_id = @cand)
        + (SELECT COUNT(*) FROM experiences WHERE candidate_id = @cand)
        + (SELECT COUNT(*) FROM applications WHERE candidate_id = @cand)
        + (SELECT COUNT(*) FROM matching_results WHERE candidate_id = @cand) = 0, 'OK', 'KO') AS resultat;

-- Suppression de l'entreprise : cascade sur ses offres et leurs compétences requises
DELETE FROM companies WHERE id = @company;
SELECT 'T13 suppression entreprise -> offres supprimées' AS test,
       IF((SELECT COUNT(*) FROM job_offers WHERE id = @offer)
        + (SELECT COUNT(*) FROM job_offer_skills WHERE job_offer_id = @offer) = 0, 'OK', 'KO') AS resultat;

SELECT 'T14 compétence du référentiel conservée' AS test,
       IF((SELECT COUNT(*) FROM skills WHERE id = @skill) = 1, 'OK', 'KO') AS resultat;

ROLLBACK;

SELECT 'Base restaurée (ROLLBACK) : utilisateurs restants' AS info, COUNT(*) AS nb FROM users;
