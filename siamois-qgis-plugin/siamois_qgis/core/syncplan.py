"""Détection des modifications et envoi vers l'API (sans QGIS).

Modèle « import xlsx » : les cellules actuelles de la couche sont comparées à la copie de référence
(table `base` du fichier annexe) ; seules les cellules modifiées sont résolues (libellé -> identifiant)
puis envoyées. Rapport d'erreurs ligne par ligne.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Iterable, List, Optional

from . import _sdk_import  # noqa: F401
from siamois_sdk import AuthenticationError, ConflictError, SiamoisClient, SiamoisError
from siamois_sdk.flatten import ColumnSpec, Issue, Vocabularies, cells_to_patch, row_to_cells
from .loader import FID_FULL, FID_STATUS, KIND_FIND, KIND_PROJECT, KIND_RU, with_fixed


@dataclass
class RowChange:
    layer: str
    kind: str
    id: str
    label: str
    answers: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    validated: Optional[str] = None
    geom_changed: bool = False
    geom: Optional[Dict[str, Any]] = None
    issues: List[Issue] = field(default_factory=list)
    expected_revision: Optional[int] = None
    current_cells: Dict[str, Any] = field(default_factory=dict)
    current_wkt: Optional[str] = None

    @property
    def has_changes(self) -> bool:
        return bool(self.answers) or self.validated is not None or self.geom_changed

    @property
    def blocking(self) -> bool:
        return any(i.blocking for i in self.issues)


@dataclass
class PushResult:
    change: RowChange
    status: str  # "ok" | "conflict" | "auth" | "error"
    message: str = ""
    new_revision: Optional[int] = None
    conflict: Optional[ConflictError] = None


def collect_changes(layer: str, kind: str, specs: List[ColumnSpec], base: Dict[str, Dict[str, Any]],
                    current: Dict[str, Dict[str, Any]], vocabs: Vocabularies, geom_editable: bool) -> List[RowChange]:
    """`current` : id -> {"cells": {...}, "wkt": str|None, "geojson": dict|None}.

    Les lignes sans identifiant SIAMOIS (créées dans QGIS) ne sont pas gérées en v1 : signalées par l'appelant.
    """
    out: List[RowChange] = []
    for id_, b in base.items():
        cur = current.get(id_)
        if cur is None:
            continue  # ligne supprimée dans QGIS : non gérée en v1
        label = str(cur["cells"].get("Identifiant complet") or b["cells"].get("Identifiant complet") or id_)
        ch = RowChange(layer, kind, id_, label, expected_revision=b.get("revision"),
                       current_cells=cur["cells"], current_wkt=cur.get("wkt"))
        allowed = b.get("allowed") or set()
        row_specs = [s for s in specs if not allowed or s.field_id in allowed]
        skipped = [s for s in specs if s not in row_specs]
        diff = cells_to_patch(row_specs, b["cells"], cur["cells"], vocabs, incomplete=b.get("incomplete") or [])
        ch.issues.extend(diff.issues)
        for s in skipped:
            if str(b["cells"].get(s.name) or "") != str(cur["cells"].get(s.name) or ""):
                ch.issues.append(Issue(s.name, "champ absent du formulaire de ce type : modification ignorée", False))
        answers = dict(diff.answers)
        status = answers.pop(FID_STATUS, None)
        if status is not None:
            ch.validated = status.get("value")
            if ch.validated is None:  # statut vidé : non autorisé
                ch.validated = None
                ch.issues.append(Issue("Statut", "le statut ne peut pas être vidé"))
        ch.answers = answers
        if (cur.get("wkt") or None) != (b.get("geom_wkt") or None):
            if geom_editable:
                ch.geom_changed, ch.geom = True, cur.get("geojson")
            else:
                ch.issues.append(Issue("géométrie", "la géométrie de cette couche est en lecture seule : ignorée", False))
        out.append(ch)
    return [c for c in out if c.has_changes or c.issues]


def push(client: SiamoisClient, change: RowChange, *, force_revision: Optional[int] = None,
         use_revision: bool = True) -> PushResult:
    """Envoie une ligne. `force_revision` : écrase la version serveur (résolution « garder local »)."""
    expected = force_revision if force_revision is not None else (change.expected_revision if use_revision else None)
    try:
        if change.kind == KIND_RU:
            res = client.update_recording_unit(
                change.id, answers=change.answers or None,
                geom=change.geom if change.geom_changed else ..., validated=change.validated,
                expected_revision=expected)
            return PushResult(change, "ok", new_revision=res.sync_revision)
        if change.kind == KIND_FIND:
            client.update_find(change.id, answers=change.answers or None, validated=change.validated)
            return PushResult(change, "ok")
        if change.kind == KIND_PROJECT:
            payload: Dict[str, Any] = {}
            if change.answers:
                payload["answers"] = change.answers
            if change.validated is not None:
                payload["validated"] = change.validated
            if change.geom_changed:
                payload["geom"] = change.geom
            client.update_project(change.id, **payload)
            return PushResult(change, "ok")
        return PushResult(change, "error", f"type inconnu : {change.kind}")
    except ConflictError as exc:
        if exc.is_revision_conflict:
            return PushResult(change, "conflict", exc.user_message, conflict=exc)
        return PushResult(change, "error", exc.user_message)
    except AuthenticationError as exc:
        return PushResult(change, "auth", exc.user_message)
    except SiamoisError as exc:
        return PushResult(change, "error", exc.user_message)


def fetch_server_row(client: SiamoisClient, kind: str, id_: str, specs: List[ColumnSpec],
                     vocabs: Vocabularies) -> Dict[str, Any]:
    """Cellules de la version serveur (résolution « garder le serveur »). Retourne {cells, geom, revision}."""
    if kind == KIND_RU:
        e = client.recording_unit(id_)
        rev = e.sync_revision
    elif kind == KIND_FIND:
        e, rev = client.find(id_), None
    else:
        e, rev = client.project(id_, fields="all"), None
    return {"cells": row_to_cells(specs, with_fixed(e.answers, e.full_identifier, e.validated), vocabs),
            "geom": e.geom, "revision": rev}
