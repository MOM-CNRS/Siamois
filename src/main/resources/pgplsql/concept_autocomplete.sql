DROP FUNCTION IF EXISTS concept_autocomplete(BIGINT, VARCHAR, TEXT, INT);
DROP FUNCTION IF EXISTS concept_autocomplete_get_definition(BIGINT, VARCHAR);
DROP FUNCTION IF EXISTS concept_autocomplete_get_hierarchy(BIGINT, BIGINT);
DROP FUNCTION IF EXISTS concept_autocomplete_get_alt_labels(BIGINT, BIGINT, VARCHAR);
DROP FUNCTION IF EXISTS concept_get_label(BIGINT, BIGINT, VARCHAR);
DROP TYPE IF EXISTS concept_autocomplete_record;

CREATE TYPE concept_autocomplete_record AS
(
    concept_id                 BIGINT,
    concept_external_id        TEXT,

    vocabulary_id              BIGINT,
    vocabulary_base_uri        VARCHAR(255),
    vocabulary_external_id     VARCHAR(255),

    vocabylary_type_id         BIGINT,
    vocabulary_type_label      TEXT,

    data_aggregated_alt_labels TEXT,
    data_definition            TEXT,
    data_hierarchy_str         TEXT
);

-- Search for the preferred label of a concept in the given language,
-- falling back to the first available pref label if not found,
-- then to any alt label if no pref label exists,
-- and finally to a string representation of the concept id if no label exists.
CREATE OR REPLACE FUNCTION concept_get_label(
    p_concept_id BIGINT,
    p_field_concept_id BIGINT,
    p_langcode VARCHAR(3)
) RETURNS TEXT AS
$$
DECLARE
    v_pref_label TEXT;
BEGIN
    SELECT cl.label
    INTO v_pref_label
    FROM concept_label cl
    WHERE cl.fk_concept_id = p_concept_id
      AND cl.fk_field_parent_concept_id = p_field_concept_id
      AND cl.lang_code = p_langcode
      AND cl.label_type = 0
    LIMIT 1;

    IF v_pref_label IS NULL THEN
        SELECT cl.label || ' (' || cl.lang_code || ')'
        INTO v_pref_label
        FROM concept_label cl
        WHERE cl.fk_concept_id = p_concept_id
          AND cl.fk_field_parent_concept_id = p_field_concept_id
          AND cl.label_type = 0
        LIMIT 1;
    END IF;

    IF v_pref_label IS NULL THEN
        SELECT cl.label || ' (' || cl.lang_code || ')'
        INTO v_pref_label
        FROM concept_label cl
        WHERE cl.fk_concept_id = p_concept_id
          AND cl.fk_field_parent_concept_id = p_field_concept_id
        LIMIT 1;
    END IF;

    IF v_pref_label IS NULL THEN
        v_pref_label := '[' || p_concept_id || ']';
    end if;

    RETURN v_pref_label;
end
$$ language plpgsql;

-- Aggregates all alternative labels of a concept into a single comma-separated string
CREATE OR REPLACE FUNCTION concept_autocomplete_get_alt_labels(
    p_concept_id BIGINT,
    p_field_concept_id BIGINT,
    p_langcode VARCHAR(3)
)
    RETURNS TEXT AS
$$
DECLARE
    v_alt_labels TEXT;
BEGIN

    SELECT string_agg(cl.label, ',')
    INTO v_alt_labels
    FROM concept_label cl
    WHERE cl.fk_concept_id = p_concept_id
      AND cl.fk_field_parent_concept_id = p_field_concept_id
      AND cl.lang_code = p_langcode;

    RETURN v_alt_labels;
END;
$$ LANGUAGE plpgsql;


-- Returns the definition of a concept in the specified language
-- Fonction de récupération de la définition d'un concept
CREATE OR REPLACE FUNCTION concept_autocomplete_get_definition(
    p_concept_id BIGINT,
    p_langcode VARCHAR(3)
)
    RETURNS TEXT AS
$$
DECLARE
    v_definition TEXT;
