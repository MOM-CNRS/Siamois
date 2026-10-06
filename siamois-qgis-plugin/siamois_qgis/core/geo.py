"""GeoJSON (API) <-> WKT, sans dépendance QGIS."""

from __future__ import annotations

from typing import Any, Dict, Optional, Tuple


def _pt(c) -> str:
    return " ".join(repr(float(x)) if not float(x).is_integer() else str(int(x)) for x in c[:3 if len(c) > 2 else 2])


def _ring(coords) -> str:
    return "(" + ", ".join(_pt(c) for c in coords) + ")"


def geojson_to_wkt(g: Optional[Dict[str, Any]]) -> Optional[str]:
    if not g or not g.get("type"):
        return None
    t, c = g["type"], g.get("coordinates")
    if c is None:
        return None
    z = " Z" if _has_z(c) else ""
    if t == "Point":
        return f"POINT{z} ({_pt(c)})"
    if t == "MultiPoint":
        return f"MULTIPOINT{z} (" + ", ".join(f"({_pt(p)})" for p in c) + ")"
    if t == "LineString":
        return f"LINESTRING{z} {_ring(c)}"
    if t == "MultiLineString":
        return f"MULTILINESTRING{z} (" + ", ".join(_ring(l) for l in c) + ")"
    if t == "Polygon":
        return f"POLYGON{z} (" + ", ".join(_ring(r) for r in c) + ")"
    if t == "MultiPolygon":
        return f"MULTIPOLYGON{z} (" + ", ".join("(" + ", ".join(_ring(r) for r in p) + ")" for p in c) + ")"
    raise ValueError(f"Type de géométrie non géré : {t}")


def _has_z(c) -> bool:
    while isinstance(c, (list, tuple)) and c and isinstance(c[0], (list, tuple)):
        c = c[0]
    return isinstance(c, (list, tuple)) and len(c) > 2


def srid_of(g: Optional[Dict[str, Any]], default: int = 4326) -> int:
    try:
        return int((g or {}).get("srid") or default)
    except (TypeError, ValueError):
        return default


def with_srid(geojson: Dict[str, Any], srid: int) -> Dict[str, Any]:
    return {"type": geojson["type"], "srid": int(srid), "coordinates": geojson["coordinates"]}


def dominant_srid(geoms, default: int = 4326) -> int:
    counts: Dict[int, int] = {}
    for g in geoms:
        if g:
            s = srid_of(g, default)
            counts[s] = counts.get(s, 0) + 1
    return max(counts, key=counts.get) if counts else default
