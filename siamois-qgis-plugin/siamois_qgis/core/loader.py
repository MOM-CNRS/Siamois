"""Chargement d'un projet depuis l'API vers des structures à plat (sans QGIS)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Optional, Set

from . import _sdk_import  # noqa: F401
from siamois_sdk import SiamoisClient
from siamois_sdk.flatten import (
    CHOICE, TEXT, ColumnSpec, Vocabulary, Vocabularies, flatten_schema, incomplete_columns, merge_schemas,
    row_to_cells,
)
from .geo import dominant_srid, srid_of

FID_FULL = "__fullIdentifier"
FID_STATUS = "__validated"
VOCAB_STATUS = "__validated"
VOCAB_RU = "__ru"  # vocabulaire synthétique : UE du projet (id -> identifiant complet)
BINDING_TYPE = "type"
BINDING_RU = "recordingUnit"
IMMUTABLE_BINDINGS = (BINDING_TYPE, BINDING_RU)  # non modifiables après création

STATUS_LABELS = {"INCOMPLETE": "En cours", "COMPLETE": "Terminé", "CANCELLED": "Annulé", "VALIDATED": "Validé"}
EDITABLE_STATUSES = ("INCOMPLETE", "COMPLETE", "CANCELLED")  # VALIDATED exige un droit validateur : lecture seule

KIND_PROJECT, KIND_RU, KIND_FIND = "project", "recording_unit", "find"
LAYER_NAMES = {KIND_PROJECT: "projet", KIND_RU: "unites_enregistrement", KIND_FIND: "mobilier"}
LAYER_TITLES = {KIND_PROJECT: "SIAMOIS – Projet", KIND_RU: "SIAMOIS – Unités d'enregistrement", KIND_FIND: "SIAMOIS – Mobilier"}


class Cancelled(Exception):
    pass


Progress = Callable[[str, int, int], bool]


@dataclass
class RowData:
    id: str
    cells: Dict[str, Any]
    geom: Optional[Dict[str, Any]] = None
    revision: Optional[int] = None
    incomplete: List[str] = field(default_factory=list)
    allowed: Set[str] = field(default_factory=set)  # field_id éditables pour le type de la ligne


@dataclass
class LayerData:
    kind: str
    name: str
    title: str
    specs: List[ColumnSpec]
    rows: List[RowData]
    srid: int = 4326
    geom_editable: bool = True
    type_allowed: Dict[str, Set[str]] = field(default_factory=dict)  # type_id -> field_id du formulaire du type
    default_allowed: Set[str] = field(default_factory=set)

    @property
    def has_geometry(self) -> bool:
        return any(r.geom for r in self.rows)


@dataclass
class ProjectBundle:
    org_id: str
    project_id: str
    project_name: str
    layers: List[LayerData]
    vocab_concepts: Dict[str, List[Dict[str, Any]]]
    base_url: str = ""


def status_vocabulary() -> Vocabulary:
    return Vocabulary({"id": k, "resolvedLabel": v} for k, v in STATUS_LABELS.items() if k in EDITABLE_STATUSES)


def fixed_specs() -> List[ColumnSpec]:
    return [
        ColumnSpec("Identifiant complet", FID_FULL, TEXT, "TEXT", editable=False),
        ColumnSpec("Statut", FID_STATUS, CHOICE, "SELECT_ONE_FROM_FIELD_CODE", vocab_code=VOCAB_STATUS),
    ]


def with_fixed(answers: Dict[str, Any], full_identifier: Optional[str], validated: Optional[str]) -> Dict[str, Any]:
    out = dict(answers or {})
    out[FID_FULL] = full_identifier
    out[FID_STATUS] = {"resourceId": validated, "label": STATUS_LABELS.get(validated or "", validated)} if validated else None
    return out


def ru_vocabulary(concepts) -> Vocabulary:
    return Vocabulary(concepts)


def entity_answers(kind: str, entity: Any, specs: List[ColumnSpec]) -> Dict[str, Any]:
    """Réponses « à plat » d'une entité API (colonnes techniques + champ UE du mobilier incluses)."""
    answers = with_fixed(entity.answers, entity.full_identifier, entity.validated)
    if kind == KIND_FIND and getattr(entity, "recording_unit_id", None):
        for s in specs:
            if s.binding == BINDING_RU:
                answers[s.field_id] = {"resourceId": entity.recording_unit_id}
    return answers


def _step(progress: Optional[Progress], label: str, done: int = 0, total: int = 0) -> None:
    if progress is not None and progress(label, done, total) is False:
        raise Cancelled()


def _vocab_codes(layers: List[LayerData]) -> Set[str]:
    return {s.vocab_code for l in layers for s in l.specs if s.vocab_code and s.vocab_code != VOCAB_STATUS}