BEGIN
    SELECT lcd.concept_definition
    INTO v_definition
    FROM localized_concept_data lcd
    WHERE lcd.fk_concept_id = p_concept_id
      AND lcd.lang_code = p_langcode;

    RETURN v_definition;
END;
$$ LANGUAGE plpgsql;

-- Returns the parents of the concept in a ' > ' separated string
CREATE OR REPLACE FUNCTION concept_autocomplete_get_hierarchy(
    p_concept_id BIGINT,
    p_field_concept_id BIGINT,
    p_langcode VARCHAR(3)
)
    RETURNS TEXT AS
$$
DECLARE
    v_parent_label  TEXT;
    v_parents       TEXT[];
    v_result_record concept_hierarchy%rowtype;
BEGIN
    SELECT ch.*
    INTO v_result_record
    FROM concept_hierarchy ch
    WHERE ch.fk_child_concept_id = p_concept_id
      AND ch.fk_parent_field_context_id = p_field_concept_id;

    WHILE v_result_record IS NOT NULL
        LOOP
        v_parent_label = concept_get_label(v_result_record.fk_parent_concept_id, p_field_concept_id, p_langcode);
        v_parents := array_prepend(v_parent_label, v_parents);

        SELECT ch.*
        INTO v_result_record
        FROM concept_hierarchy ch
        WHERE ch.fk_child_concept_id = v_result_record.fk_parent_concept_id
          AND ch.fk_parent_field_context_id = p_field_concept_id;
        END LOOP;

    RETURN array_to_string(v_parents, ' > ');
END;
$$ LANGUAGE plpgsql;

-- Main autocomplete function
CREATE OR REPLACE FUNCTION concept_autocomplete(
    p_field_concept_id BIGINT,
    p_langcode VARCHAR(3),
    p_input TEXT,
    p_limit INT
)
    RETURNS SETOF concept_autocomplete_record AS
$$
DECLARE
BEGIN
    -- Cas quand input est NULL ou vide
    RETURN QUERY
        SELECT c.concept_id,
               c.external_id,
               v.vocabulary_id,
               v.base_uri,
               v.external_id,
               vt.vocabulary_type_id,
               vt.label,
               concept_autocomplete_get_alt_labels(c.concept_id, p_field_concept_id, p_langcode),
               concept_autocomplete_get_definition(c.concept_id, p_langcode),
               concept_autocomplete_get_hierarchy(c.concept_id, p_field_concept_id, p_langcode)
        FROM concept_label cl
                 JOIN concept c ON cl.fk_concept_id = c.concept_id
                 JOIN vocabulary v ON c.fk_vocabulary_id = v.vocabulary_id
                 JOIN vocabulary_type vt ON v.fk_type_id = vt.vocabulary_type_id
        WHERE cl.fk_field_parent_concept_id = p_field_concept_id
          AND cl.lang_code = p_langcode
          AND NOT c.is_deleted
        LIMIT p_limit;

    -- Cas quand input n'est pas vide
    RETURN QUERY
        SELECT c.concept_id,
               c.external_id,
               v.vocabulary_id,
               v.base_uri,
               v.external_id,
               vt.vocabulary_type_id,
               vt.label,
               concept_autocomplete_get_alt_labels(c.concept_id, p_field_concept_id, p_langcode),
               concept_autocomplete_get_definition(c.concept_id, p_langcode),
               concept_autocomplete_get_hierarchy(c.concept_id, p_field_concept_id, p_langcode)
        FROM concept_label cl
                 JOIN concept c ON cl.fk_concept_id = c.concept_id
                 JOIN vocabulary v ON c.fk_vocabulary_id = v.vocabulary_id
                 JOIN vocabulary_type vt ON v.fk_type_id = vt.vocabulary_type_id
        WHERE cl.fk_field_parent_concept_id = p_field_concept_id
          AND cl.lang_code = p_langcode
          AND NOT c.is_deleted
          AND unaccent(cl.label) ILIKE unaccent('%' || p_input || '%')
        LIMIT p_limit;


END;
$$ LANGUAGE plpgsql;
