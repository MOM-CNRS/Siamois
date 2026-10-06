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
from siamois_sdk.flatten import (
    ColumnSpec, Issue, Vocabularies, cells_to_create, cells_to_patch, find_by_binding, resolve_label, row_to_cells,
)
from .loader import (
    BINDING_RU, BINDING_TYPE, FID_FULL, FID_STATUS, IMMUTABLE_BINDINGS, KIND_FIND, KIND_PROJECT, KIND_RU, VOCAB_RU,
    entity_answers,
)


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
        row_specs = [s for s in specs if (not allowed or s.field_id in allowed) and s.binding not in IMMUTABLE_BINDINGS]
        skipped = [s for s in specs if s not in row_specs and s.binding not in IMMUTABLE_BINDINGS]
        for s in specs:
            if s.binding in IMMUTABLE_BINDINGS and str(b["cells"].get(s.name) or "") != str(cur["cells"].get(s.name) or ""):
                ch.issues.append(Issue(s.name, "non modifiable après la création : ignoré", False))
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
    elif kind == KIND_FIND:
        e = client.find(id_)
    else:
        e = client.project(id_, fields="all")
    return entity_to_row(kind, e, specs, vocabs)


def entity_to_row(kind: str, e: Any, specs: List[ColumnSpec], vocabs: Vocabularies) -> Dict[str, Any]:
    return {"cells": row_to_cells(specs, entity_answers(kind, e, specs), vocabs), "geom": e.geom,
            "revision": getattr(e, "sync_revision", None), "id": e.id}


# ------------------------------------------------------------------ création


@dataclass
class RowCreation:
    layer: str
    kind: str
    fid: int  # identifiant de l'entité dans la couche QGIS
    label: str
    type_id: Optional[str] = None
    parent_id: Optional[str] = None  # mobilier : UE
    answers: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    geom: Optional[Dict[str, Any]] = None
    issues: List[Issue] = field(default_factory=list)
    current_cells: Dict[str, Any] = field(default_factory=dict)
    current_wkt: Optional[str] = None

    @property
    def has_changes(self) -> bool:
        return True

    @property
    def blocking(self) -> bool:
        return any(i.blocking for i in self.issues)


@dataclass
class CreateResult(PushResult):
    entity: Any = None


def collect_creations(layer: str, kind: str, specs: List[ColumnSpec], new_rows: List[Dict[str, Any]],
                      vocabs: Vocabularies, type_allowed: Dict[str, Any], default_allowed: Any,
                      geom_editable: bool) -> List[RowCreation]:
    """Lignes sans identifiant SIAMOIS (créées dans QGIS) -> créations à envoyer.

    `new_rows` : {fid, cells, wkt, geojson}. Une ligne entièrement vide (ni cellule ni géométrie) est ignorée.
    """
    out: List[RowCreation] = []
    type_spec = find_by_binding(specs, BINDING_TYPE)
    parent_spec = find_by_binding(specs, BINDING_RU) if kind == KIND_FIND else None
    for r in new_rows:
        cells = {k: v for k, v in r["cells"].items() if v not in (None, "")}
        cells.pop("Statut", None)
        cells.pop("Identifiant complet", None)  # généré par le serveur
        if not cells and not r.get("geojson"):
            continue
        cr = RowCreation(layer, kind, r["fid"], f"nouvelle ligne #{r['fid']}", current_cells=r["cells"],
                         current_wkt=r.get("wkt"))
        # type (obligatoire)
        if type_spec is None:
            cr.issues.append(Issue("type", "le formulaire ne propose pas de champ « type »"))
        elif not cells.get(type_spec.name):
            cr.issues.append(Issue(type_spec.name, "type obligatoire pour créer un élément"))
        else:
            try:
                cr.type_id = resolve_label(type_spec, cells[type_spec.name], vocabs)
            except ValueError as exc:
                cr.issues.append(Issue(type_spec.name, str(exc)))
        # UE parente (mobilier)
        if kind == KIND_FIND:
            if parent_spec is None or not cells.get(parent_spec.name):
                cr.issues.append(Issue(parent_spec.name if parent_spec else "UE", "UE obligatoire pour créer un mobilier"))
            else:
                try:
                    cr.parent_id = resolve_label(parent_spec, cells[parent_spec.name], vocabs)
                except ValueError as exc:
                    cr.issues.append(Issue(parent_spec.name, str(exc)))
        allowed = set(default_allowed or ()) | set(type_allowed.get(cr.type_id or "", ())) if cr.type_id else None
        skip = {s.field_id for s in (type_spec, parent_spec) if s is not None}
        diff = cells_to_create(specs, r["cells"], vocabs, allowed=allowed, skip=skip)
        cr.answers = diff.answers
        cr.issues.extend(diff.issues)
        # géométrie
        if r.get("geojson"):
            if geom_editable:
                cr.geom = r["geojson"]
            else:
                cr.issues.append(Issue("géométrie", "la géométrie d'un mobilier ne peut pas être envoyée : ignorée", False))
        ident = next((str(v) for k, v in cells.items() if k not in (type_spec.name if type_spec else "",)), None)
        cr.label = f"nouvelle ligne #{r['fid']}" + (f" ({ident})" if ident else "")
        out.append(cr)
    return out


def push_create(client: SiamoisClient, cr: RowCreation, project_id: str) -> CreateResult:
    try:
        if cr.kind == KIND_RU:
            e = client.create_recording_unit(project_id, cr.type_id, answers=cr.answers or None, geom=cr.geom)
        elif cr.kind == KIND_FIND:
            e = client.create_find(cr.parent_id, cr.type_id, answers=cr.answers or None)
        else:
            return CreateResult(cr, "error", f"création non gérée pour : {cr.kind}")
        return CreateResult(cr, "ok", new_revision=getattr(e, "sync_revision", None), entity=e)
    except AuthenticationError as exc:
        return CreateResult(cr, "auth", exc.user_message)
    except SiamoisError as exc:
        return CreateResult(cr, "error", exc.user_message)
