DROP FUNCTION IF EXISTS base_search_action_unit;
DROP FUNCTION IF EXISTS base_search_spatial_unit;
DROP FUNCTION IF EXISTS base_search_recording_unit;
DROP FUNCTION IF EXISTS base_search_specimen;
DROP FUNCTION IF EXISTS basic_search;
DROP TYPE IF EXISTS basic_search_result;

CREATE TYPE basic_search_result AS
(
    matching_term     VARCHAR,
    action_unit_id    BIGINT,
    spatial_unit_id   BIGINT,
    recording_unit_id BIGINT,
    specimen_id       BIGINT,
    similarity_score  REAL
);

-- Chercher avec un input dans la table des unités d'actions
CREATE OR REPLACE FUNCTION base_search_action_unit(
    p_input VARCHAR,
    p_fk_institution_id action_unit.fk_institution_id%type
) RETURNS SETOF basic_search_result AS
$$
BEGIN
    RETURN QUERY
        SELECT resultats.matching_term,
               resultats.action_unit_id,
               resultats.spatial_unit_id,
               resultats.recording_unit_id,
               resultats.specimen_id,
               resultats.similarity_score::REAL
        FROM (SELECT au.full_identifier                      AS matching_term,
                     au.action_unit_id                  AS action_unit_id,
                     NULL::BIGINT                       AS spatial_unit_id,
                     NULL::BIGINT                       AS recording_unit_id,
                     NULL::BIGINT                       AS specimen_id,
                     similarity(au.full_identifier, p_input) AS similarity_score
              FROM action_unit au
              WHERE au.full_identifier ILIKE concat(left(p_input, 1), '%')
                AND au.fk_institution_id = p_fk_institution_id

              UNION ALL

              SELECT au.name                      AS matching_term,
                     au.action_unit_id            AS action_unit_id,
                     NULL::BIGINT                 AS spatial_unit_id,
                     NULL::BIGINT                 AS recording_unit_id,
                     NULL::BIGINT                 AS specimen_id,
                     similarity(au.name, p_input) AS similarity_score
              FROM action_unit au
              WHERE au.name ILIKE concat(left(p_input, 1), '%')
                AND au.fk_institution_id = p_fk_institution_id

              UNION ALL

              SELECT aac.fk_action_code_id                      AS matching_term,
                     aac.fk_action_id                           AS action_unit_id,
                     NULL::BIGINT                               AS spatial_unit_id,
                     NULL::BIGINT                               AS recording_unit_id,
                     NULL::BIGINT                               AS specimen_id,
                     similarity(aac.fk_action_code_id, p_input) AS similarity_score
              FROM action_action_code aac
                       JOIN public.action_unit au ON aac.fk_action_id = au.action_unit_id
              WHERE aac.fk_action_code_id ILIKE concat(left(p_input, 1), '%')
                AND au.fk_institution_id = p_fk_institution_id) AS resultats

        ORDER BY resultats.similarity_score DESC
        LIMIT 50;
END;
$$ LANGUAGE plpgsql;

-- Chercher avec un input dans la table des unités spatiales
CREATE OR REPLACE FUNCTION base_search_spatial_unit(
    p_input VARCHAR,
    p_fk_institution_id spatial_unit.fk_institution_id%type
) RETURNS SETOF basic_search_result AS
$$
BEGIN
    RETURN QUERY
        SELECT su.name::VARCHAR             AS matching_term,
               NULL::BIGINT                 AS action_unit_id,
               su.spatial_unit_id           AS spatial_unit_id,
               NULL::BIGINT                 AS recording_unit_id,
               NULL::BIGINT                 AS specimen_id,
               similarity(su.name, p_input) AS similarity_score
        FROM spatial_unit su
        WHERE su.name ILIKE concat(left(p_input, 1), '%')
          AND su.fk_institution_id = p_fk_institution_id
        ORDER BY similarity_score DESC
        LIMIT 50;
end
$$ language plpgsql;


-- Chercher avec un input dans la table des unités d'enregistrement
CREATE OR REPLACE FUNCTION base_search_recording_unit(
    p_input VARCHAR,
    p_fk_institution_id recording_unit.fk_institution_id%type
) RETURNS SETOF basic_search_result AS
$$
BEGIN
    RETURN QUERY
        SELECT ru.full_identifier::varchar             AS matching_term,
               NULL::BIGINT                            AS action_unit_id,
               NULL::BIGINT                            AS spatial_unit_id,
               ru.recording_unit_id                    AS recording_unit_id,
               NULL::BIGINT                            AS specimen_id,
               similarity(ru.full_identifier, p_input) AS similarity_score
        FROM recording_unit ru
        WHERE ru.full_identifier ILIKE concat(left(p_input, 1), '%')
          AND ru.fk_institution_id = p_fk_institution_id
        ORDER BY similarity_score DESC
        LIMIT 50;
end
$$ language plpgsql;

-- Chercher avec un input dans la table des prélèvements
CREATE OR REPLACE FUNCTION base_search_specimen(
    p_input VARCHAR,
    p_fk_institution_id specimen.fk_institution_id%type
) RETURNS SETOF basic_search_result AS
$$
BEGIN
    RETURN QUERY
        SELECT s.full_identifier                      AS matching_term,
               NULL::BIGINT                           AS action_unit_id,
               NULL::BIGINT                           AS spatial_unit_id,
               NULL::BIGINT                           AS recording_unit_id,
               s.specimen_id                          AS specimen_id,
               similarity(s.full_identifier, p_input) AS similarity_score
        FROM specimen s
        WHERE s.full_identifier ILIKE concat(left(p_input, 1), '%')
          AND s.fk_institution_id = p_fk_institution_id
        ORDER BY similarity_score DESC
        LIMIT 50;
end
$$ language plpgsql;

-- Appliquer la recherche sur les 4 tables
CREATE OR REPLACE FUNCTION basic_search(
    p_input VARCHAR,
    p_fk_institution_id BIGINT
) RETURNS SETOF basic_search_result AS
$$
BEGIN
    RETURN QUERY
        SELECT *
        FROM (SELECT *
              FROM base_search_action_unit(p_input, p_fk_institution_id)
              UNION
              SELECT *
              FROM base_search_spatial_unit(p_input, p_fk_institution_id)
              UNION
              SELECT *
              FROM base_search_recording_unit(p_input, p_fk_institution_id)
              UNION
              SELECT *
              FROM base_search_specimen(p_input, p_fk_institution_id)) AS results
        ORDER BY results.similarity_score DESC
        LIMIT 50;
end
$$ language plpgsql;
