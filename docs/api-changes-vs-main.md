# SIAMOIS REST API — changes on `feat/main-panel-react-migration` vs `main`

**Scope:** public API under `/api/v1/**` (JWT-authenticated). Generated 2026-09-25 from a source diff of controllers, request/response DTOs and mappers (`git diff main...feat/main-panel-react-migration`).
**Audience:** external API consumers.

> Conventions used below: `{id}` for a project accepts the numeric id, the `fullIdentifier` or the short identifier (unchanged). All list endpoints keep the `offset` / `limit` pagination, the `meta: { total, limit, offset }` envelope and the `X-Total-Count` header unless stated otherwise.

---

## 1. Summary

| Kind | Count |
|---|---|
| Endpoints **removed** | 3 |
| Endpoints whose contract changed in a **breaking** way | 6 (plus 3 schema-level breaks) |
| Endpoints **added** | 34 |
| Existing endpoints with **additive** changes (new params/fields) | ~15 |

Things most likely to break an existing client, in order:

1. `GET /api/v1/organizations/{id}/recording-units` and `GET /api/v1/organizations/{id}/places` are **gone**. Use `GET /api/v1/recording-units?organizationId=` and `GET /api/v1/places?organizationId=`.
2. `GET /api/v1/projects/form` is **gone**. Use `GET /api/v1/organizations/{id}/project-types`.
3. `GET /api/v1/projects/{id}/phases` and `GET /api/v1/recording-units/{id}/children` are **now paginated**, defaulting to 10 and 200 items respectively. Previously they returned everything.
4. `ValidationStatus` has a new value, **`CANCELLED`**.
5. The `answers` key on list items (recording units) is **omitted** unless `?fields=` is sent. Previously it was always present as `{}`.
6. Project, recording-unit, find, phase and container lists now **reject unknown sort properties and unknown `f.*` filter keys with `400`**. On `main`, an unknown sort property was silently replaced by the default order.

---

## 2. Breaking changes

| # | Endpoint / schema | Before (`main`) | Now | Migration |
|---|---|---|---|---|
| B1 | `GET /api/v1/organizations/{id}/recording-units` | Paginated RU list of an organisation (`offset`, `limit` only, no sort/search) | **Removed** | `GET /api/v1/recording-units?organizationId={id}&offset=&limit=`. Adds `search`, `sort` (default `creationTime:desc`) and `f.*` filters. A member without organisation-wide access only sees RUs of their own projects. |
| B2 | `GET /api/v1/organizations/{id}/places` | Paginated places, default `limit=50`, `sort` in `name,id,code,creationTime` | **Removed** | `GET /api/v1/places?organizationId={id}`. **Default `limit` is now 10** (was 50), so pass `limit` explicitly. Adds `search`. Default sort `name:asc`. |
| B3 | `GET /api/v1/projects/form?organizationId=` | `ProjectFormResponse { data: ProjectFormData }`: layout + field definitions of the creation form | **Removed** (`ProjectFormResponse` / `ProjectFormData` schemas deleted) | `GET /api/v1/organizations/{id}/project-types` → `ProjectTypeListResponse { data: ProjectType[], _default: ProjectDefaultType { form, fieldConfigs, tableColumns }, fields: {fieldId → FieldResource} }`. The form layout is `_default.form`. `data` is empty for now, since projects have no configurable types yet. |
| B4 | `GET /api/v1/projects/{id}/phases` | **All** phases, unpaginated. Returned the bare body, `meta.total == data.length` | Paginated: `offset` (0), **`limit` (default 10)**, `search`, `sort` (default `orderNumber:asc`; also `identifier`, `title`, `id`), `f.*` filters, `fields` projection | To keep fetching all phases, page through with `meta.total`, or pass a large `limit`. |
| B5 | `GET /api/v1/recording-units/{id}/children` | **All** direct children, unpaginated, **`meta: null`** | Paginated: `offset` (0), **`limit` (default 200)**, `search`, `sort` (default `creationTime:desc`), `f.*`, `fields`. `meta` is now always populated, plus the `X-Total-Count` header | Clients that assumed `meta == null` or that the list is complete must page. |
| B6 | `GET /api/v1/projects` — `sort` | The parameter was mis-declared (`name="name:asc"`), so **`sort=` was ignored** and results came back in default order | `sort` is honoured. Default `name:asc`. Allowed: `id, name, identifier, fullIdentifier, beginDate, endDate, creationTime, recordingUnitCount`, and `<fieldId>:asc\|desc`. **An unknown property returns `400`** | Check any `sort` you already send, because it is now applied and validated. |
| B6b | Sorting on RU lists (`/projects/{id}/recording-units`, `/recording-units/{id}/children`) and on find lists (`/recording-units/{id}/mobiliers`) | Unknown property → silently fell back to `creationTime:desc` | Unknown property → **`400 Champ de tri inconnu`** | Only send documented properties (see §4 "Sorting"). |
| B7 | `GET /api/context/check` *(internal, see §5)* | `institutionId`, `panelIds` | `panelIds` removed | Stop sending `panelIds`. It is ignored now, not rejected. |
| S1 | `ValidationStatus` enum | `INCOMPLETE, COMPLETE, VALIDATED` | **+ `CANCELLED`** | Clients that deserialise into a closed enum must accept the new value. `validated` is now also exposed on all entity resources (see A-fields). |
| S2 | `RecordingUnitResource.answers` on **lists** (`/projects/{id}/recording-units`, `/recording-units`, `/phases/{id}/recording-units`, children) | Always present, always `{}` | **Absent** unless `?fields=all\|default\|<id,id,…>` is sent. When present, it holds **raw values** keyed by fieldId, **not** the `FieldAnswer` envelope | Don't rely on the key existing. Ask for `fields=` if you need values. On the **detail** endpoint (`GET /recording-units/{id}`) `answers` is still the `FieldAnswer` envelope, unchanged. |
| S3 | `FindResource.recordingUnit` | `RecordingUnitResourceIdentifier { resourceType, id }` | `RecordingUnitReference { resourceType, id, fullIdentifier? }` | Additive on the wire (`fullIdentifier` added, omitted when null). Only strict/closed schema validators break. |
| S4 | `FieldAnswer` polymorphism (`answerType` discriminator) | `TEXT`, `INTEGER`, `DATE`, … | **+ `DECIMAL`** (`DecimalFieldAnswer { answerType, field, value: number \| null }`) | Handle the new discriminator value. Treat unknown ones as a fallback. |

