"""Modèles légers (dataclasses) construits depuis les réponses JSON de l'API.

Les champs de formulaire dynamiques restent dans ``answers`` (dict fieldId -> valeur brute)
pour ne pas figer le schéma : il dépend du formulaire configuré côté serveur.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Generic, List, Optional, TypeVar

T = TypeVar("T")


@dataclass
class Page(Generic[T]):
    items: List[T]
    total: int
    limit: int
    offset: int

    @property
    def has_more(self) -> bool:
        return self.offset + len(self.items) < self.total


@dataclass
class User:
    id: Optional[int]
    username: Optional[str]
    name: Optional[str]
    lastname: Optional[str]
    organizations: List["Organization"] = field(default_factory=list)

    @classmethod
    def from_json(cls, d: Dict[str, Any]) -> "User":
        return cls(
            id=d.get("id"),
            username=d.get("username"),
            name=d.get("name"),
            lastname=d.get("lastname"),
            organizations=[Organization.from_json(o) for o in d.get("organizations") or []],
        )


@dataclass
class Organization:
    id: str
    name: str
    identifier: Optional[str] = None
    description: Optional[str] = None
    can_create_projects: bool = False

    @classmethod
    def from_json(cls, d: Dict[str, Any]) -> "Organization":
        return cls(
            id=str(d.get("id")),
            name=d.get("name") or "",
            identifier=d.get("identifier"),
            description=d.get("description"),
            can_create_projects=bool((d.get("_permissions") or {}).get("canCreateProjects", False)),
        )


def _label(concept: Optional[Dict[str, Any]]) -> Optional[str]:
    if not concept:
        return None
    return concept.get("resolvedLabel") or concept.get("label")


@dataclass
class Permissions:
    can_edit: bool = False
    can_delete: bool = False
    can_validate: bool = False

    @classmethod
    def from_json(cls, d: Optional[Dict[str, Any]]) -> "Permissions":
        d = d or {}
        return cls(bool(d.get("canEdit")), bool(d.get("canDelete")), bool(d.get("canValidate")))


@dataclass
class Project:
    id: str
    name: str
    full_identifier: Optional[str] = None
    identifier: Optional[str] = None
    begin_date: Optional[str] = None
    end_date: Optional[str] = None
    validated: Optional[str] = None
    type_id: Optional[str] = None
    type_label: Optional[str] = None
    geom: Optional[Dict[str, Any]] = None
    permissions: Permissions = field(default_factory=Permissions)
    answers: Dict[str, Any] = field(default_factory=dict)
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, d: Dict[str, Any]) -> "Project":
        t = d.get("type") or {}
        return cls(
            id=str(d.get("id")),
            name=d.get("name") or "",
            full_identifier=d.get("fullIdentifier"),
            identifier=d.get("identifier"),
            begin_date=d.get("beginDate"),
            end_date=d.get("endDate"),
            validated=d.get("validated"),
            type_id=str(t["id"]) if t.get("id") is not None else None,
            type_label=_label(t),
            geom=d.get("geom"),
            permissions=Permissions.from_json(d.get("_permissions")),
            answers=d.get("answers") or {},
            raw=d,
        )


@dataclass
class RecordingUnit:
    id: str
    full_identifier: Optional[str] = None
    identifier: Optional[str] = None
    project_id: Optional[str] = None
    type_id: Optional[str] = None
    type_label: Optional[str] = None
    validated: Optional[str] = None
    sync_revision: Optional[int] = None
    geom: Optional[Dict[str, Any]] = None
    permissions: Permissions = field(default_factory=Permissions)
    answers: Dict[str, Any] = field(default_factory=dict)
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, d: Dict[str, Any]) -> "RecordingUnit":
        t = d.get("type") or {}
        return cls(
            id=str(d.get("id")),
            full_identifier=d.get("fullIdentifier"),
            identifier=d.get("identifier"),
            project_id=str(d["projectId"]) if d.get("projectId") is not None else None,
            type_id=str(t["id"]) if t.get("id") is not None else None,
            type_label=_label(t),
            validated=d.get("validated"),
            sync_revision=d.get("syncRevision"),
            geom=d.get("geom"),
            permissions=Permissions.from_json(d.get("_permissions")),
            answers=d.get("answers") or {},
            raw=d,
        )


@dataclass
class Find:
    """Mobilier. NB : l'API ne permet pas (encore) de modifier sa géométrie."""

    id: str
    full_identifier: Optional[str] = None
    project_id: Optional[str] = None
    recording_unit_id: Optional[str] = None
    type_id: Optional[str] = None
    type_label: Optional[str] = None
    validated: Optional[str] = None
    geom: Optional[Dict[str, Any]] = None
    permissions: Permissions = field(default_factory=Permissions)
    answers: Dict[str, Any] = field(default_factory=dict)
    raw: Dict[str, Any] = field(default_factory=dict, repr=False)

    @classmethod
    def from_json(cls, d: Dict[str, Any]) -> "Find":
        t = d.get("type") or {}
        ru = d.get("recordingUnit") or {}
        return cls(
            id=str(d.get("id")),
            full_identifier=d.get("fullIdentifier"),
            project_id=str(d["projectId"]) if d.get("projectId") is not None else None,
            recording_unit_id=str(ru["id"]) if ru.get("id") is not None else None,
            type_id=str(t["id"]) if t.get("id") is not None else None,
            type_label=_label(t),
            validated=d.get("validated"),
            geom=d.get("geom"),
            permissions=Permissions.from_json(d.get("_permissions")),
            answers=d.get("answers") or {},
            raw=d,
        )


def page_from_json(body: Dict[str, Any], mapper: Callable[[Dict[str, Any]], T], header_total: Optional[str] = None) -> Page[T]:
    items = [mapper(x) for x in body.get("data") or []]
    meta = body.get("meta") or {}
    total = meta.get("total")
    if total is None and header_total is not None:
        try:
            total = int(header_total)
        except ValueError:
            total = None
    return Page(items, int(total if total is not None else len(items)), int(meta.get("limit") or len(items)), int(meta.get("offset") or 0))
