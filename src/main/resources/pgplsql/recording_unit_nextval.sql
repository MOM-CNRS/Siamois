DROP FUNCTION IF EXISTS recording_unit_nextval;

CREATE OR REPLACE FUNCTION recording_unit_nextval (
    p_parent_recording_unit_id BIGINT,
    p_concept_type_id BIGINT
) RETURNS INT AS
$$
DECLARE v_old_value INT;
BEGIN
    
END
$$ language plpgsql;

CREATE OR REPLACE FUNCTION recording_unit_nextval_au (
    p_action_unit_id BIGINT,
    p_concept_type_id BIGINT
) RETURNS INT AS
$$
DECLARE v_old_value INT;
BEGIN
    SELECT recording_unit_next_code
    INTO v_old_value
    FROM action_unit
    WHERE action_unit_id = p_action_unit_id;

    UPDATE action_unit
    SET recording_unit_next_code = v_old_value + 1
    WHERE action_unit_id = p_action_unit_id;

    RETURN v_old_value;
END
$$ language plpgsql;

