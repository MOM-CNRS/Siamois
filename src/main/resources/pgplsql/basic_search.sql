-- The former flat header search (basic_search and its per-entity helpers) is replaced by
-- global_search.sql. Dropped here so databases that still have it do not keep dead functions.
DROP FUNCTION IF EXISTS basic_search;
DROP FUNCTION IF EXISTS base_search_action_unit;
DROP FUNCTION IF EXISTS base_search_spatial_unit;
DROP FUNCTION IF EXISTS base_search_recording_unit;
DROP FUNCTION IF EXISTS base_search_specimen;
DROP TYPE IF EXISTS basic_search_result;
