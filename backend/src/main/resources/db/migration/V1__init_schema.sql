-- ============================================================
-- SmartRecruit AI - V1 : schéma initial
-- Ne jamais modifier ce fichier une fois mergé : créer V2__..., V3__...
-- ============================================================

-- ---------- Utilisateurs et rôles ----------
CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    enabled       BIT(1)       NOT NULL DEFAULT b'1',
    role_id       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------- Entreprises et recruteurs ----------
CREATE TABLE companies (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    sector      VARCHAR(100),
    website     VARCHAR(255),
    location    VARCHAR(150),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_companies_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE recruiter_profiles (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    company_id BIGINT,
    job_title  VARCHAR(100),
    phone      VARCHAR(30),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recruiter_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_recruiter_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recruiter_profiles_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE SET NULL
) ENGINE = InnoDB;

-- ---------- Candidats ----------
CREATE TABLE candidate_profiles (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    headline            VARCHAR(150),
    summary             TEXT,
    phone               VARCHAR(30),
    location            VARCHAR(150),
    years_of_experience INT,
    education_level     VARCHAR(30),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_candidate_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_candidate_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_candidate_profiles_years CHECK (years_of_experience IS NULL OR years_of_experience >= 0)
) ENGINE = InnoDB;

CREATE TABLE skills (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    PRIMARY KEY (id),
    CONSTRAINT uk_skills_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE candidate_skills (
    candidate_id BIGINT NOT NULL,
    skill_id     BIGINT NOT NULL,
    PRIMARY KEY (candidate_id, skill_id),
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE educations (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    candidate_id   BIGINT       NOT NULL,
    degree         VARCHAR(150) NOT NULL,
    field_of_study VARCHAR(150),
    institution    VARCHAR(150) NOT NULL,
    start_date     DATE,
    end_date       DATE,
    description    TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_educations_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_educations_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
) ENGINE = InnoDB;

CREATE TABLE experiences (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT       NOT NULL,
    job_title    VARCHAR(150) NOT NULL,
    company_name VARCHAR(150) NOT NULL,
    location     VARCHAR(150),
    start_date   DATE         NOT NULL,
    end_date     DATE,
    description  TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_experiences_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_experiences_dates CHECK (end_date IS NULL OR end_date >= start_date)
) ENGINE = InnoDB;

-- ---------- CV ----------
CREATE TABLE cvs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    candidate_id       BIGINT       NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name   VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    file_size          BIGINT       NOT NULL,
    extracted_text     LONGTEXT,
    extracted_data     LONGTEXT,
    parsing_status     VARCHAR(20)  NOT NULL,
    parsing_error      VARCHAR(500),
    parsed_at          DATETIME(6),
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cvs_candidate UNIQUE (candidate_id),
    CONSTRAINT uk_cvs_stored_file UNIQUE (stored_file_name),
    CONSTRAINT fk_cvs_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT ck_cvs_file_size CHECK (file_size > 0),
    CONSTRAINT ck_cvs_parsing_status CHECK (parsing_status IN ('EN_COURS', 'TERMINE', 'ECHEC'))
) ENGINE = InnoDB;

CREATE TABLE cv_skills (
    cv_id    BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (cv_id, skill_id),
    CONSTRAINT fk_cv_skills_cv FOREIGN KEY (cv_id) REFERENCES cvs (id) ON DELETE CASCADE,
    CONSTRAINT fk_cv_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------- Offres d'emploi ----------
CREATE TABLE job_offers (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    company_id           BIGINT        NOT NULL,
    recruiter_id         BIGINT,
    title                VARCHAR(150)  NOT NULL,
    description          TEXT          NOT NULL,
    location             VARCHAR(150),
    contract_type        VARCHAR(20)   NOT NULL,
    min_experience_years INT           NOT NULL DEFAULT 0,
    education_level      VARCHAR(30),
    salary_min           DECIMAL(10, 2),
    salary_max           DECIMAL(10, 2),
    status               VARCHAR(20)   NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_job_offers_status (status),
    INDEX idx_job_offers_title (title),
    CONSTRAINT fk_job_offers_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_offers_recruiter FOREIGN KEY (recruiter_id) REFERENCES recruiter_profiles (id) ON DELETE SET NULL,
    CONSTRAINT ck_job_offers_experience CHECK (min_experience_years >= 0),
    CONSTRAINT ck_job_offers_salary CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min),
    CONSTRAINT ck_job_offers_status CHECK (status IN ('BROUILLON', 'PUBLIEE', 'FERMEE')),
    CONSTRAINT ck_job_offers_contract CHECK (contract_type IN ('CDI', 'CDD', 'STAGE', 'ALTERNANCE', 'FREELANCE'))
) ENGINE = InnoDB;

CREATE TABLE job_offer_skills (
    job_offer_id BIGINT NOT NULL,
    skill_id     BIGINT NOT NULL,
    PRIMARY KEY (job_offer_id, skill_id),
    CONSTRAINT fk_job_offer_skills_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_offer_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------- Candidatures ----------
CREATE TABLE applications (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT      NOT NULL,
    job_offer_id BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    cover_letter TEXT,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_application_candidate_offer UNIQUE (candidate_id, job_offer_id),
    INDEX idx_applications_status (status),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    CONSTRAINT ck_applications_status CHECK (status IN ('EN_ATTENTE', 'PRESELECTIONNE', 'ACCEPTE', 'REFUSE'))
) ENGINE = InnoDB;

-- ---------- Résultats de matching ----------
CREATE TABLE matching_results (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    candidate_id      BIGINT       NOT NULL,
    job_offer_id      BIGINT       NOT NULL,
    score             DECIMAL(5, 2) NOT NULL,
    skills_score      DECIMAL(5, 2),
    experience_score  DECIMAL(5, 2),
    education_score   DECIMAL(5, 2),
    matched_skills    TEXT,
    missing_skills    TEXT,
    explanation       TEXT,
    algorithm_version VARCHAR(20)  NOT NULL,
    computed_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_matching_candidate_offer UNIQUE (candidate_id, job_offer_id),
    INDEX idx_matching_offer_score (job_offer_id, score),
    CONSTRAINT fk_matching_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_matching_offer FOREIGN KEY (job_offer_id) REFERENCES job_offers (id) ON DELETE CASCADE,
    CONSTRAINT ck_matching_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_matching_sub_scores CHECK (
        (skills_score IS NULL OR skills_score BETWEEN 0 AND 100) AND
        (experience_score IS NULL OR experience_score BETWEEN 0 AND 100) AND
        (education_score IS NULL OR education_score BETWEEN 0 AND 100))
) ENGINE = InnoDB;

-- ---------- Données de référence ----------
INSERT INTO roles (name) VALUES ('ADMIN'), ('RECRUTEUR'), ('CANDIDAT');
