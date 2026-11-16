-- Schema iniziale di JobPilot (MySQL 9.7, utf8mb4)

-- Profilo master: una sola riga, fonte di verità per CV e lettere
CREATE TABLE profile (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(120)  NOT NULL,
    headline        VARCHAR(160)  NOT NULL,          -- es. "Sviluppatore Junior Full Stack"
    location        VARCHAR(120),
    phone           VARCHAR(40),
    email           VARCHAR(160),
    linkedin_url    VARCHAR(255),
    github_url      VARCHAR(255),
    summary         TEXT,                            -- profilo professionale
    languages       VARCHAR(255),                    -- es. "Italiano (madrelingua), Inglese (base)"
    extra_info      VARCHAR(255),                    -- es. "Patente B, automunito"
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE experience (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    company         VARCHAR(160) NOT NULL,
    role            VARCHAR(160) NOT NULL,
    period_label    VARCHAR(80),                     -- es. "2025, 140 ore"
    bullets         TEXT,                            -- una riga per punto
    sort_order      INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_experience_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE
);

CREATE TABLE education (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    institution     VARCHAR(160) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    period_label    VARCHAR(80),
    details         TEXT,                            -- una riga per punto
    sort_order      INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_education_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE
);

CREATE TABLE skill (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    category        VARCHAR(80)  NOT NULL,           -- es. "Linguaggi & Web", "Strumenti & IDE"
    name            VARCHAR(80)  NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_skill_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE
);

-- Risposte pronte alle domande ricorrenti dei form di candidatura
CREATE TABLE standard_answer (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    question        VARCHAR(255) NOT NULL,
    answer          TEXT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_answer_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE
);

-- Offerte di lavoro importate (LinkedIn, Indeed, altro)
CREATE TABLE job_offer (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    source          VARCHAR(20)  NOT NULL,           -- LINKEDIN | INDEED | ALTRO
    url             VARCHAR(1000),
    url_hash        CHAR(64),                        -- SHA-256 dell'URL normalizzato, per evitare duplicati
    title           VARCHAR(200) NOT NULL,
    company         VARCHAR(160),
    location        VARCHAR(160),
    remote          BOOLEAN NOT NULL DEFAULT FALSE,
    description     MEDIUMTEXT NOT NULL,             -- testo grezzo dell'annuncio
    language        VARCHAR(10),                     -- "it" / "en"
    found_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_job_offer_url_hash (url_hash)
);

-- Risultato dell'analisi AI di un'offerta rispetto al profilo
CREATE TABLE job_analysis (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_offer_id            BIGINT NOT NULL,
    score                   INT NOT NULL,            -- compatibilità 0-100
    summary                 TEXT,                    -- 2-3 frasi: perché sì / perché no
    matched_requirements    JSON,                    -- lista di stringhe
    missing_requirements    JSON,
    keywords                JSON,                    -- parole chiave da usare nel CV
    red_flags               JSON,                    -- es. "richiesti 5 anni di esperienza"
    language                VARCHAR(10),             -- lingua dell'annuncio
    model                   VARCHAR(60),
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_analysis_job FOREIGN KEY (job_offer_id) REFERENCES job_offer(id) ON DELETE CASCADE,
    UNIQUE KEY uk_analysis_job (job_offer_id)
);

-- Candidatura: stato e materiali per ogni offerta
CREATE TABLE application (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_offer_id    BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'TROVATA',
    cv_path         VARCHAR(500),                    -- PDF del CV su misura
    cover_letter    TEXT,                            -- testo della lettera
    letter_path     VARCHAR(500),                    -- PDF della lettera
    sent_at         DATETIME(3),
    follow_up_at    DATE,
    notes           TEXT,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_application_job FOREIGN KEY (job_offer_id) REFERENCES job_offer(id) ON DELETE CASCADE,
    UNIQUE KEY uk_application_job (job_offer_id)
);
