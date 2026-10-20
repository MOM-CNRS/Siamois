CREATE OR REPLACE FUNCTION institution_identifier_nextval(
    p_institution_id BIGINT,
    p_form_config_id BIGINT,
    p_canonical_key TEXT,
    p_min_code INTEGER
) RETURNS INTEGER AS $$
DECLARE
    v_allocated INTEGER;
BEGIN
    INSERT INTO institution_identifier_counter (
        fk_institution_id,
        fk_form_config_id,
        canonical_key,
        counter
    ) VALUES (
        p_institution_id,
        p_form_config_id,
        p_canonical_key,
        p_min_code + 1
    )
    ON CONFLICT (fk_institution_id, fk_form_config_id, canonical_key)
    DO UPDATE SET counter = GREATEST(institution_identifier_counter.counter, p_min_code) + 1
    RETURNING counter - 1 INTO v_allocated;

    RETURN v_allocated;
END;
$$ LANGUAGE plpgsql;
