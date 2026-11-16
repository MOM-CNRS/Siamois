--liquibase formatted sql
--changeset siamois:geojson_wgs84_or_null runOnChange:true splitStatements:false

-- The GeoJSON in WGS84 of a geometry written as text (WKT, or EWKT with a SRID=…; prefix, as the
-- import writes it), for the map view of an additional geometry field. NULL when the text is not a
-- geometry, has no SRID or a SRID PostGIS does not know: the row is then left off the map rather than
-- failing the whole query.
CREATE OR REPLACE FUNCTION geojson_wgs84_or_null(p_text TEXT) RETURNS TEXT AS $$
DECLARE
    v_geom geometry;
BEGIN
    IF p_text IS NULL OR btrim(p_text) = '' THEN
        RETURN NULL;
    END IF;
    v_geom := ST_GeomFromEWKT(btrim(p_text));
    IF NOT EXISTS (SELECT 1 FROM spatial_ref_sys WHERE srid = ST_SRID(v_geom)) THEN
        RETURN NULL;
    END IF;
    RETURN ST_AsGeoJSON(ST_Transform(v_geom, 4326));
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;
