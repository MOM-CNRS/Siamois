"""Formulaires dynamiques : parsing du layout serveur et conversion des réponses.

Le serveur décrit un formulaire par :
- un *layout* (``layoutJson``) : panneaux -> lignes -> colonnes (``fieldId``, largeur, requis, lecture seule…) ;
- un *catalogue* ``fields`` : ``{fieldId: FieldResource}`` (libellé, ``answerType``, contraintes…).

Ce module n'a aucune dépendance QGIS : le plugin construit les widgets à partir de ``FormDefinition``.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Tuple

SELECT_ONE_PREFIXES = ("SELECT_ONE", "SELECT_ADDRESS")
SELECT_MANY_PREFIX = "SELECT_MULTIPLE"


@dataclass
class FieldDef:
    id: str
    label: str
    answer_type: str
    hint: Optional[str] = None
    is_system: bool = False
    value_binding: Optional[str] = None
    field_code: Optional[str] = None  # code de vocabulaire pour SELECT_*_FROM_FIELD_CODE
    is_text_area: bool = False
    min: Optional[float] = None
    max: Optional[float] = None
    show_time: bool = False
    unit: Optional[str] = None
    read_only: bool = False

    @property
    def is_select_one(self) -> bool:
        return self.answer_type.startswith(SELECT_ONE_PREFIXES)

    @property
    def is_select_many(self) -> bool:
        return self.answer_type.startswith(SELECT_MANY_PREFIX)

    @classmethod
    def from_json(cls, fid: str, d: Dict[str, Any]) -> "FieldDef":
        c = d.get("constraints") or {}
        return cls(
            id=str(d.get("id", fid)),
            label=d.get("label") or str(fid),
            answer_type=d.get("answerType") or "TEXT",
            hint=d.get("hint"),
            is_system=bool(d.get("isSystemField")),
            value_binding=d.get("valueBinding"),
            field_code=d.get("fieldCode"),
            is_text_area=bool(d.get("isTextArea")),
            min=c.get("min"),
            max=c.get("max"),
            show_time=bool(c.get("showTime")),
            unit=c.get("unit"),
            read_only=bool(d.get("readOnly")),
        )


@dataclass
class Column:
    field_id: Optional[str]
    span: int = 12  # grille 12 colonnes (PrimeFlex)
    hidden: bool = False
    required: bool = False
    read_only: bool = False
    rules: Optional[Dict[str, Any]] = None


@dataclass
class Panel:
    name: str
    rows: List[List[Column]] = field(default_factory=list)


@dataclass
class FormDefinition:
    panels: List[Panel]
    fields: Dict[str, FieldDef]

    def columns(self) -> Iterable[Column]:
        for p in self.panels:
            for row in p.rows:
                yield from row

    def field_ids(self) -> List[str]:
        return [c.field_id for c in self.columns() if c.field_id is not None]

    def field(self, field_id: str) -> Optional[FieldDef]:
        return self.fields.get(str(field_id))


def _span(width: Any) -> int:
    if isinstance(width, dict):
        try:
            return max(1, min(12, int(width.get("span", 12))))
        except (TypeError, ValueError):
            return 12
    return 12


_WIDTH_BY_NAME = {"FULL": 12, "HALF": 6, "THIRD": 4, "QUARTER": 3}


def _parse_layout(layout: Any) -> List[Panel]:
    if layout is None or layout == "":
        return []
    if isinstance(layout, str):
        layout = json.loads(layout)
    # Forme "panneaux" (FormUiDtoLayoutJson / custom_form.layout)
    if isinstance(layout, list):
        panels = []
        for p in layout:
            rows = []
            for r in p.get("rows") or []:
                cols = []
                for c in r.get("columns") or []:
                    fid = c.get("fieldId")
                    cols.append(Column(
                        field_id=str(fid) if fid is not None else None,
                        span=_span(c.get("width")),
                        hidden=bool(c.get("hidden")),
                        required=bool(c.get("isRequired")),
                        read_only=bool(c.get("isReadOnly")),
                        rules=c.get("rules"),
                    ))
                rows.append(cols)
            panels.append(Panel(name=p.get("name") or "", rows=rows))
        return panels
    # Forme "groups" (gabarits système), une colonne par ligne
    if isinstance(layout, dict) and "groups" in layout:
        panels = []
        for g in layout["groups"]:
            rows = [[Column(str(f["fieldId"]), _WIDTH_BY_NAME.get(f.get("width"), 12))] for f in g.get("fields") or []]
            panels.append(Panel(name=g.get("label") or "", rows=rows))
        return panels
    raise ValueError("Layout de formulaire non reconnu")


def parse_form(layout_json: Any, fields: Optional[Dict[str, Any]]) -> FormDefinition:
    """Construit un FormDefinition depuis ``layoutJson`` et le catalogue ``fields``."""
    defs = {str(k): FieldDef.from_json(str(k), v) for k, v in (fields or {}).items()}
    return FormDefinition(_parse_layout(layout_json), defs)


def parse_form_bundle(bundle: Optional[Dict[str, Any]], fields: Optional[Dict[str, Any]]) -> FormDefinition:
    """``bundle`` = ``formBundle`` ou ``form`` d'un type ({resourceType, layoutJson})."""
    return parse_form((bundle or {}).get("layoutJson"), fields)


