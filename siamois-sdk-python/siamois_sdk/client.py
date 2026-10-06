"""Client HTTP de l'API SIAMOIS (``/api/v1``)."""

from __future__ import annotations

import logging
import time
from typing import Any, Callable, Dict, Iterator, List, Optional, Tuple

import requests

from .errors import (
    AuthenticationError,
    InvalidResponseError,
    NetworkError,
    SiamoisError,
    error_from_response,
)
from .flatten import Vocabulary
from .forms import FormDefinition, parse_form, parse_form_bundle
from .models import (
    Find,
    Organization,
    Page,
    Project,
    RecordingUnit,
    User,
    page_from_json,
)

log = logging.getLogger("siamois_sdk")

API_PREFIX = "/api/v1"
MAX_LIMIT = 200  # plafond serveur


class SiamoisClient:
    """Client synchrone. Un client = une session authentifiée (JWT, sans refresh).

    >>> c = SiamoisClient("https://siamois.example.org/siamois")
    >>> c.login("me@example.org", "secret")
    >>> org = c.organizations().items[0]
    >>> for p in c.iter_all(lambda **kw: c.projects(organization_id=org.id, **kw)): ...
    """

    def __init__(self, base_url: str, *, lang: str = "fr", timeout: float = 30.0,
                 session: Optional[requests.Session] = None):
        self.base_url = base_url.rstrip("/")
        self.lang = lang
        self.timeout = timeout
        self._session = session or requests.Session()
        self.token: Optional[str] = None
        self.token_expires_at: Optional[float] = None
        self.user: Optional[User] = None

    # ------------------------------------------------------------------ bas niveau

    @property
    def is_authenticated(self) -> bool:
        if not self.token:
            return False
        return self.token_expires_at is None or time.time() < self.token_expires_at

    def _url(self, path: str) -> str:
        return f"{self.base_url}{API_PREFIX}{path}"

    def _request(self, method: str, path: str, *, params: Optional[Dict[str, Any]] = None,
                 json: Any = None, auth: bool = True) -> Tuple[Any, Dict[str, str]]:
        headers = {"Accept": "application/json", "Accept-Language": self.lang}
        if auth and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        params = {k: v for k, v in (params or {}).items() if v is not None}
        try:
            resp = self._session.request(method, self._url(path), params=params, json=json,
                                         headers=headers, timeout=self.timeout)
        except requests.RequestException as exc:  # timeout, DNS, connexion refusée…
            log.warning("%s %s -> erreur réseau : %s", method, path, exc)
            raise NetworkError(str(exc)) from exc
        log.debug("%s %s %s -> %s", method, path, params or "", resp.status_code)
        body: Any = None
        if resp.content:
            try:
                body = resp.json()
            except ValueError:
                if resp.status_code < 400:
                    raise InvalidResponseError("Réponse non JSON", resp.status_code)
        if resp.status_code >= 400:
            log.warning("%s %s -> HTTP %s : %s", method, path, resp.status_code, str(body)[:500])
            raise error_from_response(resp.status_code, body)
        return body, resp.headers

    def _get(self, path: str, **params: Any) -> Any:
        return self._request("GET", path, params=params)[0]

    def _get_page(self, path: str, mapper: Callable[[Dict[str, Any]], Any], **params: Any) -> Page:
        body, headers = self._request("GET", path, params=params)
        if not isinstance(body, dict):
            raise InvalidResponseError()
        return page_from_json(body, mapper, headers.get("X-Total-Count"))

    @staticmethod
    def _data(body: Any) -> Dict[str, Any]:
        if not isinstance(body, dict) or not isinstance(body.get("data"), dict):
            raise InvalidResponseError()
        return body["data"]

    # ------------------------------------------------------------------ auth

    def login(self, email: str, password: str) -> User:
        body, _ = self._request("POST", "/auth/login", json={"email": email, "password": password}, auth=False)
        if not isinstance(body, dict) or not body.get("accessToken"):
            raise InvalidResponseError()
        self.token = body["accessToken"]
        expires_in = body.get("expiresIn")
        # marge de 30 s ; expiresIn est supposé en secondes
        self.token_expires_at = time.time() + float(expires_in) - 30 if expires_in else None
        self.user = User.from_json(body.get("user") or {})
        return self.user

    def logout(self) -> None:
        self.token = None
        self.token_expires_at = None
        self.user = None

    def me(self) -> User:
        body = self._get("/auth/me")
        return User.from_json(body)

    # ------------------------------------------------------------------ pagination

    @staticmethod
    def iter_all(fetch: Callable[..., Page], page_size: int = 100,
                 on_progress: Optional[Callable[[int, int], bool]] = None) -> Iterator[Any]:
        """Parcourt toutes les pages de ``fetch(offset=, limit=)``.

        ``on_progress(done, total)`` peut renvoyer ``False`` pour interrompre (annulation utilisateur).
        """
        page_size = max(1, min(page_size, MAX_LIMIT))
        offset = 0
        while True:
            page = fetch(offset=offset, limit=page_size)
            for item in page.items:
                yield item
            offset += len(page.items)
            if on_progress is not None and on_progress(offset, page.total) is False:
                return
            if not page.items or offset >= page.total:
                return

    # ------------------------------------------------------------------ organisations / projets

    def organizations(self, offset: int = 0, limit: int = 100) -> Page[Organization]:
        return self._get_page("/organizations", Organization.from_json, offset=offset, limit=limit)

    def projects(self, organization_id: Any = None, *, search: Optional[str] = None, sort: str = "name:asc",
                 fields: Optional[str] = None, offset: int = 0, limit: int = 50) -> Page[Project]:
        return self._get_page("/projects", Project.from_json, organizationId=organization_id, search=search,
                              sort=sort, fields=fields, offset=offset, limit=limit)

    def project(self, project_id: Any, fields: Optional[str] = "all") -> Project:
        return Project.from_json(self._data(self._get(f"/projects/{project_id}", fields=fields)))

    def project_form(self, organization_id: Any) -> FormDefinition:
        """Formulaire (par défaut) de la fiche projet d'une organisation."""
        body = self._get(f"/organizations/{organization_id}/project-types")
        default = (body or {}).get("_default") or {}
        return parse_form_bundle(default.get("form"), (body or {}).get("fields"))

    def update_project(self, project_id: Any, **changes: Any) -> Project:
        """PATCH projet. Clés : name, identifier, typeId, beginDate, endDate, mainLocationId,
        spatialContextSpatialUnitIds, validated, answers, geom."""
        body, _ = self._request("PATCH", f"/projects/{project_id}", json=changes)
        return Project.from_json(self._data(body))

    # ------------------------------------------------------------------ unités d'enregistrement

    def recording_units(self, project_id: Any, *, search: Optional[str] = None, sort: Optional[str] = None,
                        fields: Optional[str] = "all", values_limit: int = 200, offset: int = 0,
                        limit: int = 100) -> Page[RecordingUnit]:
        return self._get_page(f"/projects/{project_id}/recording-units", RecordingUnit.from_json,
                              search=search, sort=sort, fields=fields, valuesLimit=values_limit,
                              offset=offset, limit=limit)

    def recording_unit(self, ru_id: Any) -> RecordingUnit:
        return RecordingUnit.from_json(self._data(self._get(f"/recording-units/{ru_id}")))

    def recording_unit_forms(self, project_id: Any) -> Tuple[FormDefinition, Dict[str, FormDefinition]]:
        """(formulaire par défaut, {type_id: formulaire du type}) pour les UE d'un projet."""
        return self._types(f"/projects/{project_id}/recording-unit-types")

    def create_recording_unit(self, project_id: Any, type_id: Any, *, answers: Optional[Dict[str, Any]] = None,
                              geom: Optional[Dict[str, Any]] = None,
                              parent_recording_unit_id: Optional[Any] = None) -> RecordingUnit:
        """POST UE. ``type_id`` = concept de type (obligatoire). L'identifiant complet est généré par le serveur."""
        payload: Dict[str, Any] = {"projectId": str(project_id), "typeId": str(type_id)}
        if answers:
            payload["answers"] = answers
        if geom:
            payload["geom"] = geom
        if parent_recording_unit_id is not None:
            payload["parentRecordingUnitId"] = int(parent_recording_unit_id)
        body, _ = self._request("POST", "/recording-units", json=payload)
        return RecordingUnit.from_json(self._data(body))

    def update_recording_unit(self, ru_id: Any, *, answers: Optional[Dict[str, Any]] = None,
                              geom: Any = ..., validated: Optional[str] = None,
                              expected_revision: Optional[int] = None) -> RecordingUnit:
        """PATCH UE. ``geom=None`` supprime la géométrie, ``geom=...`` (défaut) la laisse inchangée.

        ``expected_revision`` (= ``syncRevision`` lue) : le serveur répond 409 (``ConflictError``) si
        la version serveur a changé entre-temps.
        """
        payload: Dict[str, Any] = {}
        if answers:
            payload["answers"] = answers
        if geom is not ...:
            payload["geom"] = geom
        if validated is not None:
            payload["validated"] = validated
        if expected_revision is not None:
            payload["expectedRevision"] = expected_revision
        body, _ = self._request("PATCH", f"/recording-units/{ru_id}", json=payload)
        return RecordingUnit.from_json(self._data(body))

    # ------------------------------------------------------------------ mobilier

    def finds(self, project_id: Any, *, search: Optional[str] = None, sort: Optional[str] = None,
              fields: Optional[str] = "all", values_limit: int = 200, offset: int = 0,
              limit: int = 100) -> Page[Find]:
        return self._get_page(f"/projects/{project_id}/mobiliers", Find.from_json,
                              search=search, sort=sort, fields=fields, valuesLimit=values_limit,
                              offset=offset, limit=limit)

    def find(self, find_id: Any) -> Find:
        return Find.from_json(self._data(self._get(f"/finds/{find_id}")))

    def find_forms(self, project_id: Any) -> Tuple[FormDefinition, Dict[str, FormDefinition]]:
        return self._types(f"/projects/{project_id}/find-types")

    def create_find(self, recording_unit_id: Any, type_id: Any, *, answers: Optional[Dict[str, Any]] = None) -> Find:
        """POST mobilier rattaché à une UE. L'API n'accepte pas de géométrie à la création."""
        payload: Dict[str, Any] = {"recordingUnitId": str(recording_unit_id), "typeId": str(type_id)}
        if answers:
            payload["answers"] = answers
        body, _ = self._request("POST", "/finds", json=payload)
        return Find.from_json(self._data(body))

    def update_find(self, find_id: Any, *, answers: Optional[Dict[str, Any]] = None,
                    validated: Optional[str] = None) -> Find:
        """PATCH mobilier. L'API ne permet pas de modifier la géométrie d'un mobilier."""
        payload: Dict[str, Any] = {}
        if answers:
            payload["answers"] = answers
        if validated is not None:
            payload["validated"] = validated
        body, _ = self._request("PATCH", f"/finds/{find_id}", json=payload)
        return Find.from_json(self._data(body))

    # ------------------------------------------------------------------ vocabulaires

    def concepts(self, project_id: Any, field_code: str, *, q: Optional[str] = None,
                 offset: int = 0, limit: int = 50) -> List[Dict[str, Any]]:
        """Valeurs possibles d'un champ à vocabulaire (``FieldDef.field_code``)."""
        body = self._get(f"/projects/{project_id}/concepts", fieldCode=field_code, q=q, offset=offset, limit=limit)
        return list((body or {}).get("data") or [])

    def vocabulary(self, project_id: Any, field_code: str) -> Vocabulary:
        """Vocabulaire complet (toutes les pages) d'un code, pour libellés <-> identifiants."""
        concepts: List[Dict[str, Any]] = []
        offset = 0
        while True:
            chunk = self.concepts(project_id, field_code, offset=offset, limit=MAX_LIMIT)
            concepts.extend(chunk)
            if len(chunk) < MAX_LIMIT:
                break
            offset += len(chunk)
        return Vocabulary(concepts)

    # ------------------------------------------------------------------ interne

    def _types(self, path: str) -> Tuple[FormDefinition, Dict[str, FormDefinition]]:
        body = self._get(path)
        if not isinstance(body, dict):
            raise InvalidResponseError()
        top_fields = body.get("fields") or {}
        default = body.get("_default") or {}
        default_form = parse_form_bundle(default.get("formBundle") or default.get("form"),
                                         default.get("fields") or top_fields)
        by_type: Dict[str, FormDefinition] = {}
        for t in body.get("data") or []:
            tid = t.get("id") or (t.get("concept") or {}).get("id")
            if tid is None:
                continue
            by_type[str(tid)] = parse_form_bundle(t.get("formBundle"), t.get("fields") or top_fields)
        return default_form, by_type
