--liquibase formatted sql
--changeset siamois:relation_link_delete_on_entity_delete runOnChange:true splitStatements:false

-- Removes the links of a deleted record (docs/modele-relations.md, §3.5): relation_link designates each end by its
-- kind and id, without a foreign key, so this trigger keeps it from pointing to a record that no longer exists.
-- Set AFTER DELETE FOR EACH ROW on each business table by the changelog 2026-11-26-1, with two arguments: the kind
-- the links store for that table (RelationEndKind) and the name of its id column.
-- These deletions bypass Envers: they leave no revision, as the native deletes of a project already do.
CREATE OR REPLACE FUNCTION public.relation_link_delete_on_entity_delete()
    RETURNS TRIGGER AS $$
DECLARE
    deleted_kind text   := TG_ARGV[0];
    deleted_id   bigint := (to_jsonb(OLD) ->> TG_ARGV[1])::bigint;
BEGIN
    DELETE FROM public.relation_link
    WHERE (source_kind = deleted_kind AND source_id = deleted_id)
       OR (target_kind = deleted_kind AND target_id = deleted_id);
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;
