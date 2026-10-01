#!/usr/bin/env python3
"""
Importe les géométries d'un GeoPackage (emprise prescrite / ouvertures / unités
d'observation) dans un projet SIAMOIS existant, via l'API REST /api/v1.

Aucune reprojection n'est effectuée : les coordonnées et le SRID natifs du
GeoPackage sont envoyés tels quels à l'API, qui les stocke sans les convertir.

Mapping des couches :
  - <prefix>_PRESCR  -> Projet (PATCH /api/v1/projects/{id})
  - <prefix>_OUVERT  -> Lieu (SpatialUnit.placeNumber) si trouvé dans le
                        périmètre du projet, sinon UE (RecordingUnit.identifier)
  - <prefix>_UNOBS   -> UE (RecordingUnit.identifier)

Usage:
    python3 import_geopackage_geometries.py FICHIER.gpkg PROJECT_ID \
        --base-url https://siamois.example.org --email a@b.fr --password ***

Dépendance externe unique : `requests` (pip install requests).
"""
from __future__ import annotations

import argparse
import getpass
import re
import sqlite3
import sys
from dataclasses import dataclass, field

import requests


# ---------------------------------------------------------------------------
# Lecture du GeoPackage : les 3 couches ont chacune une colonne `geom_wkt`
# déjà en WKT (pas besoin de décoder le binaire GPB), et leur SRID natif est
# déclaré dans gpkg_geometry_columns.
# ---------------------------------------------------------------------------

@dataclass
class Feature:
    identifier: str
    geom_geojson: dict


def read_srid(conn: sqlite3.Connection, table: str) -> int:
    row = conn.execute(
        "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name = ?", (table,)
    ).fetchone()
    if row is None:
        raise RuntimeError(f"Couche introuvable dans le GeoPackage : {table}")
    return row[0]


_NUM = r"-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?"
_POINT_RE = re.compile(rf"({_NUM})\s+({_NUM})")


def _parse_ring(text: str) -> list[list[float]]:
    return [[float(x), float(y)] for x, y in _POINT_RE.findall(text)]


def _split_top_level(text: str) -> list[str]:
    """Splits a comma-separated list of parenthesized groups at the top level only."""
    parts = []
    depth = 0
    start = 0
    for i, ch in enumerate(text):
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append(text[start:i])
            start = i + 1
    parts.append(text[start:])
    return [p.strip() for p in parts if p.strip()]


def wkt_multipolygon_to_geojson_coordinates(wkt: str) -> list:
    """Parses `MultiPolygon (((x y, ...), (...)), ((...)))` into GeoJSON coordinates."""
    body = wkt.strip()
    body = re.sub(r"^MultiPolygon\s*", "", body, flags=re.IGNORECASE).strip()
    if not (body.startswith("(") and body.endswith(")")):
        raise ValueError(f"WKT MultiPolygon inattendu : {wkt[:80]}...")
    body = body[1:-1]  # strip outer MultiPolygon parens

    polygons = []
    for polygon_text in _split_top_level(body):
        polygon_text = polygon_text.strip()
        if not (polygon_text.startswith("(") and polygon_text.endswith(")")):
            raise ValueError(f"Anneau de polygone inattendu : {polygon_text[:80]}...")
        polygon_text = polygon_text[1:-1]
        rings = [_parse_ring(r) for r in _split_top_level(polygon_text)]
        polygons.append(rings)
    return polygons


def read_layer(conn: sqlite3.Connection, table: str, identifier_col: str) -> tuple[int, list[Feature]]:
    """Reads a layer and merges all rows sharing the same identifier into a single
    MultiPolygon (a GeoPackage layer can hold several disjoint fragments for the
    same numouvert/numunobs, and RecordingUnit/SpatialUnit only store one geometry)."""
    srid = read_srid(conn, table)
    cur = conn.execute(f'SELECT "{identifier_col}", geom_wkt FROM "{table}"')
    polygons_by_identifier: dict[str, list] = {}
    for identifier, wkt in cur.fetchall():
        if wkt is None or identifier is None:
            continue
        polygons = wkt_multipolygon_to_geojson_coordinates(wkt)
        polygons_by_identifier.setdefault(str(identifier), []).extend(polygons)

    features = [
        Feature(
            identifier=identifier,
            geom_geojson={"type": "MultiPolygon", "coordinates": polygons, "srid": srid},
        )
        for identifier, polygons in polygons_by_identifier.items()
    ]
    return srid, features


