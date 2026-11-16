import type {MessageKey} from "../i18n";

/** How a fiche's chip names a geometry type: its label, its icon, and whether it is points or areas. */
export interface GeometryKind {
  label: MessageKey;
  icon: string;
  shape: "point" | "line" | "area";
}

const POINT_ICON = "bi bi-geo-alt";
const LINE_ICON = "bi bi-bezier2";
const SHAPE_ICON = "bi bi-pentagon";

const KINDS: Record<string, GeometryKind> = {
  POINT: { label: "map.kindPoint", icon: POINT_ICON, shape: "point" },
  MULTIPOINT: { label: "map.kindMultiPoint", icon: POINT_ICON, shape: "point" },
  LINESTRING: { label: "map.kindLineString", icon: LINE_ICON, shape: "line" },
  MULTILINESTRING: { label: "map.kindMultiLineString", icon: LINE_ICON, shape: "line" },
  POLYGON: { label: "map.kindPolygon", icon: SHAPE_ICON, shape: "area" },
  MULTIPOLYGON: { label: "map.kindMultiPolygon", icon: SHAPE_ICON, shape: "area" },
  GEOMETRYCOLLECTION: { label: "map.kindCollection", icon: SHAPE_ICON, shape: "area" },
};

const UNKNOWN_KIND: GeometryKind = { label: "map.kindGeometry", icon: SHAPE_ICON, shape: "area" };

/** The kind of a WKT's geometry (an EWKT `SRID=…;` prefix is skipped); a generic one when unknown. */
export function geometryKind(wkt: string): GeometryKind {
  const type = /^\s*(?:SRID=\d+;)?\s*([A-Za-z]+)/.exec(wkt)?.[1]?.toUpperCase();
  return (type && KINDS[type]) || UNKNOWN_KIND;
}
