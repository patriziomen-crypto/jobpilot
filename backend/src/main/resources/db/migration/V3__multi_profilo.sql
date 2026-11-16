-- Più persone: le candidature e le analisi appartengono a un profilo; le offerte restano condivise.

-- 1. Candidatura legata al profilo (le esistenti vanno al profilo 1)
ALTER TABLE application ADD COLUMN profile_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE application ADD CONSTRAINT fk_application_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE;
-- prima il nuovo indice (serve anche alla chiave esterna su job_offer_id), poi si toglie il vecchio
ALTER TABLE application ADD UNIQUE KEY uk_application_job_profile (job_offer_id, profile_id);
ALTER TABLE application DROP INDEX uk_application_job;

-- 2. Analisi legata alla candidatura (non più all'offerta): persone diverse hanno punteggi diversi
ALTER TABLE job_analysis ADD COLUMN application_id BIGINT NULL AFTER id;
UPDATE job_analysis a JOIN application app ON app.job_offer_id = a.job_offer_id SET a.application_id = app.id;
DELETE FROM job_analysis WHERE application_id IS NULL;
ALTER TABLE job_analysis DROP FOREIGN KEY fk_analysis_job;
ALTER TABLE job_analysis DROP INDEX uk_analysis_job;
ALTER TABLE job_analysis DROP COLUMN job_offer_id;
ALTER TABLE job_analysis MODIFY application_id BIGINT NOT NULL;
ALTER TABLE job_analysis ADD CONSTRAINT fk_analysis_application FOREIGN KEY (application_id) REFERENCES application(id) ON DELETE CASCADE;
ALTER TABLE job_analysis ADD UNIQUE KEY uk_analysis_application (application_id);
ALTER TABLE job_analysis ADD COLUMN provider VARCHAR(60) NULL AFTER model;

-- 3. Impostazioni di ricerca per profilo
CREATE TABLE profile_search (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id      BIGINT NOT NULL,
    keywords        TEXT,                              -- una per riga
    location        VARCHAR(160) NOT NULL DEFAULT 'Roma, Lazio, Italia',
    remote          BOOLEAN NOT NULL DEFAULT TRUE,
    days            INT NOT NULL DEFAULT 7,
    linkedin        BOOLEAN NOT NULL DEFAULT TRUE,
    indeed          BOOLEAN NOT NULL DEFAULT TRUE,
    auto_analyze    BOOLEAN NOT NULL DEFAULT TRUE,
    max_per_query   INT NOT NULL DEFAULT 10,
    scheduled       BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_profile_search_profile FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE,
    UNIQUE KEY uk_profile_search (profile_id)
);

INSERT INTO profile_search (profile_id, keywords)
VALUES (1, 'junior java developer\nsviluppatore junior\nstage sviluppatore software');