# ---------------------------------------------------------------------------
# Client API SIAMOIS
# ---------------------------------------------------------------------------

class SiamoisClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()

    def login(self, email: str, password: str) -> None:
        resp = self.session.post(
            f"{self.base_url}/api/v1/auth/login",
            json={"email": email, "password": password},
            timeout=30,
        )
        resp.raise_for_status()
        token = resp.json()["accessToken"]
        self.session.headers["Authorization"] = f"Bearer {token}"

    def get_project(self, project_id: str) -> dict:
        resp = self.session.get(f"{self.base_url}/api/v1/projects/{project_id}", timeout=30)
        resp.raise_for_status()
        return resp.json()

    def list_all(self, path: str, params: dict | None = None) -> list[dict]:
        items = []
        offset = 0
        limit = 100
        while True:
            resp = self.session.get(
                f"{self.base_url}{path}",
                params={**(params or {}), "offset": offset, "limit": limit},
                timeout=30,
            )
            resp.raise_for_status()
            body = resp.json()
            page = body.get("data", [])
            items.extend(page)
            total = (body.get("meta") or {}).get("total", len(items))
            offset += limit
            if offset >= total or not page:
                break
        return items

    def patch_project_geom(self, project_id: str, geom: dict) -> None:
        resp = self.session.patch(
            f"{self.base_url}/api/v1/projects/{project_id}",
            json={"geom": geom},
            timeout=30,
        )
        resp.raise_for_status()

    def patch_place_geom(self, place_id: str, geom: dict) -> None:
        resp = self.session.patch(
            f"{self.base_url}/api/v1/places/{place_id}",
            json={"geom": geom},
            timeout=30,
        )
        resp.raise_for_status()

    def patch_recording_unit_geom(self, ru_id: str, geom: dict) -> None:
        resp = self.session.patch(
            f"{self.base_url}/api/v1/recording-units/{ru_id}",
            json={"geom": geom},
            timeout=30,
        )
        resp.raise_for_status()


# ---------------------------------------------------------------------------
# Logique d'import
# ---------------------------------------------------------------------------

@dataclass
class Summary:
    processed: int = 0
    matched: int = 0
    not_found: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


def build_place_number_map(client: SiamoisClient, project: dict) -> dict[int, str]:
    org = project.get("organization") or {}
    org_id = org.get("id")
    if org_id is None:
        return {}

    linked_ids = set()
    main_location = project.get("mainLocation")
    if main_location and main_location.get("id"):
        linked_ids.add(str(main_location["id"]))
    for su in project.get("spatialContext") or []:
        if su.get("id"):
            linked_ids.add(str(su["id"]))
    if not linked_ids:
        return {}

    places = client.list_all("/api/v1/places", {"organizationId": org_id})
    result = {}
    for place in places:
        if str(place.get("id")) in linked_ids and place.get("placeNumber") is not None:
            result[int(place["placeNumber"])] = str(place["id"])
    return result


def build_recording_unit_map(client: SiamoisClient, project_id: str) -> dict[str, str]:
    units = client.list_all(f"/api/v1/projects/{project_id}/recording-units")
    result = {}
    for unit in units:
        identifier = unit.get("identifier")
        if identifier is not None and unit.get("id") is not None:
            result[str(identifier).strip()] = str(unit["id"])
    return result


def import_prescr(client: SiamoisClient, project_id: str, features: list[Feature], summary: Summary) -> None:
    if not features:
        return
    if len(features) > 1:
        print(f"[PRESCR] {len(features)} lignes trouvées, une seule attendue : la première sera utilisée.",
              file=sys.stderr)
    feature = features[0]
    summary.processed += 1
    try:
        client.patch_project_geom(project_id, feature.geom_geojson)
        summary.matched += 1
        print(f"[PRESCR] Emprise du projet {project_id} mise à jour.")
    except requests.HTTPError as e:
        summary.errors.append(f"PRESCR -> projet {project_id}: {e}")


