CREATE OR REPLACE FUNCTION identifier_nextval(
    p_action_unit_id BIGINT,
    p_form_config_id BIGINT,
    p_canonical_key TEXT,
    p_min_code INTEGER
) RETURNS INTEGER AS $$
DECLARE
    v_allocated INTEGER;
BEGIN
    INSERT INTO identifier_counter (
        fk_action_unit_id,
        fk_form_config_id,
        canonical_key,
        counter
    ) VALUES (
        p_action_unit_id,
        p_form_config_id,
        p_canonical_key,
        p_min_code + 1
    )
    ON CONFLICT (fk_action_unit_id, fk_form_config_id, canonical_key)
    DO UPDATE SET counter = GREATEST(identifier_counter.counter, p_min_code) + 1
    RETURNING counter - 1 INTO v_allocated;

    RETURN v_allocated;
END;
$$ LANGUAGE plpgsql;
