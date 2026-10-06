"""Fichier annexe SQLite (`<projet>.siamois.db`) à côté du GeoPackage.

Contient ce que le GeoPackage ne doit pas montrer à l'utilisateur : la copie de référence de chaque ligne
(pour ne renvoyer que ce qui a changé), le schéma des colonnes et les vocabulaires.
"""

from __future__ import annotations

import json
import sqlite3
from dataclasses import asdict
from typing import Any, Dict, List, Optional

from . import _sdk_import  # noqa: F401
from siamois_sdk.flatten import ColumnSpec, Vocabulary

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE IF NOT EXISTS layers(name TEXT PRIMARY KEY, kind TEXT, srid INTEGER, columns TEXT, extra TEXT);
CREATE TABLE IF NOT EXISTS vocab(code TEXT PRIMARY KEY, concepts TEXT);
CREATE TABLE IF NOT EXISTS base(layer TEXT, id TEXT, cells TEXT, geom_wkt TEXT, revision INTEGER,
                                incomplete TEXT, allowed TEXT, dirty_server INTEGER DEFAULT 0,
                                PRIMARY KEY(layer, id));
"""


class Store:
    def __init__(self, path: str):
        self.path = path
        self.db = sqlite3.connect(path)
        self.db.executescript(SCHEMA)

    def close(self) -> None:
        self.db.close()

    # meta
    def set_meta(self, key: str, value: Any) -> None:
        self.db.execute("INSERT OR REPLACE INTO meta VALUES(?,?)", (key, json.dumps(value)))
        self.db.commit()

    def get_meta(self, key: str, default: Any = None) -> Any:
        r = self.db.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
        return json.loads(r[0]) if r else default

    # layers
    def save_layer(self, name: str, kind: str, srid: int, columns: List[ColumnSpec],
                   type_allowed: Optional[Dict[str, Any]] = None, default_allowed: Optional[Any] = None) -> None:
        extra = {"type_allowed": {t: sorted(a) for t, a in (type_allowed or {}).items()},
                 "default_allowed": sorted(default_allowed or [])}
        self.db.execute("INSERT OR REPLACE INTO layers VALUES(?,?,?,?,?)",
                        (name, kind, srid, json.dumps([asdict(c) for c in columns]), json.dumps(extra)))
        self.db.commit()

    def layer(self, name: str) -> Optional[Dict[str, Any]]:
        r = self.db.execute("SELECT kind, srid, columns, extra FROM layers WHERE name=?", (name,)).fetchone()
        if not r:
            return None
        extra = json.loads(r[3]) if r[3] else {}
        return {"kind": r[0], "srid": r[1], "columns": [ColumnSpec(**c) for c in json.loads(r[2])],
                "type_allowed": {t: set(a) for t, a in (extra.get("type_allowed") or {}).items()},
                "default_allowed": set(extra.get("default_allowed") or [])}

    # vocabularies
    def save_vocab(self, code: str, concepts: List[Dict[str, Any]]) -> None:
        self.db.execute("INSERT OR REPLACE INTO vocab VALUES(?,?)", (code, json.dumps(concepts)))
        self.db.commit()

    def vocabularies(self) -> Dict[str, Vocabulary]:
        return {code: Vocabulary(json.loads(c)) for code, c in self.db.execute("SELECT code, concepts FROM vocab")}

    # base rows
    def save_base(self, layer: str, rows: List[Dict[str, Any]]) -> None:
        """rows : {id, cells, geom_wkt, revision, incomplete, allowed}."""
        self.db.execute("DELETE FROM base WHERE layer=?", (layer,))
        self.db.executemany(
            "INSERT INTO base(layer, id, cells, geom_wkt, revision, incomplete, allowed) VALUES(?,?,?,?,?,?,?)",
            [(layer, str(r["id"]), json.dumps(r["cells"]), r.get("geom_wkt"), r.get("revision"),
              json.dumps(r.get("incomplete") or []), json.dumps(sorted(r.get("allowed") or []))) for r in rows])
        self.db.commit()

    def base(self, layer: str) -> Dict[str, Dict[str, Any]]:
        out = {}
        for id_, cells, wkt, rev, inc, allowed in self.db.execute(
                "SELECT id, cells, geom_wkt, revision, incomplete, allowed FROM base WHERE layer=?", (layer,)):
            out[id_] = {"cells": json.loads(cells), "geom_wkt": wkt, "revision": rev,
                        "incomplete": json.loads(inc), "allowed": set(json.loads(allowed))}
        return out

    def add_base(self, layer: str, row: Dict[str, Any]) -> None:
        """Ajoute (ou remplace) la copie de référence d'une ligne venant d'être créée côté serveur."""
        self.db.execute(
            "INSERT OR REPLACE INTO base(layer, id, cells, geom_wkt, revision, incomplete, allowed) VALUES(?,?,?,?,?,?,?)",
            (layer, str(row["id"]), json.dumps(row["cells"]), row.get("geom_wkt"), row.get("revision"),
             json.dumps(row.get("incomplete") or []), json.dumps(sorted(row.get("allowed") or []))))
        self.db.commit()

    def update_base(self, layer: str, id_: str, cells: Dict[str, Any], geom_wkt: Optional[str],
                    revision: Optional[int]) -> None:
        self.db.execute("UPDATE base SET cells=?, geom_wkt=?, revision=? WHERE layer=? AND id=?",
                        (json.dumps(cells), geom_wkt, revision, layer, str(id_)))
        self.db.commit()