def import_ouvert(client: SiamoisClient, features: list[Feature],
                   place_by_number: dict[int, str], ru_by_identifier: dict[str, str],
                   summary: Summary) -> None:
    for feature in features:
        summary.processed += 1
        place_id = None
        try:
            place_id = place_by_number.get(int(feature.identifier))
        except ValueError:
            pass
        if place_id is not None:
            try:
                client.patch_place_geom(place_id, feature.geom_geojson)
                summary.matched += 1
                print(f"[OUVERT] numouvert={feature.identifier} -> lieu {place_id} mis à jour.")
            except requests.HTTPError as e:
                summary.errors.append(f"OUVERT numouvert={feature.identifier} -> lieu {place_id}: {e}")
            continue

        ru_id = ru_by_identifier.get(feature.identifier)
        if ru_id is not None:
            try:
                client.patch_recording_unit_geom(ru_id, feature.geom_geojson)
                summary.matched += 1
                print(f"[OUVERT] numouvert={feature.identifier} -> UE {ru_id} mise à jour.")
            except requests.HTTPError as e:
                summary.errors.append(f"OUVERT numouvert={feature.identifier} -> UE {ru_id}: {e}")
            continue

        summary.not_found.append(f"OUVERT numouvert={feature.identifier} (aucun lieu ni UE correspondant)")


def import_unobs(client: SiamoisClient, features: list[Feature],
                  ru_by_identifier: dict[str, str], summary: Summary) -> None:
    for feature in features:
        summary.processed += 1
        ru_id = ru_by_identifier.get(feature.identifier)
        if ru_id is None:
            summary.not_found.append(f"UNOBS numunobs={feature.identifier} (aucune UE correspondante)")
            continue
        try:
            client.patch_recording_unit_geom(ru_id, feature.geom_geojson)
            summary.matched += 1
            print(f"[UNOBS] numunobs={feature.identifier} -> UE {ru_id} mise à jour.")
        except requests.HTTPError as e:
            summary.errors.append(f"UNOBS numunobs={feature.identifier} -> UE {ru_id}: {e}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("gpkg", help="Chemin du fichier .gpkg")
    parser.add_argument("project_id", help="Identifiant du projet SIAMOIS (action_unit_id)")
    parser.add_argument("--base-url", required=True, help="URL de base de l'API, ex. https://siamois.example.org")
    parser.add_argument("--email", required=True, help="Email de connexion à l'API")
    parser.add_argument("--password", help="Mot de passe (sinon demandé de façon interactive)")
    parser.add_argument("--prefix", help="Préfixe des couches (déduit automatiquement si absent), "
                                          "ex. C309_01 pour C309_01_PRESCR/OUVERT/UNOBS")
    args = parser.parse_args()

    password = args.password or getpass.getpass("Mot de passe API : ")

    conn = sqlite3.connect(args.gpkg)
    tables = [r[0] for r in conn.execute(
        "SELECT table_name FROM gpkg_contents WHERE data_type = 'features'").fetchall()]

    def find_table(suffix: str) -> str:
        matches = [t for t in tables if t.upper().endswith(suffix)]
        if args.prefix:
            matches = [t for t in matches if t == f"{args.prefix}_{suffix}"]
        if not matches:
            raise RuntimeError(f"Aucune couche se terminant par _{suffix} trouvée parmi : {tables}")
        if len(matches) > 1:
            raise RuntimeError(f"Plusieurs couches _{suffix} trouvées, précisez --prefix : {matches}")
        return matches[0]

    prescr_table = find_table("PRESCR")
    ouvert_table = find_table("OUVERT")
    unobs_table = find_table("UNOBS")

    _, prescr_features = read_layer(conn, prescr_table, "numprescr")
    _, ouvert_features = read_layer(conn, ouvert_table, "numouvert")
    _, unobs_features = read_layer(conn, unobs_table, "numunobs")
    conn.close()

    client = SiamoisClient(args.base_url)
    client.login(args.email, password)

    project = client.get_project(args.project_id)
    place_by_number = build_place_number_map(client, project)
    ru_by_identifier = build_recording_unit_map(client, args.project_id)

    print(f"Projet {args.project_id} : {len(place_by_number)} lieux (placeNumber) et "
          f"{len(ru_by_identifier)} UE (identifier) dans le périmètre.")

    summary = Summary()
    import_prescr(client, args.project_id, prescr_features, summary)
    import_ouvert(client, ouvert_features, place_by_number, ru_by_identifier, summary)
    import_unobs(client, unobs_features, ru_by_identifier, summary)

    print("\n--- Résumé ---")
    print(f"Traités  : {summary.processed}")
    print(f"Matchés  : {summary.matched}")
    print(f"Non trouvés : {len(summary.not_found)}")
    for line in summary.not_found:
        print(f"  - {line}")
    print(f"Erreurs  : {len(summary.errors)}")
    for line in summary.errors:
        print(f"  - {line}")

    return 1 if summary.errors else 0


if __name__ == "__main__":
    sys.exit(main())