# --------------------------------------------------------------------------- réponses


def is_envelope(value: Any) -> bool:
    return isinstance(value, dict) and "answerType" in value


def raw_value(answer: Any) -> Any:
    """Valeur « brute » d'une réponse, qu'elle vienne du détail (enveloppe) ou d'une liste."""
    if is_envelope(answer):
        if "values" in answer:
            return list(answer.get("values") or [])
        return answer.get("value")
    if isinstance(answer, dict) and "values" in answer and "total" in answer:  # MultiValue
        return list(answer.get("values") or [])
    return answer


def is_incomplete_multi(answer: Any) -> bool:
    """Vrai si une réponse multi-valeurs n'est qu'un aperçu (ne jamais la réécrire via ``values``)."""
    return isinstance(answer, dict) and answer.get("complete") is False


def ref_id(value: Any) -> Optional[str]:
    if isinstance(value, dict):
        rid = value.get("resourceId", value.get("id"))
        return str(rid) if rid is not None else None
    return str(value) if value is not None else None


def display_value(value: Any) -> str:
    """Texte lisible d'une valeur brute (pour tables d'attributs et libellés en lecture seule)."""
    value = raw_value(value)
    if value is None:
        return ""
    if isinstance(value, list):
        return ", ".join(display_value(v) for v in value)
    if isinstance(value, dict):
        if "label" in value and value["label"] is not None:
            return str(value["label"])
        if "value" in value:  # mesure
            unit = value.get("unit") or ""
            return f"{value['value']} {unit}".strip()
        return ref_id(value) or ""
    return str(value)


def build_answer_input(fd: FieldDef, value: Any) -> Dict[str, Any]:
    """Corps ``AnswerInput`` d'un PATCH pour la valeur saisie dans l'UI.

    - select un : ``value`` = id (ou ref) ;  vide -> ``{"value": None}``
    - select plusieurs : ``values`` = liste d'ids ;  vide -> ``{"values": []}``
    - autres : ``value`` scalaire.
    """
    if fd.is_select_many:
        return {"values": [ref_id(v) for v in (value or [])]}
    if fd.is_select_one:
        return {"value": ref_id(value) if value not in (None, "") else None}
    if value == "":
        value = None
    return {"value": value}


def diff_multi(old_ids: Iterable[Any], new_ids: Iterable[Any]) -> Dict[str, List[str]]:
    """Écriture sûre d'un multi-valeurs partiel : ``{"add": [...], "remove": [...]}``."""
    old = {str(i) for i in old_ids}
    new = {str(i) for i in new_ids}
    return {"add": sorted(new - old), "remove": sorted(old - new)}


def build_patch_answers(form: FormDefinition, edited: Dict[str, Any], original: Optional[Dict[str, Any]] = None) -> Dict[str, Dict[str, Any]]:
    """Construit ``answers`` d'un PATCH : seuls les champs modifiés, jamais les champs en lecture seule.

    ``edited`` : fieldId -> valeur saisie. ``original`` : réponses lues (détail ou liste) pour détecter les
    modifications et utiliser add/remove quand une valeur multiple lue était incomplète.
    """
    original = original or {}
    readonly = {c.field_id for c in form.columns() if c.read_only}
    out: Dict[str, Dict[str, Any]] = {}
    for fid, value in edited.items():
        fd = form.field(fid)
        if fd is None or fd.read_only or fid in readonly:
            continue
        old_answer = original.get(fid)
        old = raw_value(old_answer)
        if fd.is_select_many:
            old_ids = [ref_id(v) for v in (old or [])]
            new_ids = [ref_id(v) for v in (value or [])]
            if sorted(map(str, old_ids)) == sorted(map(str, new_ids)):
                continue
            if is_incomplete_multi(old_answer):
                out[fid] = diff_multi(old_ids, new_ids)
            else:
                out[fid] = build_answer_input(fd, value)
            continue
        if fd.is_select_one:
            if ref_id(old) == (ref_id(value) if value not in (None, "") else None):
                continue
        elif old == value or (old in (None, "") and value in (None, "")):
            continue
        out[fid] = build_answer_input(fd, value)
    return out
