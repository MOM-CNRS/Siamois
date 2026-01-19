DROP FUNCTION IF EXISTS recording_unit_nextval;
DROP FUNCTION IF EXISTS recording_unit_nextval_au;

CREATE OR REPLACE FUNCTION recording_unit_nextval(
    p_parent_recording_unit_id BIGINT,
    p_concept_type_id BIGINT
) RETURNS INT AS
$$
DECLARE
    v_old_value              INT;
    DECLARE v_action_unit_id BIGINT;
BEGIN
    SELECT c.counter
    INTO v_old_value
    FROM identifier_ru_counter c
    WHERE c.fk_recording_unit_id = p_parent_recording_unit_id;

    IF v_old_value IS NULL THEN
        SELECT r.fk_action_unit_id
        INTO v_action_unit_id
        FROM recording_unit r
        WHERE recording_unit_id = p_parent_recording_unit_id;

        IF v_action_unit_id IS NULL THEN
            v_old_value := 1;
        ELSE
            SELECT a.min_recording_unit_code
            INTO v_old_value
            FROM action_unit a
            WHERE a.action_unit_id = v_action_unit_id;
        end if;

        IF v_old_value IS NULL THEN
            v_old_value := 1;
        end if;

        INSERT INTO identifier_ru_counter(counter, fk_recording_unit_id, fk_concept_type_id,
                                          fk_action_unit_id)
        VALUES (v_old_value + 1,
                p_parent_recording_unit_id,
                p_concept_type_id,
                v_action_unit_id);
    ELSE
        UPDATE identifier_ru_counter
        SET counter = v_old_value + 1
        WHERE fk_recording_unit_id = p_parent_recording_unit_id
          AND fk_concept_type_id = p_concept_type_id;
    END IF;

    RETURN v_old_value;
END
$$ language plpgsql;

CREATE OR REPLACE FUNCTION recording_unit_nextval_au(
    p_action_unit_id BIGINT,
    p_concept_type_id BIGINT
) RETURNS INT AS
$$
DECLARE
    v_old_value INT;
BEGIN
    SELECT c.counter
    INTO v_old_value
    FROM identifier_ru_counter c
    WHERE fk_action_unit_id = p_action_unit_id
      AND c.fk_recording_unit_id IS NULL
      AND c.fk_concept_type_id = p_concept_type_id;

    IF v_old_value IS NULL THEN
        SELECT a.min_recording_unit_code
        INTO v_old_value
        FROM action_unit a
        WHERE a.action_unit_id = p_action_unit_id;

        IF v_old_value IS NULL THEN
            v_old_value := 1;
        end if;

        INSERT INTO identifier_ru_counter(counter, fk_recording_unit_id, fk_concept_type_id,
                                          fk_action_unit_id)
        VALUES (v_old_value + 1,
                NULL,
                p_concept_type_id,
                p_action_unit_id);
    ELSE
        UPDATE identifier_ru_counter
        SET counter = v_old_value + 1
        WHERE fk_action_unit_id = p_action_unit_id
          AND fk_recording_unit_id IS NULL
          AND fk_concept_type_id = p_concept_type_id;
    end if;

    return v_old_value;
END
$$ language plpgsql;

