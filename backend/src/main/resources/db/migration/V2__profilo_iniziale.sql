-- Profilo master iniziale, ricavato dal CV di Patrizio (modificabile poi dalla pagina Profilo).
-- Telefono ed email sono segnaposto: si inseriscono quelli veri dalla pagina Profilo.

INSERT INTO profile (id, full_name, headline, location, phone, email, summary, languages, extra_info)
VALUES (1, 'Patrizio Menegoni', 'Sviluppatore Junior Full Stack', 'Pomezia (RM)', '+39 000 000 0000',
        'nome@esempio.it',
        'Sviluppatore Junior Full Stack con forte passione per la tecnologia, l''innovazione e il mondo dell''Intelligenza Artificiale. Conoscenza pratica di linguaggi e framework tra cui Java, MySQL e Angular. Motivato, orientato al lavoro in team e con elevate capacità di apprendimento rapido. Cerco un''opportunità professionale come Programmatore per mettere a frutto le mie competenze tecniche, contribuire ai progetti aziendali e proseguire il mio percorso di crescita professionale.',
        'Italiano (madrelingua), Inglese (livello base)',
        'Patente B, automunito');

INSERT INTO experience (profile_id, company, role, period_label, bullets, sort_order) VALUES
(1, 'EVOCONS', 'Tirocinio Sviluppatore Software', '140 ore',
 'Partecipazione alle attività di supporto allo sviluppo software all''interno del team tecnico.\nApplicazione pratica delle nozioni di programmazione e gestione dei progetti informatici.', 0);

INSERT INTO education (profile_id, institution, title, period_label, details, sort_order) VALUES
(1, 'Accademia Informatica', 'Attestato da Programmatore Full Stack Junior', NULL,
 'Sviluppo Software & Coding: programmazione orientata agli oggetti e procedurale, linguaggi di scripting e markup, principi di business logic e responsive design.\nDatabase & Dati: modellazione, interrogazione e gestione delle basi di dati.\nCiclo di vita del software: definizione e conduzione del deployment, attività di testing e principi di universal design.\nSicurezza & Sistemi: basi di Cyber Security, inquadramento della professione e architettura dei sistemi informatici.', 0),
(1, 'Istituto Giovanni Paolo II', 'Diploma di Liceo Scientifico (Opzione Scienze Applicate)', NULL, NULL, 1);

INSERT INTO skill (profile_id, category, name, sort_order) VALUES
(1, 'Linguaggi & Web', 'Java', 0),
(1, 'Linguaggi & Web', 'Angular', 1),
(1, 'Linguaggi & Web', 'MySQL', 2),
(1, 'Linguaggi & Web', 'HTML/CSS', 3),
(1, 'Linguaggi & Web', 'JavaScript/TypeScript', 4),
(1, 'Strumenti & IDE', 'Eclipse', 10),
(1, 'Strumenti & IDE', 'VS Code', 11),
(1, 'Strumenti & IDE', 'Postman', 12),
(1, 'Strumenti & IDE', 'MySQL Workbench', 13),
(1, 'Strumenti & IDE', 'Git', 14),
(1, 'Sistemi operativi', 'Windows', 20),
(1, 'Sistemi operativi', 'Ubuntu (Linux)', 21),
(1, 'Collaborazione & Piattaforme', 'Microsoft Teams', 30),
(1, 'Collaborazione & Piattaforme', 'Google Meet', 31),
(1, 'Collaborazione & Piattaforme', 'Zoom', 32),
(1, 'Collaborazione & Piattaforme', 'Suite Office', 33);

INSERT INTO standard_answer (profile_id, question, answer, sort_order) VALUES
(1, 'Anni di esperienza come sviluppatore', '0-1 (tirocinio di 140 ore + progetti personali)', 0),
(1, 'Disponibilità a iniziare', 'Immediata', 1),
(1, 'Retribuzione attesa (RAL)', 'Da concordare in base al ruolo; in linea con le posizioni junior', 2),
(1, 'Autorizzazione a lavorare in Italia', 'Sì, cittadino italiano', 3),
(1, 'Disponibilità al lavoro in sede / ibrido', 'Sì, Roma e provincia; disponibile anche al remoto', 4),
(1, 'Livello di inglese', 'Base (A2/B1), in miglioramento', 5),
(1, 'Patente e auto', 'Patente B, automunito', 6);
