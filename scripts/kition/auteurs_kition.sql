-- Trois personnes (comptes désactivés, sans mot de passe utilisable) rattachées à l'organisation :
-- elles peuvent être choisies comme auteurs des unités d'enregistrement, mais ne peuvent pas se connecter.
-- L'organisation est retrouvée par son nom (ici « Chypre »).
BEGIN;

-- Arrêt si l'organisation n'existe pas (sinon les comptes seraient créés sans être rattachés).
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM institution WHERE lower(institution_name) = lower('Chypre')) THEN
        RAISE EXCEPTION 'Organisation Chypre introuvable';
    END IF;
END $$;

INSERT INTO person (person_id, name, lastname, username, mail, password, is_enabled, mail_verified)
SELECT nextval(pg_get_serial_sequence('person', 'person_id')), v.name, v.lastname, v.username, v.mail, '!disabled', false, true
FROM (VALUES
    ('Anna',     'Cannavò',   'anna.cannavo',       'anna.cannavo@kition-pervolia.invalid'),
    ('Nathalia', 'Denninger', 'nathalia.denninger', 'nathalia.denninger@kition-pervolia.invalid'),
    ('Sabine',   'Fourrier',  'sabine.fourrier',    'sabine.fourrier@kition-pervolia.invalid')
) AS v(name, lastname, username, mail)
WHERE NOT EXISTS (SELECT 1 FROM person p WHERE p.username = v.username);

INSERT INTO person_profile_assignment (fk_person_id, fk_profile_id)
SELECT p.person_id, pr.profile_id
FROM person p
JOIN profile pr ON pr.code = 'ORGANIZATION_MEMBER' AND pr.fk_institution_id = (SELECT institution_id FROM institution WHERE lower(institution_name) = lower('Chypre'))
WHERE p.username IN ('anna.cannavo', 'nathalia.denninger', 'sabine.fourrier')
  AND NOT EXISTS (SELECT 1 FROM person_profile_assignment a WHERE a.fk_person_id = p.person_id AND a.fk_profile_id = pr.profile_id);

SELECT p.person_id, p.name, p.lastname, p.username, p.is_enabled, a.fk_profile_id
FROM person p LEFT JOIN person_profile_assignment a ON a.fk_person_id = p.person_id
WHERE p.username IN ('anna.cannavo', 'nathalia.denninger', 'sabine.fourrier') ORDER BY p.person_id;

COMMIT;
