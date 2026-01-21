DROP FUNCTION IF EXISTS ru_nextval_unique;
DROP FUNCTION IF EXISTS ru_nextval_parent;
DROP FUNCTION IF EXISTS ru_nextval_type_unique;
DROP FUNCTION IF EXISTS ru_nextval_type_parent;

CREATE OR REPLACE FUNCTION ru_nextval_unique(
    p_action_unit_id BIGINT
) RETURNS INT AS
$$
DECLARE
    v_counter_id identifier_ru_counter.ru_counter_id%type;
    v_old_value INT;
BEGIN
    SELECT c.ru_counter_id ,c.counter
    INTO v_counter_id, v_old_value
    FROM identifier_ru_counter c
    WHERE c.fk_action_unit_id = p_action_unit_id
    AND c.fk_recording_unit_id IS NULL
    AND c.fk_concept_type_id IS NULL;

    IF v_old_value IS NULL THEN

        SELECT au.min_recording_unit_code
        INTO v_old_value
        FROM action_unit au
        WHERE au.action_unit_id = p_action_unit_id;

        INSERT INTO identifier_ru_counter (fk_action_unit_id, fk_recording_unit_id, fk_concept_type_id, counter)
        VALUES (p_action_unit_id, NULL, NULL, v_old_value + 1);
    ELSE
        UPDATE identifier_ru_counter
        SET counter = counter + 1
        WHERE ru_counter_id = v_counter_id;
    END IF;

    RETURN v_old_value;
END
$$ language plpgsql;

CREATE OR REPLACE FUNCTION ru_nextval_parent(
    p_parent_ru_id BIGINT
) RETURNS INT AS
$$
DECLARE
    v_counter_id identifier_ru_counter.ru_counter_id%type;
    v_action_unit_id identifier_ru_counter.fk_action_unit_id%type;
    v_old_value INT;
BEGIN
    SELECT c.ru_counter_id ,c.counter
    INTO v_counter_id, v_old_value
    FROM identifier_ru_counter c
    WHERE c.fk_recording_unit_id = p_parent_ru_id
    AND c.fk_action_unit_id IS NULL
    AND c.fk_concept_type_id IS NULL;

    IF v_old_value IS NULL THEN

        SELECT ru.fk_action_unit_id
        INTO v_action_unit_id
        FROM recording_unit ru
        WHERE ru.recording_unit_id = p_parent_ru_id;

        SELECT au.min_recording_unit_code
        INTO v_old_value
        FROM action_unit au
        WHERE au.action_unit_id = v_action_unit_id;

        INSERT INTO identifier_ru_counter(counter, fk_action_unit_id, fk_recording_unit_id, fk_concept_type_id)
        VALUES (v_old_value + 1, NULL, p_parent_ru_id, NULL);
    ELSE
        UPDATE identifier_ru_counter
        SET counter = counter + 1
        WHERE ru_counter_id = v_counter_id;
    end if;

    RETURN v_old_value;
END
$$ language plpgsql;

CREATE OR REPLACE FUNCTION ru_nextval_type_unique(
    p_action_unit_id BIGINT,
    p_type_concept_id BIGINT
) RETURNS INT AS
$$
DECLARE
    v_counter_id identifier_ru_counter.ru_counter_id%type;
    v_old_value INT;
BEGIN
    SELECT c.ru_counter_id ,c.counter
    INTO v_counter_id, v_old_value
    FROM identifier_ru_counter c
    WHERE c.fk_action_unit_id = p_action_unit_id
    AND c.fk_concept_type_id = p_type_concept_id
    AND c.fk_recording_unit_id IS NULL;

    IF v_old_value IS NULL THEN

        SELECT au.min_recording_unit_code
        INTO v_old_value
        FROM action_unit au
        WHERE au.action_unit_id = p_action_unit_id;

        INSERT INTO identifier_ru_counter(counter, fk_action_unit_id, fk_recording_unit_id, fk_concept_type_id)
        VALUES (v_old_value + 1, p_action_unit_id, NULL, p_type_concept_id);
    ELSE
        UPDATE identifier_ru_counter
        SET counter = counter + 1
        WHERE ru_counter_id = v_counter_id;
    end if;

    RETURN v_old_value;
END
$$ language plpgsql;

CREATE OR REPLACE FUNCTION ru_nextval_type_parent(
    p_parent_ru_id BIGINT,
    p_type_concept_id BIGINT
) RETURNS INT AS
$$
DECLARE
    v_counter_id identifier_ru_counter.ru_counter_id%type;
    v_action_unit_id action_unit.action_unit_id%type;
    v_old_value INT;
BEGIN
    SELECT c.ru_counter_id ,c.counter
    INTO v_counter_id, v_old_value
    FROM identifier_ru_counter c
    WHERE c.fk_recording_unit_id = p_parent_ru_id
    AND c.fk_concept_type_id = p_type_concept_id
    AND c.fk_action_unit_id IS NULL;

    IF v_old_value IS NULL THEN

        SELECT ru.fk_action_unit_id
        INTO v_action_unit_id
        FROM recording_unit ru
        WHERE ru.recording_unit_id = p_parent_ru_id;

        SELECT au.min_recording_unit_code
        INTO v_old_value
        FROM action_unit au
        WHERE au.action_unit_id = v_action_unit_id;

        INSERT INTO identifier_ru_counter(counter, fk_action_unit_id, fk_recording_unit_id, fk_concept_type_id)
        VALUES (v_old_value + 1, NULL, p_parent_ru_id, p_type_concept_id);
    ELSE
        UPDATE identifier_ru_counter
        SET counter = counter + 1
        WHERE ru_counter_id = v_counter_id;
    end if;

    RETURN v_old_value;
END
$$ language plpgsql;

