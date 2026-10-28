--liquibase formatted sql
--changeset siamois:global_search runOnChange:true splitStatements:false

DROP FUNCTION IF EXISTS global_search;

-- Recherche globale sectionnée (barre du haut) : au plus p_limit lignes par type d'entité, avec le
-- total des correspondances de ce type (window count avant la coupe).
--   p_project_ids         : restreint à ces projets (action_unit) ; NULL = pas de restriction
--   p_visible_project_ids : projets visibles de l'appelant ; NULL = toute l'institution
--   p_types               : types demandés ('project','recordingUnit','find','place') ; NULL = tous
CREATE OR REPLACE FUNCTION global_search(
    p_input VARCHAR,
    p_institution_id BIGINT,
    p_project_ids BIGINT[],
    p_visible_project_ids BIGINT[],
    p_types VARCHAR[],
    p_limit INT
) RETURNS TABLE
          (
              entity_type      VARCHAR,
              entity_id        BIGINT,
              label            VARCHAR,
              subtitle         VARCHAR,
              project_id       BIGINT,
              similarity_score REAL,
              total            BIGINT
          )
AS
$$
DECLARE
    v_pattern VARCHAR := '%' || replace(replace(replace(p_input, '\', '\\'), '%', '\%'), '_', '\_') || '%';
BEGIN
    IF p_types IS NULL OR 'project' = ANY (p_types) THEN
        RETURN QUERY
            SELECT 'project'::VARCHAR, r.id, r.lbl, r.sub, r.id, r.score, r.cnt
            FROM (SELECT au.action_unit_id                                                       AS id,
                         au.full_identifier::VARCHAR                                             AS lbl,
                         au.name::VARCHAR                                                        AS sub,
                         greatest(similarity(au.full_identifier, p_input), similarity(au.name, p_input)) AS score,
                         count(*) OVER ()                                                        AS cnt,
                         row_number() OVER (ORDER BY greatest(similarity(au.full_identifier, p_input),
                                                              similarity(au.name, p_input)) DESC,
                                                     au.action_unit_id)                          AS rn
                  FROM action_unit au
                  WHERE (au.full_identifier ILIKE v_pattern OR au.name ILIKE v_pattern)
                    AND au.fk_institution_id = p_institution_id
                    AND (p_project_ids IS NULL OR au.action_unit_id = ANY (p_project_ids))
                    AND (p_visible_project_ids IS NULL OR au.action_unit_id = ANY (p_visible_project_ids))) r
            WHERE r.rn <= p_limit;
    END IF;

    IF p_types IS NULL OR 'recordingUnit' = ANY (p_types) THEN
        RETURN QUERY
            SELECT 'recordingUnit'::VARCHAR, r.id, r.lbl, r.sub, r.pid, r.score, r.cnt
            FROM (SELECT ru.recording_unit_id                                   AS id,
                         ru.full_identifier::VARCHAR                            AS lbl,
                         au.full_identifier::VARCHAR                            AS sub,
                         au.action_unit_id                                      AS pid,
                         similarity(ru.full_identifier, p_input)                AS score,
                         count(*) OVER ()                                       AS cnt,
                         row_number() OVER (ORDER BY similarity(ru.full_identifier, p_input) DESC,
                                                     ru.recording_unit_id)      AS rn
                  FROM recording_unit ru
                           JOIN action_unit au ON au.action_unit_id = ru.fk_action_unit_id
                  WHERE ru.full_identifier ILIKE v_pattern
                    AND ru.fk_institution_id = p_institution_id
                    AND (p_project_ids IS NULL OR au.action_unit_id = ANY (p_project_ids))
                    AND (p_visible_project_ids IS NULL OR au.action_unit_id = ANY (p_visible_project_ids))) r
            WHERE r.rn <= p_limit;
    END IF;

    IF p_types IS NULL OR 'find' = ANY (p_types) THEN
        RETURN QUERY
            SELECT 'find'::VARCHAR, r.id, r.lbl, r.sub, r.pid, r.score, r.cnt
            FROM (SELECT s.specimen_id                                          AS id,
                         s.full_identifier::VARCHAR                             AS lbl,
                         au.full_identifier::VARCHAR                            AS sub,
                         au.action_unit_id                                      AS pid,
                         similarity(s.full_identifier, p_input)                 AS score,
                         count(*) OVER ()                                       AS cnt,
                         row_number() OVER (ORDER BY similarity(s.full_identifier, p_input) DESC,
                                                     s.specimen_id)             AS rn
                  FROM specimen s
                           JOIN recording_unit ru ON ru.recording_unit_id = s.fk_recording_unit_id
                           JOIN action_unit au ON au.action_unit_id = COALESCE(s.fk_action_unit_id, ru.fk_action_unit_id)
                  WHERE s.full_identifier ILIKE v_pattern
                    AND s.fk_institution_id = p_institution_id
                    AND (p_project_ids IS NULL OR au.action_unit_id = ANY (p_project_ids))
                    AND (p_visible_project_ids IS NULL OR au.action_unit_id = ANY (p_visible_project_ids))) r
            WHERE r.rn <= p_limit;
    END IF;

    IF p_types IS NULL OR 'place' = ANY (p_types) THEN
        -- Un lieu n'a pas de projet propre : il est rattaché à un projet par son contexte spatial
        -- ou sa localisation principale. Sans restriction de projet, on cherche dans toute l'institution
        -- (sauf pour un appelant à périmètre restreint, qui ne voit que les lieux de ses projets).
        RETURN QUERY
            SELECT 'place'::VARCHAR, r.id, r.lbl, NULL::VARCHAR, NULL::BIGINT, r.score, r.cnt
            FROM (SELECT su.spatial_unit_id                                     AS id,
                         su.name::VARCHAR                                       AS lbl,
                         similarity(su.name, p_input)                           AS score,
                         count(*) OVER ()                                       AS cnt,
                         row_number() OVER (ORDER BY similarity(su.name, p_input) DESC,
                                                     su.spatial_unit_id)        AS rn
                  FROM spatial_unit su
                  WHERE su.name ILIKE v_pattern
                    AND su.fk_institution_id = p_institution_id
                    AND (p_project_ids IS NULL AND p_visible_project_ids IS NULL
                        OR EXISTS (SELECT 1
                                   FROM action_unit au
                                   WHERE (au.fk_main_location = su.spatial_unit_id
                                       OR EXISTS (SELECT 1
                                                  FROM action_unit_spatial_context c
                                                  WHERE c.fk_action_unit_id = au.action_unit_id
                                                    AND c.fk_spatial_unit_id = su.spatial_unit_id))
                                     AND (p_project_ids IS NULL OR au.action_unit_id = ANY (p_project_ids))
                                     AND (p_visible_project_ids IS NULL
                                       OR au.action_unit_id = ANY (p_visible_project_ids))))) r
            WHERE r.rn <= p_limit;
    END IF;
END;
$$ LANGUAGE plpgsql;
