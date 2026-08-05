DROP FUNCTION IF EXISTS concept_autocomplete;
DROP FUNCTION IF EXISTS concept_autocomplete_related;
DROP FUNCTION IF EXISTS concept_autocomplete_branch;
DROP FUNCTION IF EXISTS concept_autocomplete_collection;
DROP FUNCTION IF EXISTS concept_autocomplete_search;
DROP FUNCTION IF EXISTS concept_branch_concept_ids;
DROP FUNCTION IF EXISTS concept_autocomplete_get_definition;
DROP FUNCTION IF EXISTS concept_autocomplete_get_hierarchy;
DROP FUNCTION IF EXISTS concept_parent_id_in_context;
DROP FUNCTION IF EXISTS concept_autocomplete_get_alt_labels;
DROP FUNCTION IF EXISTS concept_get_label;
DROP TYPE IF EXISTS concept_autocomplete_record;

CREATE TYPE concept_autocomplete_record AS
(
    concept_id                 BIGINT,
    concept_external_id        TEXT,

    parent_concept_id          BIGINT,
    parent_concept_external_id TEXT,

    vocabulary_id              BIGINT,
    vocabulary_base_uri        VARCHAR(255),
    vocabulary_external_id     VARCHAR(255),

    vocabulary_type_id         BIGINT,
    vocabulary_type_label      TEXT,

    concept_label_id           BIGINT,
    concept_label_label        citext,

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
      AND cl.fk_field_parent_concept_id IS NOT DISTINCT FROM p_field_concept_id
      AND cl.lang_code = p_langcode
      AND cl.label_type = 0
    LIMIT 1;

    IF v_pref_label IS NULL THEN
        SELECT cl.label || ' (' || cl.lang_code || ')'
        INTO v_pref_label
        FROM concept_label cl
        WHERE cl.fk_concept_id = p_concept_id
          AND cl.fk_field_parent_concept_id IS NOT DISTINCT FROM p_field_concept_id
          AND cl.label_type = 0
        LIMIT 1;
    END IF;

    IF v_pref_label IS NULL THEN
        SELECT cl.label || ' (' || cl.lang_code || ')'
        INTO v_pref_label
        FROM concept_label cl
        WHERE cl.fk_concept_id = p_concept_id
          AND cl.fk_field_parent_concept_id IS NOT DISTINCT FROM p_field_concept_id
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

    SELECT string_agg(cl.label, ';#')
    INTO v_alt_labels
    FROM concept_label cl
    WHERE cl.fk_concept_id = p_concept_id
      AND cl.fk_field_parent_concept_id IS NOT DISTINCT FROM p_field_concept_id
      AND cl.lang_code = p_langcode
      AND cl.label_type = 1;

    RETURN v_alt_labels;
END;
$$ LANGUAGE plpgsql;


-- Returns the definition of a concept in the specified language
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

-- Returns the parent of the concept in the given field context, NULL when it has none.
-- p_field_concept_id may be NULL, in which case the relations imported outside of any
-- field context (branch and collection configurations) are followed.
CREATE OR REPLACE FUNCTION concept_parent_id_in_context(
    p_concept_id BIGINT,
    p_field_concept_id BIGINT
)
    RETURNS BIGINT AS
$$
DECLARE
    v_parent_concept_id BIGINT;
BEGIN
    SELECT ch.fk_parent_concept_id
    INTO v_parent_concept_id
    FROM concept_hierarchy ch
    WHERE ch.fk_child_concept_id = p_concept_id
      AND ch.fk_parent_field_context_id IS NOT DISTINCT FROM p_field_concept_id
    LIMIT 1;

    RETURN v_parent_concept_id;
END;
$$ LANGUAGE plpgsql;

-- Returns the parents of the concept in a ' > ' separated string.
-- p_field_concept_id may be NULL, see concept_parent_id_in_context.
CREATE OR REPLACE FUNCTION concept_autocomplete_get_hierarchy(
    p_concept_id BIGINT,
    p_field_concept_id BIGINT,
    p_langcode VARCHAR(3)
)
    RETURNS TEXT AS
$$
DECLARE
    -- Guards against a cyclic hierarchy, which would otherwise loop forever
    c_max_depth CONSTANT INT := 100;
    v_parents             TEXT[];
    v_ancestor_concept_id BIGINT := concept_parent_id_in_context(p_concept_id, p_field_concept_id);
    v_depth               INT    := 0;
BEGIN
    WHILE v_ancestor_concept_id IS NOT NULL AND v_depth < c_max_depth
        LOOP
            v_parents := array_prepend(
                    concept_get_label(v_ancestor_concept_id, p_field_concept_id, p_langcode),
                    v_parents);

            v_ancestor_concept_id := concept_parent_id_in_context(v_ancestor_concept_id, p_field_concept_id);
            v_depth := v_depth + 1;
        END LOOP;

    RETURN array_to_string(v_parents, ' > ');
END;
$$ LANGUAGE plpgsql;

-- Returns the ids of every child and sub-child of the given concept, following the narrower
-- relations imported outside of any field context (branch configurations).
-- The top term itself is not part of the result.
CREATE OR REPLACE FUNCTION concept_branch_concept_ids(
    p_top_term_concept_id BIGINT
)
    RETURNS BIGINT[] AS
$$
DECLARE
    v_concept_ids BIGINT[];
BEGIN
    WITH RECURSIVE descendants AS (SELECT ch.fk_child_concept_id AS concept_id
                                   FROM concept_hierarchy ch
                                   WHERE ch.fk_parent_concept_id = p_top_term_concept_id
                                     AND ch.fk_parent_field_context_id IS NULL
                                   UNION -- UNION and not UNION ALL, so a cyclic hierarchy terminates
                                   SELECT ch.fk_child_concept_id
                                   FROM concept_hierarchy ch
                                            JOIN descendants d ON ch.fk_parent_concept_id = d.concept_id
                                   WHERE ch.fk_parent_field_context_id IS NULL)
    SELECT array_agg(d.concept_id)
    INTO v_concept_ids
    FROM descendants d;

    RETURN coalesce(v_concept_ids, ARRAY []::BIGINT[]);
END;
$$ LANGUAGE plpgsql;

-- Core autocomplete query, shared by every autocomplete entry point.
-- The candidates are restricted by the filters that are not NULL:
--   p_field_concept_id      : only the concepts imported in the context of that field concept
--   p_concept_ids           : only the concepts of that list (an empty list matches nothing)
--   p_related_of_concept_id : only the concepts related to that concept
CREATE OR REPLACE FUNCTION concept_autocomplete_search(
    p_field_concept_id BIGINT,
    p_concept_ids BIGINT[],
    p_related_of_concept_id BIGINT,
    p_langcode VARCHAR(3),
    p_input TEXT,
    p_limit INT
)
    RETURNS SETOF concept_autocomplete_record AS
$$
BEGIN
    -- The matching labels are selected and limited first so the aggregation functions below
    -- (alt labels, definition, hierarchy) are only evaluated for the rows actually returned.
    RETURN QUERY
        WITH matching_labels AS (SELECT cl.concept_label_id,
                                        cl.label,
                                        cl.fk_concept_id,
                                        cl.fk_field_parent_concept_id
                                 FROM concept_label cl
                                          JOIN concept c ON cl.fk_concept_id = c.concept_id
                                 WHERE cl.lang_code = p_langcode
                                   AND cl.label_type = 0
                                   AND NOT c.is_deleted
                                   AND (p_field_concept_id IS NULL OR
                                        cl.fk_field_parent_concept_id = p_field_concept_id)
                                   AND (p_concept_ids IS NULL OR c.concept_id = ANY (p_concept_ids))
                                   AND (p_related_of_concept_id IS NULL
                                            OR
                                        (p_related_of_concept_id IS NOT NULL AND EXISTS (SELECT 1
                                                                                         FROM concept_related cr
                                                                                         WHERE cr.fk_concept_id = p_related_of_concept_id
                                                                                           AND cr.fk_related_concept_id = c.concept_id)
                                                                                        )
                                       )
                                   AND (p_input IS NULL OR trim(p_input) = '' OR
                                        unaccent(cl.label) ILIKE unaccent('%' || p_input || '%'))
                                 ORDER BY cl.label -- Sort by label in alphabetical order
                                 LIMIT p_limit)
        SELECT c.concept_id,
               c.external_id,

               c2.concept_id,
               c2.external_id,

               v.vocabulary_id,
               v.base_uri,
               v.external_id,

               vt.vocabulary_type_id,
               vt.label,

               ml.concept_label_id,
               ml.label,

               concept_autocomplete_get_alt_labels(c.concept_id, ml.fk_field_parent_concept_id, p_langcode),
               concept_autocomplete_get_definition(c.concept_id, p_langcode),
               concept_autocomplete_get_hierarchy(c.concept_id, ml.fk_field_parent_concept_id, p_langcode)
        FROM matching_labels ml
                 JOIN concept c ON ml.fk_concept_id = c.concept_id
                 LEFT JOIN concept c2 ON c2.concept_id = ml.fk_field_parent_concept_id
                 JOIN vocabulary v ON c.fk_vocabulary_id = v.vocabulary_id
                 JOIN vocabulary_type vt ON v.fk_type_id = vt.vocabulary_type_id
        ORDER BY ml.label;
END;
$$ LANGUAGE plpgsql;

-- Autocomplete on every concept configured for the given field concept
CREATE OR REPLACE FUNCTION concept_autocomplete(
    p_field_concept_id BIGINT,
    p_langcode VARCHAR(3),
    p_input TEXT,
    p_limit INT
)
    RETURNS SETOF concept_autocomplete_record AS
$$
BEGIN
    RETURN QUERY SELECT *
                 FROM concept_autocomplete_search(p_field_concept_id, NULL::BIGINT[], NULL::BIGINT, p_langcode, p_input,
                                                  p_limit);
END;
$$ LANGUAGE plpgsql;

-- Autocomplete restricted to the concepts related to p_base_concept_id.
-- Used when a field value depends on the value selected in another field.
CREATE OR REPLACE FUNCTION concept_autocomplete_related(
    p_field_concept_id BIGINT,
    p_base_concept_id BIGINT,
    p_langcode VARCHAR(3),
    p_input TEXT,
    p_limit INT
)
    RETURNS SETOF concept_autocomplete_record AS
$$
BEGIN
    RETURN QUERY SELECT *
                 FROM concept_autocomplete_search(p_field_concept_id, NULL::BIGINT[], p_base_concept_id, p_langcode,
                                                  p_input, p_limit);
END;
$$ LANGUAGE plpgsql;

-- Autocomplete on every child and sub-child of the given top term.
-- Used by the form field configurations set up on a thesaurus branch.
CREATE OR REPLACE FUNCTION concept_autocomplete_branch(
    p_top_term_concept_id BIGINT,
    p_langcode VARCHAR(3),
    p_input TEXT,
    p_limit INT
)
    RETURNS SETOF concept_autocomplete_record AS
$$
BEGIN
    RETURN QUERY SELECT *
                 FROM concept_autocomplete_search(NULL::BIGINT, concept_branch_concept_ids(p_top_term_concept_id),
                                                  NULL::BIGINT, p_langcode, p_input, p_limit);
END;
$$ LANGUAGE plpgsql;

-- Autocomplete on every concept of the given collection.
-- Used by the form field configurations set up on a thesaurus collection.
CREATE OR REPLACE FUNCTION concept_autocomplete_collection(
    p_collection_id BIGINT,
    p_langcode VARCHAR(3),
    p_input TEXT,
    p_limit INT
)
    RETURNS SETOF concept_autocomplete_record AS
$$
DECLARE
    v_concept_ids BIGINT[];
BEGIN
    SELECT coalesce(array_agg(cc.fk_concept_id), ARRAY []::BIGINT[])
    INTO v_concept_ids
    FROM collection_concept cc
    WHERE cc.fk_collection_id = p_collection_id;

    RETURN QUERY SELECT *
                 FROM concept_autocomplete_search(NULL::BIGINT, v_concept_ids, NULL::BIGINT, p_langcode, p_input,
                                                  p_limit);
END;
$$ LANGUAGE plpgsql;
