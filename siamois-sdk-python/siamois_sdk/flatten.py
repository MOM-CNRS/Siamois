"""Mise à plat « façon tableur » : formulaire dynamique <-> colonnes simples.

Chaque champ du formulaire (système ET additionnel) devient une colonne nommée par son libellé ;
les vocabulaires sont affichés par leur libellé, pas par leur identifiant. À la synchronisation, seules
les cellules modifiées par rapport à la copie de référence sont relues et re-résolues vers l'API
(même esprit que l'import xlsx : en-tête = libellé, comparaison normalisée, erreurs ligne par ligne,
libellé ambigu = erreur, jamais de choix au hasard).

Aucune dépendance QGIS : le plugin ne fait que stocker/afficher ces colonnes.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, Iterable, List, Optional, Tuple

from .forms import FormDefinition, FieldDef, is_incomplete_multi, raw_value, ref_id

MULTI_SEP = ";"

# Types de colonne (côté stockage)
TEXT, INTEGER, DECIMAL, DATETIME, MEASUREMENT = "text", "integer", "decimal", "datetime", "measurement"
CHOICE, CHOICE_MANY, REFERENCE = "choice", "choice_many", "reference"


def normalize(s: Any) -> str:
    """Casse, accents et espaces ne comptent pas (comme `normalize()` de l'import xlsx)."""
    s = unicodedata.normalize("NFKD", str(s or ""))
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", s).strip().casefold()


@dataclass
class ColumnSpec:
    name: str  # nom de colonne = libellé du champ (dédoublonné)
    field_id: str
    kind: str
    answer_type: str
    vocab_code: Optional[str] = None  # code de vocabulaire (CHOICE / CHOICE_MANY)
    editable: bool = True
    required: bool = False
    min: Optional[float] = None
    max: Optional[float] = None
    unit: Optional[str] = None
    show_time: bool = False
    hint: Optional[str] = None
    panel: str = ""  # panneau (onglet) du formulaire serveur

    @property
    def is_choice(self) -> bool:
        return self.kind in (CHOICE, CHOICE_MANY)


def _kind(fd: FieldDef) -> Tuple[str, bool]:
    """(kind, éditable en v1)."""
    t = fd.answer_type
    if t == "TEXT":
        return TEXT, True
    if t == "INTEGER":
        return INTEGER, True
    if t == "DECIMAL":
        return DECIMAL, True
    if t == "DATETIME":
        return DATETIME, True
    if t == "MEASUREMENT":
        return MEASUREMENT, True
    if t == "SELECT_ONE_FROM_FIELD_CODE" and fd.field_code:
        return CHOICE, True
    if t == "SELECT_MULTIPLE_FROM_FIELD_CODE" and fd.field_code:
        return CHOICE_MANY, True
    # Références (personnes, UE, lieux…) : affichées, non éditables en v1
    return (CHOICE_MANY if fd.is_select_many else REFERENCE), False


def flatten_schema(form: FormDefinition, *, skip_hidden: bool = True) -> List[ColumnSpec]:
    """Colonnes d'un formulaire, dans l'ordre du layout, noms uniques."""
    specs: List[ColumnSpec] = []
    seen: Dict[str, int] = {}
    required = {c.field_id: c.required for c in form.columns()}
    layout_ro = {c.field_id: c.read_only for c in form.columns()}
    hidden = {c.field_id for c in form.columns() if c.hidden}
    panel_of = {c.field_id: p.name for p in form.panels for row in p.rows for c in row if c.field_id}
    done = set()
    for fid in form.field_ids():
        if fid in done or (skip_hidden and fid in hidden):
            continue
        done.add(fid)
        fd = form.field(fid)
        if fd is None:
            continue
        kind, editable = _kind(fd)
        if fd.read_only or layout_ro.get(fid):
            editable = False
        name = fd.label
        n = seen.get(normalize(name), 0)
        seen[normalize(name)] = n + 1
        if n:
            name = f"{name} ({fid})"
        specs.append(ColumnSpec(
            name=name, field_id=fid, kind=kind,
            answer_type=fd.answer_type,
            vocab_code=fd.field_code if kind in (CHOICE, CHOICE_MANY) and fd.field_code else None,
            editable=editable, required=bool(required.get(fid)), min=fd.min, max=fd.max, unit=fd.unit,
            show_time=fd.show_time, hint=fd.hint, panel=panel_of.get(fid, ""),
        ))
    return specs


def merge_schemas(*schemas: Iterable[ColumnSpec]) -> List[ColumnSpec]:
    """Union (par field_id) de plusieurs formulaires (un par type) : la couche a toutes les colonnes."""
    out: List[ColumnSpec] = []
    by_id: Dict[str, ColumnSpec] = {}
    names: Dict[str, str] = {}
    for schema in schemas:
        for s in schema:
            if s.field_id in by_id:
                continue
            c = ColumnSpec(**s.__dict__)
            if names.get(normalize(c.name)) not in (None, c.field_id):
                c.name = f"{c.name} ({c.field_id})"
            names[normalize(c.name)] = c.field_id
            by_id[c.field_id] = c
            out.append(c)
    return out


# ------------------------------------------------------------------ vocabulaires


class Vocabulary:
    """Concepts d'un code de vocabulaire : id <-> libellé, résolution normalisée."""

    def __init__(self, concepts: Iterable[Dict[str, Any]] = ()):
        self.labels: Dict[str, str] = {}
        self._by_norm: Dict[str, List[str]] = {}
        for c in concepts:
            cid = ref_id(c)
            label = c.get("resolvedLabel") or c.get("prefLabel") or c.get("label")
            if cid is None or label is None:
                continue
            self.labels[cid] = label
            ids = self._by_norm.setdefault(normalize(label), [])
            if cid not in ids:
                ids.append(cid)
            pref = c.get("prefLabel")  # un libellé alternatif résout aussi vers le concept
            if pref and normalize(pref) != normalize(label):
                ids = self._by_norm.setdefault(normalize(pref), [])
                if cid not in ids:
                    ids.append(cid)

    def label_of(self, cid: Any, fallback: Optional[str] = None) -> str:
        return self.labels.get(str(cid), fallback if fallback is not None else str(cid))

    def resolve(self, label: str) -> str:
        ids = self._by_norm.get(normalize(label), [])
        if not ids:
            raise LookupError("inconnu")
        if len(ids) > 1:
            raise LookupError("ambigu")
        return ids[0]

    @property
    def sorted_labels(self) -> List[str]:
        return sorted(set(self.labels.values()), key=normalize)


Vocabularies = Dict[str, Vocabulary]  # vocab_code -> Vocabulary


# ------------------------------------------------------------------ valeur API -> cellule


def _fmt_date(v: Any) -> Optional[str]:
    return str(v) if v not in (None, "") else None


def to_cell(spec: ColumnSpec, answer: Any, vocabs: Optional[Vocabularies] = None) -> Any:
    """Valeur d'API (enveloppe ou brute) -> valeur de cellule affichable/stockable."""
    value = raw_value(answer)
    if value is None or value == []:
        return None
    vocab = (vocabs or {}).get(spec.vocab_code or "")
    if spec.kind == CHOICE_MANY or (spec.kind == REFERENCE and isinstance(value, list)):
        items = value if isinstance(value, list) else [value]
        labels = []
        for it in items:
            cid = ref_id(it)
            lbl = it.get("label") if isinstance(it, dict) else None
            labels.append(vocab.label_of(cid, lbl) if vocab else (lbl or str(cid)))
        return MULTI_SEP.join(labels)
    if spec.kind in (CHOICE, REFERENCE):
        cid = ref_id(value)
        lbl = value.get("label") if isinstance(value, dict) else None
        return vocab.label_of(cid, lbl) if vocab else (lbl or cid)
    if spec.kind == MEASUREMENT:
        return value.get("numericValue") if isinstance(value, dict) else value
    if spec.kind == DATETIME:
        return _fmt_date(value)
    return value


def row_to_cells(specs: Iterable[ColumnSpec], answers: Dict[str, Any], vocabs: Optional[Vocabularies] = None) -> Dict[str, Any]:
    return {s.name: to_cell(s, answers.get(s.field_id), vocabs) for s in specs}


def incomplete_columns(specs: Iterable[ColumnSpec], answers: Dict[str, Any]) -> List[str]:
    """Colonnes multi-valeurs dont la lecture n'était qu'un aperçu (jamais réécrites via ``values``)."""
    return [s.name for s in specs if is_incomplete_multi(answers.get(s.field_id))]


# ------------------------------------------------------------------ cellule -> PATCH


@dataclass
class Issue:
    column: str
    message: str
    blocking: bool = True


@dataclass
class RowDiff:
    answers: Dict[str, Dict[str, Any]] = field(default_factory=dict)  # fieldId -> AnswerInput
    issues: List[Issue] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not any(i.blocking for i in self.issues)


def _blank(v: Any) -> bool:
    return v is None or (isinstance(v, str) and v.strip() == "")


def _same(a: Any, b: Any) -> bool:
    if _blank(a) and _blank(b):
        return True
    return str(a).strip() == str(b).strip()


def _parse_number(spec: ColumnSpec, v: Any) -> Any:
    try:
        n = float(str(v).replace(",", ".")) if not isinstance(v, (int, float)) else float(v)
    except ValueError:
        raise ValueError(f"« {v} » n'est pas un nombre")
    if spec.kind == INTEGER:
        if n != int(n):
            raise ValueError(f"« {v} » n'est pas un entier")
        n = int(n)
    if spec.min is not None and n < spec.min:
        raise ValueError(f"{n} < minimum {spec.min:g}")
    if spec.max is not None and n > spec.max:
        raise ValueError(f"{n} > maximum {spec.max:g}")
    return n


def _parse_date(v: Any) -> str:
    s = str(v).strip().replace(" ", "T", 1) if " " in str(v).strip() and "T" not in str(v) else str(v).strip()
    try:
        d = datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        raise ValueError(f"« {v} » n'est pas une date ISO (AAAA-MM-JJ)")
    if d.tzinfo is None:
        s = d.strftime("%Y-%m-%dT%H:%M:%S") + "Z" if "T" in s else d.strftime("%Y-%m-%dT00:00:00Z")
    return s


def cells_to_patch(specs: Iterable[ColumnSpec], base: Dict[str, Any], current: Dict[str, Any],
                   vocabs: Optional[Vocabularies] = None, *, original_ids: Optional[Dict[str, Any]] = None,
                   incomplete: Iterable[str] = ()) -> RowDiff:
    """Compare ``current`` à ``base`` (copie de référence) et construit les ``answers`` d'un PATCH.

    - Seules les cellules modifiées sont traitées (une cellule non modifiée n'est jamais renvoyée).
    - Une cellule vidée par l'utilisateur efface la valeur (contrairement à l'import xlsx, c'est ici
      une action explicite : la cellule avait une valeur à la référence).
    - Colonne non éditable modifiée : avertissement non bloquant, modification ignorée.
    - Libellé inconnu / ambigu, nombre ou date illisible, hors bornes : erreur bloquante.
    - Champ obligatoire vidé : erreur bloquante ; obligatoire vide inchangé : avertissement (comme l'import).
    - Multi-valeurs dont la lecture était incomplète : écrit en ``add``/``remove`` (jamais ``values``).
    """
    diff = RowDiff()
    incomplete = set(incomplete)
    for s in specs:
        b, c = base.get(s.name), current.get(s.name)
        changed = not _same(b, c)
        if not changed:
            if s.required and _blank(c) and s.editable:
                diff.issues.append(Issue(s.name, "champ obligatoire vide", blocking=False))
            continue
        if not s.editable:
            diff.issues.append(Issue(s.name, "colonne en lecture seule : modification ignorée", blocking=False))
            continue
        if _blank(c):
            if s.required:
                diff.issues.append(Issue(s.name, "champ obligatoire : ne peut pas être vidé"))
                continue
            diff.answers[s.field_id] = {"values": []} if s.kind == CHOICE_MANY else {"value": None}
            continue
        try:
            diff.answers[s.field_id] = _cell_to_input(s, c, b, vocabs, s.name in incomplete)
        except ValueError as exc:
            diff.issues.append(Issue(s.name, str(exc)))
    return diff


def _resolve(s: ColumnSpec, label: str, vocabs: Optional[Vocabularies]) -> str:
    vocab = (vocabs or {}).get(s.vocab_code or "")
    if vocab is None:
        raise ValueError(f"vocabulaire « {s.vocab_code} » non chargé")
    try:
        return vocab.resolve(label)
    except LookupError as exc:
        reason = "libellé ambigu" if str(exc) == "ambigu" else "libellé inconnu"
        raise ValueError(f"{reason} : « {label} »")


def _split(v: Any) -> List[str]:
    return [p.strip() for p in str(v).split(MULTI_SEP) if p.strip()]


def _cell_to_input(s: ColumnSpec, cur: Any, base: Any, vocabs: Optional[Vocabularies], incomplete: bool) -> Dict[str, Any]:
    if s.kind == TEXT:
        return {"value": str(cur)}
    if s.kind in (INTEGER, DECIMAL, MEASUREMENT):
        return {"value": _parse_number(s, cur)}
    if s.kind == DATETIME:
        return {"value": _parse_date(cur)}
    if s.kind == CHOICE:
        return {"value": _resolve(s, str(cur), vocabs)}
    if s.kind == CHOICE_MANY:
        new = [_resolve(s, lbl, vocabs) for lbl in _split(cur)]
        if incomplete:
            old = [] if _blank(base) else [_resolve(s, lbl, vocabs) for lbl in _split(base)]
            return {"add": sorted(set(new) - set(old)), "remove": sorted(set(old) - set(new))}
        return {"values": list(dict.fromkeys(new))}
    raise ValueError("type de colonne non éditable")