def load_project(client: SiamoisClient, org_id: Any, project_id: Any, progress: Optional[Progress] = None) -> ProjectBundle:
    """Charge projet + UE + mobilier. Lève `Cancelled` si `progress(...)` renvoie False."""
    _step(progress, "Projet")
    project = client.project(project_id, fields="all")
    pid = project.id
    project_form = client.project_form(org_id)

    _step(progress, "Formulaires")
    ru_default, ru_by_type = client.recording_unit_forms(pid)
    find_default, find_by_type = client.find_forms(pid)

    ru_specs = merge_schemas(fixed_specs(), flatten_schema(ru_default), *(flatten_schema(f) for f in ru_by_type.values()))
    find_specs = merge_schemas(fixed_specs(), flatten_schema(find_default), *(flatten_schema(f) for f in find_by_type.values()))
    project_specs = merge_schemas(fixed_specs(), flatten_schema(project_form))

    for sp in find_specs:  # l'UE d'un mobilier : liste des UE du projet (utile à la création)
        if sp.binding == BINDING_RU:
            sp.kind, sp.vocab_code, sp.editable = CHOICE, VOCAB_RU, True

    # Vocabulaires de toutes les colonnes (un appel par code)
    codes = sorted({s.vocab_code for specs in (ru_specs, find_specs, project_specs) for s in specs
                    if s.vocab_code and s.vocab_code not in (VOCAB_STATUS, VOCAB_RU)})
    vocab_concepts: Dict[str, List[Dict[str, Any]]] = {}
    vocabs: Vocabularies = {VOCAB_STATUS: status_vocabulary(), VOCAB_RU: Vocabulary()}
    for i, code in enumerate(codes):
        _step(progress, f"Vocabulaire {code}", i, len(codes))
        v = client.vocabulary(pid, code)
        vocabs[code] = v
        vocab_concepts[code] = v.concepts

    # Projet (1 ligne)
    p_allowed = {s.field_id for s in project_specs}
    p_row = RowData(
        id=pid,
        cells=row_to_cells(project_specs, with_fixed(project.answers, project.full_identifier, project.validated), vocabs),
        geom=project.geom, allowed=p_allowed,
        incomplete=incomplete_columns(project_specs, project.answers),
    )

    # UE
    ru_rows: List[RowData] = []
    ru_allowed = {t: {*(c for c in f.field_ids()), FID_FULL, FID_STATUS} for t, f in ru_by_type.items()}
    default_allowed = {*ru_default.field_ids(), FID_FULL, FID_STATUS}

    def on_ru(done: int, total: int) -> bool:
        return progress("Unités d'enregistrement", done, total) is not False if progress else True

    for ru in client.iter_all(lambda **kw: client.recording_units(pid, **kw), on_progress=on_ru):
        ans = entity_answers(KIND_RU, ru, ru_specs)
        allowed = default_allowed | ru_allowed.get(ru.type_id or "", set())
        ru_rows.append(RowData(ru.id, row_to_cells(ru_specs, ans, vocabs), ru.geom, ru.sync_revision,
                               incomplete_columns(ru_specs, ru.answers), allowed))
    if progress and progress("Unités d'enregistrement", len(ru_rows), len(ru_rows)) is False:
        raise Cancelled()

    ru_concepts = [{"id": r.id, "resolvedLabel": r.cells.get("Identifiant complet")}
                   for r in ru_rows if r.cells.get("Identifiant complet")]
    vocabs[VOCAB_RU] = ru_vocabulary(ru_concepts)
    vocab_concepts[VOCAB_RU] = ru_concepts

    # Mobilier
    find_rows: List[RowData] = []
    find_allowed = {t: set(f.field_ids()) for t, f in find_by_type.items()}
    default_find = {*find_default.field_ids(), FID_FULL, FID_STATUS}

    def on_find(done: int, total: int) -> bool:
        return progress("Mobilier", done, total) is not False if progress else True

    for f in client.iter_all(lambda **kw: client.finds(pid, **kw), on_progress=on_find):
        ans = entity_answers(KIND_FIND, f, find_specs)
        allowed = default_find | find_allowed.get(f.type_id or "", set())
        find_rows.append(RowData(f.id, row_to_cells(find_specs, ans, vocabs), f.geom, None,
                                 incomplete_columns(find_specs, f.answers), allowed))

    layers = [
        LayerData(KIND_PROJECT, LAYER_NAMES[KIND_PROJECT], LAYER_TITLES[KIND_PROJECT], project_specs, [p_row],
                  srid_of(project.geom)),
        LayerData(KIND_RU, LAYER_NAMES[KIND_RU], LAYER_TITLES[KIND_RU], ru_specs, ru_rows,
                  dominant_srid(r.geom for r in ru_rows), type_allowed=ru_allowed, default_allowed=default_allowed),
        # L'API ne permet pas de modifier la géométrie d'un mobilier : lecture seule
        LayerData(KIND_FIND, LAYER_NAMES[KIND_FIND], LAYER_TITLES[KIND_FIND], find_specs, find_rows,
                  dominant_srid(r.geom for r in find_rows), geom_editable=False,
                  type_allowed={t: a | {FID_FULL, FID_STATUS} for t, a in find_allowed.items()},
                  default_allowed=default_find),
    ]
    return ProjectBundle(str(org_id), pid, project.name, layers, vocab_concepts, client.base_url)
