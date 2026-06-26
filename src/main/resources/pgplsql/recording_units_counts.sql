DROP FUNCTION IF EXISTS recording_unit_counts;
DROP TYPE IF EXISTS recording_unit_counts;

CREATE TYPE recording_unit_counts AS
(
    related_specimen_count  BIGINT,
    ru_parents_count        BIGINT,
    ru_childrens_count      BIGINT,
    ru_relationships_count  BIGINT
);

CREATE OR REPLACE FUNCTION recording_unit_counts(
    p_ru_id recording_unit.recording_unit_id%type
) RETURNS TEXT AS
$$
DECLARE
    v_result recording_unit_counts;
BEGIN
    SELECT COUNT(*)
    INTO v_result.related_specimen_count
    FROM recording_unit ru
    JOIN specimen s ON ru.recording_unit_id = s.fk_recording_unit_id
    WHERE ru.recording_unit_id = p_ru_id;

    SELECT SUM(CASE WHEN ruh.fk_parent_id = p_ru_id THEN 1 ELSE 0 END), SUM(CASE WHEN ruh.fk_child_id = p_ru_id THEN 1 ELSE 0 END)
    INTO v_result.ru_parents_count, v_result.ru_childrens_count
    FROM recording_unit_hierarchy ruh
    WHERE ruh.fk_child_id = p_ru_id OR ruh.fk_parent_id = p_ru_id;

    SELECT COUNT(r)
    INTO v_result.ru_relationships_count
    FROM stratigraphic_relationship r
    WHERE r.fk_recording_unit_1_id = p_ru_id OR r.fk_recording_unit_2_id = p_ru_id;

    RETURN v_result;
end
$$ language plpgsql;