### Validation errors that used to be silent

These are also breaking for clients that sent sloppy parameters:

- **Unknown filter keys**: on every list that accepts `f.*` (projects, RUs, phases, finds, containers), an `f.<key>` that isn't a declared filter now returns **`400 Filtre inconnu : f.<key>`**. `f.<number>` (custom-field filters) is always accepted.
- Mixing a value filter and a range filter on the same key (`f.12=x&f.12.from=1`) returns `400`.
- Sending more than one value to a single-value or range filter returns `400`.
- `GET /api/v1/projects/{id}/recording-units`: `f.actionUnit` returns `400` (the path already scopes the project).

---

## 3. Additive changes

### 3.1 New endpoints

**Organisation-scoped lists.** These replace B1/B2 and add three entity types. All take `organizationId` (required; `400` if missing, `403` if outside the caller's scope), `offset`, `limit` (default 10), `search`, `sort` and `f.*` filters.

| Endpoint | Default sort | Response |
|---|---|---|
| `GET /api/v1/recording-units` | `creationTime:desc` | `RecordingUnitListResponse` |
| `GET /api/v1/places` | `name:asc` | `PlaceListResponse` |
| `GET /api/v1/finds` | `fullIdentifier:asc` | `FindListResponse` |
| `GET /api/v1/phases` | `orderNumber:asc` | `PhaseListResponse` |
| `GET /api/v1/containers` | `identifier:asc` | `ContainerListResponse` |

Items in these organisation lists carry a `project: ResourceRef` (owning project), which is omitted on project-scoped lists.

**Phases** (new resource family)

| Endpoint | Notes |
|---|---|
| `GET /api/v1/phases/{id}` | `PhaseResponse`. Numeric id. |
| `POST /api/v1/phases` | Body `PhaseCreateRequest { projectId*, typeId*, title }` → `201 PhaseResponse` |
| `PATCH /api/v1/phases/{id}` | Body `PhasePatchRequest { validated?, answers? }` |
| `GET /api/v1/phases/{id}/recording-units` | RUs of a phase. Same contract as the project RU list. |
| `GET /api/v1/phases/{id}/siblings` | Previous/next, see "Siblings" below |
| `GET /api/v1/projects/{id}/phase-types` | `ProjectPhaseTypeListResponse` (types + form bundle + identifier config) |

**Containers** (new resource family)

| Endpoint | Notes |
|---|---|
| `GET /api/v1/containers/{id}` | `ContainerResponse` |
| `POST /api/v1/containers` | `ContainerCreateRequest { projectId*, typeId* }` → `201`. Requires container edit rights. |
| `PATCH /api/v1/containers/{id}` | `ContainerPatchRequest { validated?, answers? }` |
| `GET /api/v1/containers/{id}/siblings` | |
| `GET /api/v1/projects/{id}/containers` | Paginated. Default sort `identifier:asc`. Supports `search`, `f.*`, `fields`. |
| `GET /api/v1/projects/{id}/container-types` | `ProjectContainerTypeListResponse` |

**Places**

| Endpoint | Notes |
|---|---|
| `GET /api/v1/places/{id}` | **Now implemented** (was `501 Not Implemented` and hidden). Includes `answers`, `formBundle`, `fields`, `_permissions` on detail. |
| `GET /api/v1/places/{id}/children` | Direct child places, same contract as `GET /api/v1/places` |
| `GET /api/v1/places/{id}/projects` | Projects whose spatial context contains the place. Same contract as `GET /api/v1/projects`. |
| `GET /api/v1/places/{id}/siblings` | Siblings within the organisation, by creation order |
| `POST /api/v1/places/{id}/duplicate` | Copies fields and parents (not children), named "name (n)" → `201 PlaceCreatedResponse`. `409` if no free name. |

**Recording units / finds / projects**

| Endpoint | Notes |
|---|---|
| `POST /api/v1/recording-units/{id}/duplicate` | `201 RecordingUnitResponse` |
| `GET /api/v1/recording-units/{id}/siblings` | |
| `GET /api/v1/finds/{id}/siblings` | |
| `GET /api/v1/projects/{id}/mobiliers` | **Now implemented** (was `501` and hidden). Paginated, `search`, `sort` (default `fullIdentifier:asc`), `f.*`. |
| `GET /api/v1/projects/{id}/siblings` | Previous/next project in the *same ordering* as `GET /api/v1/projects` (accepts the same `organizationId`, `search`, `sort`, `f.*`). `400` if the sort can't be used for navigation. |
| `GET /api/v1/projects/{id}/history` | Audit trail: `ProjectHistoryListResponse { data: [{ revisionDate, revisionType, author: { id, name, lastname } }] }` |

**Organisations**

| Endpoint | Notes |
|---|---|
| `GET /api/v1/organizations/{id}/counts` | `{ projects, places, recordingUnits, finds, phases, containers }` |
| `GET /api/v1/organizations/{id}/project-types` | Replaces `/projects/form` (see B3) |
| `GET /api/v1/organizations/{id}/concepts` | Organisation-scoped equivalent of `/projects/{id}/concepts`. `fieldCode` **or** `fieldId` (+ optional `projectId`, `valueConceptId`), `q` (suggestion mode), `limit` (50), `offset`. `400` if neither `fieldCode` nor `fieldId` is given. |

**Bookmarks** (new, tag "Favoris")

| Endpoint | Notes |
|---|---|
| `POST /api/v1/bookmarks` | `{ resourceUri*, titleCode, organizationId* }` → `201`. `resourceUri` is the *navigation* URI (`/action-unit/42`), not the REST path. |
| `DELETE /api/v1/bookmarks?resourceUri=&organizationId=` | `204`, idempotent |
| `GET /api/v1/bookmarks/status?resourceUri=&organizationId=` | `{ bookmarked: boolean }` |

**Siblings contract** (every `…/siblings` endpoint): `{ data: { previous: { id, label, resourceUri } | null, next: … | null } }`. The list wraps around, so the next of the last item is the first. A value is `null` only when there is no other item.

### 3.2 New query parameters on existing endpoints

| Endpoint | New params |
|---|---|
| `GET /api/v1/projects` | `sort` (fixed, see B6), `fields`, `canCreate` (only projects where the caller can create `recordingUnit` / …), `f.*` filters: `f.name`, `f.fullIdentifier`, `f.oaCode`, `f.scientificManager` (contains), `f.status` (concept id, repeatable), `f.periods`, `f.subjects`, `f.spatialContext` (concept ids, any-of), `f.mainLocation` (place id), `f.openingRate.from` / `.to` (numeric) |
| `GET /api/v1/projects/{id}` | `fields` |
| `GET /api/v1/projects/{id}/recording-units` | `search` (on `fullIdentifier`), `fields`, `f.*` filters: `f.fullIdentifier`, `f.matrixColor` (contains); `f.type`, `f.geomorphologicalCycle`, `f.geomorphologicalAgent`, `f.normalizedInterpretation`, `f.author`, `f.contributors`, `f.spatialUnit`, `f.parents`, `f.children` (id lists); `f.openingDate` / `f.closingDate` `.from` / `.to` (ISO dates); `f.tpq` / `f.taq` `.from` / `.to` (integers). Extra sort keys: `specimenCount`, `relationshipCount`, `parentsCount`, `childrenCount`, `*Label`. `Accept-Language` is now used for labels. |
| `GET /api/v1/recording-units/{id}/mobiliers` | `search`, `f.*` |
| `GET /api/v1/places/{id}` | `Accept-Language` |

**Generic custom-field filtering and sort.** This applies to every list that accepts `f.*`.

- `f.<fieldId>=value` (repeatable): value / in filter on a form field.
- `f.<fieldId>.from=` and `f.<fieldId>.to=`: range filter.
- `sort=<fieldId>:asc|desc`: sort on a form field. A multi-valued field sorts by its number of values.
- Whether a column supports this is advertised per field in `FieldResource.query { sortable, filterOp: contains|range|date-range|in|null }`.

**`fields` projection** (projects, RUs, phases, containers):
- `all`: every field.
- `default`: the default visible table columns.
- `3,17,-118`: the listed field ids.
- Absent: **no `answers` key**, same cost as before.

When present, `answers` holds raw values keyed by fieldId.

### 3.3 New request body fields

| Request | New field |
|---|---|
| `ProjectPatchRequest` | `validated: ValidationStatus`; `answers: { [fieldId]: { value } \| { values } }` merged after the flat fields. A missing key leaves the value untouched; `value: null` and `values: []` clear it; `values: null` leaves it untouched. |
| `RecordingUnitPatchRequest`, `PlacePatchRequest`, `FindPatchRequest` | `validated: ValidationStatus`. Absent means unchanged. `INCOMPLETE`/`COMPLETE`/`CANCELLED` need the edit right. Reaching **or leaving** `VALIDATED` needs the validator right (`_permissions.canValidate`), otherwise `403`. |
| `RecordingUnitCreateRequest` | `parentRecordingUnitId`, `childRecordingUnitId`: link the new RU as child/parent of an existing RU in the same project |
| `PlaceCreateRequest` | `parentPlaceId`, `childPlaceId`: same idea, within the same organisation |

### 3.4 New response fields (A-fields)

All of these are additive. Nullable fields are omitted when `null` wherever marked `NON_NULL`.

| Resource | New fields |
|---|---|
| `ProjectResource` | `validated`, `_permissions`, `bookmarked`, `resourceUri` (e.g. `/action-unit/42`), `answers` (only with `fields=`) |
| `ProjectResourceCounts` | `finds`, `phases`, `containers` |
| `ProjectResourceLinks` | `finds`, `phases`, `containers` (URLs) |
| `RecordingUnitResource` | `organization { resourceType, id }`, `project` (org lists only), `_permissions`, `resourceUri`, `bookmarked`, `validated` |
| `RecordingUnitResourceCounts` | `relationships` |
| `FindResource` | `projectId`, `project` (org lists only), `_permissions`, `resourceUri`, `bookmarked`, `validated`, `recordingUnit.fullIdentifier`. `answers` is the envelope on detail and raw values on lists. |
| `PhaseResource` | `projectId`, `project`, `organization`, `type`, `answers`, `_permissions`, `resourceUri`, `bookmarked`, `validated` (existing `id`, `identifier`, `title`, `label` unchanged) |
| `PlaceResource` | `answers`, `formBundle`, `fields`, `_permissions` (detail only), `resourceUri`, `bookmarked`, `validated` |
| `OrganizationResource` | `_permissions { canCreateProjects }` |
| `FieldResource` | `isTextArea`, `icon`, `conceptUri`, `constraints { min, max, showTime, unit }`, `query { sortable, filterOp }` |
| `RecordingUnitDefaultType` (`/projects/{id}/recording-unit-types` → `_default`) | `tableColumns: [{ columnId, fieldId, visible, order }]` |
| `ProjectRecordingUnitTypeListResponse` | `fields`: merged field catalogue across `_default` and every type |

`_permissions` (shared `ProjectResourcePermissions`):
- `canEdit`, `canDelete`: always present.
- `canManageSettings` (projects only), `canValidate` (detail responses): **omitted when false**.

On RU lists the value is computed once per page.

---

## 4. Behavioural changes (same signature)

### Sorting

| List | Allowed `sort` properties (`prop:asc\|desc`) | Unknown property |
|---|---|---|
| Projects (`/projects`, `/places/{id}/projects`, `/projects/{id}/siblings`) | `id`, `name`, `identifier`, `fullIdentifier`, `beginDate`, `endDate`, `creationTime`, `recordingUnitCount`, `<fieldId>` | **400** (was: ignored) |
| Recording units (all RU lists) | `creationTime`, `id`, `identifier`, `fullIdentifier`, `openingDate`, `closingDate`, plus every RU table column: `type`, `author`, `contributors`, `spatialUnit`, `matrixColor`, `geomorphologicalCycle`, `geomorphologicalAgent`, `normalizedInterpretation`, `tpq`, `taq`, `specimenCount`, `relationshipCount`, `parentsCount`, `childrenCount`, …, and `<fieldId>` | **400** (was: fallback to default) |
| Finds | `fullIdentifier`, `collectionDate`, `creationTime`, `id`, `<fieldId>` | **400** |
| Phases | `identifier`, `orderNumber`, `title`, `id`, `<fieldId>` | **400** |
| Containers | `identifier`, `id`, `<fieldId>` | **400** |
| Places, organizations | `id`, `name`, `code`, `creationTime` / organisation fields | fallback to default (unchanged) |

Every sorted list now adds a **stable `id:asc` tie-breaker**, so pages no longer overlap or skip rows when several items share the sort value. That changes the order of equal-valued items compared with `main`.

Pagination rules are unchanged: `offset` must still be a multiple of `limit`.

### Other

- **The API never touches the HTTP session any more.** `/api/v1/**` was already declared stateless, but session-fixation protection could still rotate a session cookie sent along with the request. It is now disabled for this chain. There is no effect for pure JWT clients.
- **Sort validation**: unknown sort properties on the new and updated lists return `400` rather than being ignored.
- **Labels respect `Accept-Language`** on RU/project lists and place detail (concept labels resolved in batch).
- `GET /api/v1/projects/{id}/recording-units`: the `400` response now also covers invalid sort or filter (previously pagination only).

### Examples

```http
# B1 — organisation RU list
GET /api/v1/organizations/10/recording-units?offset=0&limit=20            # main (now 404)
GET /api/v1/recording-units?organizationId=10&offset=0&limit=20           # now

# B2 — organisation places (note the default limit dropped from 50 to 10)
GET /api/v1/organizations/10/places?offset=0&limit=50&sort=name:asc       # main (now 404)
GET /api/v1/places?organizationId=10&offset=0&limit=50&sort=name:asc      # now

# B3 — project creation form
GET /api/v1/projects/form?organizationId=10                               # main (now 404)
GET /api/v1/organizations/10/project-types                                # now → _default.form.layoutJson + fields

# B4/B5 — lists that used to return everything
GET /api/v1/projects/OA-2024/phases                                       # main: all phases; now: first 10
GET /api/v1/projects/OA-2024/phases?offset=0&limit=100                    # explicit page size

# S2 — list values
GET /api/v1/projects/OA-2024/recording-units?fields=default               # adds answers { "<fieldId>": value }

# New filters (custom field 42 between two dates, type in {5, 7})
GET /api/v1/projects/OA-2024/recording-units?f.42.from=2024-01-01&f.42.to=2024-12-31&f.type=5&f.type=7
```

```jsonc
// PATCH /api/v1/recording-units/123 — validation status (needs _permissions.canValidate for VALIDATED)
{ "validated": "COMPLETE" }

// PATCH /api/v1/projects/OA-2024 — flat fields and form answers together
{ "name": "Fouille 2024", "answers": { "42": { "value": "2024-06-01" }, "17": { "values": ["5", "7"] } } }
```

---

## 5. Internal endpoints: ignore them

These exist for the SIAMOIS web UI embedding and aren't part of the public contract. They're hidden from the OpenAPI document or outside `/api/v1`.

| Endpoint | Purpose |
|---|---|
| `POST /api/auth/session-token` | Exchanges an authenticated **browser session** (cookie + CSRF) for a short-lived JWT. Not reachable with a JWT alone. |
| `GET /api/context/check` | UI context sync (see B7) |

---

## 6. Unchanged

Every other endpoint keeps its path, parameters and status codes. The only changes to them are the additive response fields in §3.4. This includes: auth (`/auth/login`, `/auth/me`), documents, concepts/vocabularies, users, `/projects/{id}/concepts|documents|field-codes|find-types|recording-unit-types|geopackage`, `/recording-units/{id}` (GET/PATCH/DELETE), stratigraphic relationships, parents/children link/unlink, finds CRUD and `/finds/form`, `/recording-units/creation-form`, `/places/autocomplete`, `/places/{id}/mobiliers|recording-units`, ARK resolver.
