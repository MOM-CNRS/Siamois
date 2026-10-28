--liquibase formatted sql
--changeset siamois:identifier_peekval runOnChange:true splitStatements:false

CREATE OR REPLACE FUNCTION identifier_peekval(
    p_action_unit_id BIGINT,
    p_form_config_id BIGINT,
    p_canonical_key TEXT,
    p_min_code INTEGER
) RETURNS INTEGER AS $$
DECLARE
    v_current INTEGER;
BEGIN
    SELECT counter INTO v_current
    FROM identifier_counter
    WHERE fk_action_unit_id = p_action_unit_id
      AND fk_form_config_id = p_form_config_id
      AND canonical_key = p_canonical_key;

    IF v_current IS NULL THEN
        RETURN p_min_code;
    END IF;

    RETURN v_current;
END;
$$ LANGUAGE plpgsql;
