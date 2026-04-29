CREATE OR REPLACE FUNCTION mark_au_as_not_leaf()
    RETURNS TRIGGER AS $$
BEGIN

    UPDATE action_unit
    SET has_childrens = TRUE
    WHERE action_unit_id = NEW.fk_parent_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_after_insert_au_hierarchy
    AFTER INSERT ON public.action_hierarchy
    FOR EACH ROW
EXECUTE FUNCTION mark_au_as_not_leaf();