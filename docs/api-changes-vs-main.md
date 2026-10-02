# SIAMOIS REST API — changes on `feat/main-panel-react-migration` vs `main`

**Scope:** public API under `/api/v1/**` (JWT-authenticated). First generated 2026-09-25 from a source diff of controllers, request/response DTOs and mappers; **updated 2026-10-01** on `feat/field-rules-engine` (see §7 for what changed since the first version).
**Audience:** external API consumers.

> Conventions used below: `{id}` for a project accepts the numeric id, the `fullIdentifier` or the short identifier (unchanged). All list endpoints keep the `offset` / `limit` pagination, the `meta: { total, limit, offset }` envelope and the `X-Total-Count` header unless stated otherwise.

---

## 1. Summary

| Kind | Count |
|---|---|
| Endpoints **removed** | 3 |
| Endpoints whose contract changed in a **breaking** way | 6 (plus 4 schema-level breaks) |
| Endpoints **added** | 43 |
| Existing endpoints with **additive** changes (new params/fields) | ~17 |

Things most likely to break an existing client, in order:

1. `GET /api/v1/organizations/{id}/recording-units` and `GET /api/v1/organizations/{id}/places` are **gone**. Use `GET /api/v1/recording-units?organizationId=` and `GET /api/v1/places?organizationId=`.
2. `GET /api/v1/projects/form` is **gone**. Use `GET /api/v1/organizations/{id}/project-types`.
3. `GET /api/v1/projects/{id}/phases` and `GET /api/v1/recording-units/{id}/children` are **now paginated**, defaulting to 10 and 200 items respectively. Previously they returned everything.
4. `ValidationStatus` has a new value, **`CANCELLED`**.
5. The `answers` key on list items (recording units) is **omitted** unless `?fields=` is sent. Previously it was always present as `{}`.
6. Project, recording-unit, find, phase and container lists now **reject unknown sort properties and unknown `f.*` filter keys with `400`**. On `main`, an unknown sort property was silently replaced by the default order.
7. A **multi-valued answer** is no longer a bare array (lists) nor always complete (details): it is `{ values, total, complete, _links? }`, cut to `valuesLimit` values (1 on lists, 50 on details). See S5.

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
| S4 | `FieldAnswer` polymorphism (`answerType` discriminator) | `TEXT`, `INTEGER`, `DATE`, … | **+ `DECIMAL`** (`DecimalFieldAnswer { answerType, field, value: number \| null }`), **+ `SELECT_MULTIPLE_STRATIGRAPHY`** (a `SelectManyFieldAnswer` whose values carry a `qualifier`) | Handle the new discriminator values. Treat unknown ones as a fallback. |
| S5 | Every **multi-valued answer** (`SELECT_MULTIPLE_*`), on every list and detail that serves `answers` (projects, RUs, finds, phases, containers) | Lists: a bare array of `ResourceRef`. Details: `SelectManyFieldAnswer { answerType, field, values }` with every value | Raw answers (lists; phase, container and project details): **`MultiValue { values, total, complete, _links? }`**. Envelope (RU and find details): `SelectManyFieldAnswer` gains the same `total`, `complete`, `_links`. `values` holds at most `valuesLimit` values (default **1** on lists, **50** on details), in the order of their label; `total` is how many there are; when `complete` is `false`, `_links.values` is the paginated list of all of them (`GET /api/v1/{collection}/{id}/fields/{fieldId}/values`). An unanswered multi-valued field is `{ values: [], total: 0, complete: true }`, never `null` on the envelope | Read `values` inside the object. **Never write back a list you read with `complete: false`**: a `values` PATCH replaces the whole list and would drop what you didn't see; use `add` / `remove` (§3.3). |

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
| `GET /api/v1/documents` | `identifier:asc` | `DocumentListResponse` |

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

**Values of one multi-valued answer.** The target of every incomplete answer's `_links.values`.

| Endpoint | Params | Response |
|---|---|---|
| `GET /api/v1/{collection}/{id}/fields/{fieldId}/values`, `collection` ∈ `recording-units`, `finds`, `phases`, `containers`, `projects` | `offset` (0), `limit` (default 50, max 200), `search` (label contains, case-insensitive), `sort` (`label:asc` default, `label:desc`) | `FieldValuesResponse { data: ResourceRef[], meta: { total, limit, offset } }` + `X-Total-Count`. Same access scope as the resource's detail. `400` if the field isn't multi-valued, `404` for an unknown field or resource. |

**Organisation column catalogues** (new 2026-10-01). Drive the column picker of the organisation lists.

| Endpoint | Notes |
|---|---|
| `GET /api/v1/organizations/{id}/recording-unit-types` | `OrganizationFieldCatalogResponse { data: [], _default: { fields, tableColumns? }, fields }`. `data` is always empty. `fields` = system fields of the detail form + the union of **active** additional fields of every project of the organisation, for the matching table. `403` if the organisation is out of scope. |
| `GET /api/v1/organizations/{id}/find-types` | Same, for finds |
| `GET /api/v1/organizations/{id}/phase-types` | Same, for phases |
| `GET /api/v1/organizations/{id}/container-types` | Same, for containers |
| `GET /api/v1/organizations/{id}/place-types` | Same shape. Places have no per-project configuration and no additional field: name, category, code and grouping number, with `query { sortable, filterOp }` on each. |

The `fields` ids returned here are the ones accepted by `fields=`, `sort=<fieldId>` and `f.<fieldId>` on the matching organisation list (`/recording-units`, `/finds`, `/phases`, `/containers`, `/places`).

**Duplication** (new 2026-10-01)

| Endpoint | Notes |
|---|---|
| `GET /api/v1/recording-units/{id}/structure` | `{ data: { root: {id, label, parentId}, descendants: [{id, label, parentId}], truncated } }`. The RU and all its descendants, flat, a parent always before its children. At most 500 nodes; `truncated: true` when cut. Same access scope as `GET /recording-units/{id}`. `404` if not found or out of scope. |
| `POST /api/v1/recording-units/{id}/duplicate-structure` | Body `{ copies?: 1..50 (default 1), descendantIds?: number[] }` → `201 { data: { copies: [{id, label}], createdCount } }`. The RU is always copied; each copy stays under the same parents as the original; a copied descendant is attached only to the copy of its parent; a descendant whose ancestor is not listed is ignored. All-or-nothing (one transaction), at most 500 RUs created (`400` beyond). Needs the same right as editing the source RU (`403`). `409` if a generated identifier is already used. |
| `POST /api/v1/finds/{id}/duplicate` | No body → `201 FindResponse`. Copies the descriptive data (type, category, RU, authors/collectors, collection date, materials, interpretation, dating, description, comments, element count, weight as a new measurement) on the same RU with a regenerated identifier. **Not copied:** id, own identifiers (other identifier, isolate number, which designate one specific find), parent/child links, containers, phases, and answers to additional fields. Same right as creating a find on that RU. |

`POST /api/v1/recording-units/{id}/duplicate` (simple copy) is unchanged. Additional-field answers are not copied by any duplication endpoint.

**Siblings contract** (every `…/siblings` endpoint): `{ data: { previous: { id, label, resourceUri } | null, next: … | null } }`. The list wraps around, so the next of the last item is the first. A value is `null` only when there is no other item.

**Documents.** A document is now a full entity (identifier, mandatory project, national common-base fields, links to UE / finds / places / phases / containers). New: `GET /api/v1/documents/{id}`, `GET /api/v1/documents/{id}/siblings`, `POST /api/v1/documents` (JSON: `projectId`, `categoryId`, `title`), `GET /api/v1/{projects|organizations}/{id}/document-types`. `PATCH /api/v1/documents/{id}` keeps its flat mobile body (`title`, `description`, `natureConceptId`, `scaleConceptId`, `formatConceptId`) and also takes `answers` / `validated` like the other entities; as soon as either is present the flat fields are ignored. `DELETE` and `PATCH` now require the document edit right on the document's project (`403` otherwise, it used to be any member of the organisation). `GET /api/v1/projects/{id}/documents` is unchanged without `limit` (every document, unpaged); with `limit` it is a paged list (`offset`, `sort`, `search`, `f.*`, `fields`). `DocumentResource` gains `identifier`, `label`, `projectId`, `type` (the category), `answers`, `_permissions`, `resourceUri`, `bookmarked`, `validated`, all omitted when empty.

**Document file.** New: `PUT /api/v1/documents/{id}/file` (multipart `file`; replaces the stored file, fills `sizeMb` and, when empty, `format`; `400` on a refused type or size, `403` without the edit right) and `DELETE /api/v1/documents/{id}/file` (the document and its external URL stay); both answer the document. `GET /api/v1/documents/{id}/file` takes `?download=true` (`attachment` instead of `inline`), serves the file under its own name rather than its storage code, and answers `404` for a document with no file. The form's new `FILE` field (id `-728`) is read-only in `answers`: `{fileName, mimeType, size}`, never written by a PATCH. The external URL (`-722`) must start with `http://` or `https://` (`400` otherwise). The `/content/...` URL of a stored file now follows the configured context path instead of a hard-coded `/siamois`.

**Documents tab of the other fiches.** New: `GET /api/v1/{finds|phases|containers|places}/{id}/documents` (paged: `offset`, `limit`, `sort` among `identifier`, `title`, `creationTime`, `id`, `search`, `f.*`, `fields`; each row carries `_permissions`, `resourceUri`, `bookmarked`) and `PUT` / `DELETE /api/v1/{recording-units|finds|phases|containers|places}/{id}/documents/{documentId}` (idempotent, `204`; `400` when the document and the entity are in different projects, `403` without the document edit right on the document's project; a place has no project, so any document of the organization can be linked to it). `GET /api/v1/recording-units/{id}/documents` is unchanged without `limit` (every document, unpaged, for the mobile); with `limit` it is the same paged list. `POST /api/v1/documents` accepts optional `recordingUnitIds`, `findIds`, `placeIds`, `phaseIds`, `containerIds` to create the document already linked (each entity must be in the document's project; `400` otherwise). The detail resources of finds, phases and containers gain `_counts.documents`; places' `_counts` gains `documents`; recording units' `_counts.documents` is filled when the detail is asked with `counts=documents`.

### 3.2 New query parameters on existing endpoints

| Endpoint | New params |
|---|---|
| `GET /api/v1/projects` | `sort` (fixed, see B6), `fields`, `canCreate` (only projects where the caller can create `recordingUnit` / …), `f.*` filters: `f.name`, `f.fullIdentifier`, `f.oaCode`, `f.scientificManager` (contains), `f.status` (concept id, repeatable), `f.periods`, `f.subjects`, `f.spatialContext` (concept ids, any-of), `f.mainLocation` (place id), `f.openingRate.from` / `.to` (numeric) |
| `GET /api/v1/projects/{id}` | `fields` |
| `GET /api/v1/projects/{id}/recording-units` | `search` (on `fullIdentifier`), `fields`, `f.*` filters: `f.fullIdentifier`, `f.matrixColor` (contains); `f.type`, `f.geomorphologicalCycle`, `f.geomorphologicalAgent`, `f.normalizedInterpretation`, `f.author`, `f.contributors`, `f.spatialUnit`, `f.parents`, `f.children` (id lists); `f.openingDate` / `f.closingDate` `.from` / `.to` (ISO dates); `f.tpq` / `f.taq` `.from` / `.to` (integers). Extra sort keys: `specimenCount`, `relationshipCount`, `parentsCount`, `childrenCount`, `*Label`. `Accept-Language` is now used for labels. |
| `GET /api/v1/recording-units/{id}/mobiliers` | `search`, `f.*` |
| `GET /api/v1/places/{id}` | `Accept-Language` |
| `GET /api/v1/places`, `GET /api/v1/places/{id}/children` | `fields` (projection, from `place-types`), `sort=<fieldId>:dir`, `f.<fieldId>` filters (contains), `search` on `name`. Address is a composite object with no list value: it is not sortable, filterable or projected. |
| `GET /api/v1/organizations/…` lists (`/recording-units`, `/finds`, `/phases`, `/containers`) | `fields=` takes the ids of the organisation catalogues above (§3.1) |

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

**`valuesLimit`** (every endpoint whose response carries `answers`: the lists above, and the project, RU, find, phase and container details and their POST/PATCH responses): how many values each multi-valued answer carries, `0`–`200`. Default `1` on lists, `50` on details. `0` sends only `total`. See S5.

### 3.3 New request body fields

| Request | New field |
|---|---|
| `ProjectPatchRequest` | `validated: ValidationStatus`; `answers: { [fieldId]: { value } \| { values } }` merged after the flat fields. A missing key leaves the value untouched; `value: null` and `values: []` clear it; `values: null` leaves it untouched. |
| `RecordingUnitPatchRequest`, `PlacePatchRequest`, `FindPatchRequest` | `validated: ValidationStatus`. Absent means unchanged. `INCOMPLETE`/`COMPLETE`/`CANCELLED` need the edit right. Reaching **or leaving** `VALIDATED` needs the validator right (`_permissions.canValidate`), otherwise `403`. |
| `AnswerInput` (every `answers` map: project, RU, find, phase and container PATCH) | `add`, `remove`: ids to add to / remove from a multi-valued field, leaving every other value alone (an id already there, or not there, is ignored). Cannot be combined with `values` (`400`); a scalar field rejects them (`400`). The only safe write after reading an incomplete answer. |
| `RecordingUnitCreateRequest` | `parentRecordingUnitId`, `childRecordingUnitId`: link the new RU as child/parent of an existing RU in the same project |
| `PlaceCreateRequest` | `parentPlaceId`, `childPlaceId`: same idea, within the same organisation |

### 3.4 New response fields (A-fields)

All of these are additive. Nullable fields are omitted when `null` wherever marked `NON_NULL`.

| Resource | New fields |
|---|---|
| `ProjectResource` | `validated`, `_permissions`, `bookmarked`, `resourceUri` (e.g. `/action-unit/42`), `answers` (only with `fields=`) |
| `ProjectResourceCounts` | `finds`, `phases`, `containers`, `documents` |
| `ProjectResourceLinks` | `finds`, `phases`, `containers` (URLs) |
| `RecordingUnitResource` | `organization { resourceType, id }`, `project` (org lists only), `_permissions`, `resourceUri`, `bookmarked`, `validated` |
| `RecordingUnitResourceCounts` | `relationships` |
| `FindResource` | `projectId`, `project` (org lists only), `_permissions`, `resourceUri`, `bookmarked`, `validated`, `recordingUnit.fullIdentifier`. `answers` is the envelope on detail and raw values on lists. |
| `PhaseResource` | `projectId`, `project`, `organization`, `type`, `answers`, `_permissions`, `resourceUri`, `bookmarked`, `validated` (existing `id`, `identifier`, `title`, `label` unchanged) |
| `PlaceResource` | `answers`, `formBundle`, `fields`, `_permissions` (detail only), `resourceUri`, `bookmarked`, `validated` |
| `OrganizationResource` | `_permissions { canCreateProjects }` |
| `FieldResource` | `isTextArea`, `icon`, `conceptUri`, `constraints { min, max, showTime, unit }`, `query { sortable, filterOp }`, `readOnly` |
| `ResourceRef` | `href`: the referenced resource's detail URL (`/api/v1/recording-units/12`), absent for a type with no detail endpoint (persons, action codes). `qualifier` (stratigraphic relationships only): `{ concept: ResourceRef, role: unit1\|unit2, position: anterior\|posterior\|synchronous, conceptDirection, asynchronous, uncertain }` |
| New system fields | RU: **`-326` Mobilier** (`SELECT_MULTIPLE_SPECIMEN`, binding `specimenList`), **`-327` Relations stratigraphiques** (`SELECT_MULTIPLE_STRATIGRAPHY`). Phase: **`-511` Unités d'enregistrement** (`SELECT_MULTIPLE_RECORDING_UNIT`). Container: **`-609` Mobilier** (`SELECT_MULTIPLE_SPECIMEN`). All read-only (`FieldResource.readOnly`), sortable by number of values and filterable (`in`). The RU parents (`-319`) and children (`-320`) are now projected on lists too (preview + total) and are default columns, as are `-326` and `-327`. |
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
| Finds | `fullIdentifier`, `collectionDate`, `creationTime`, `id`, `<fieldId>` (`creationTime` is also the default of `/recording-units/{id}/mobiliers`, which used to answer `400` once an `f.*` filter was set) | **400** |
| Phases | `identifier`, `orderNumber`, `title`, `id`, `<fieldId>` | **400** |
| Containers | `identifier`, `id`, `<fieldId>` | **400** |
| Places | `id`, `name`, `code`, `creationTime`, `<fieldId>` (ids from `place-types`) | fallback to default (unchanged) |
| Organizations | `id`, `name`, `code`, `creationTime` / organisation fields | fallback to default (unchanged) |

`zInf` / `zSup` (RU altitudes, links to a measurement) sort and filter as numbers on the normalised value; a RU without altitude is **not** dropped by the sort (left join), and empty values sort last. `chronologicalPhase` sorts on the entity attribute. No catalogue declares a non-sortable field any more.

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

---

## 7. What changed since the first version (2026-09-25 → 2026-10-01)

For clients that read the first version of this document.

**Added**
- Organisation column catalogues: `GET /organizations/{id}/{recording-unit|find|phase|container|place}-types` (§3.1).
- `GET /recording-units/{id}/structure`, `POST /recording-units/{id}/duplicate-structure`, `POST /finds/{id}/duplicate` (§3.1).
- Places: `fields`, `sort=<fieldId>`, `f.<fieldId>` on `GET /places` and `GET /places/{id}/children` (§3.2).

**Changed**
- Finds: `creationTime` accepted as sort (fixes a `400` on `/recording-units/{id}/mobiliers` with `f.*`).
- Numeric altitudes (`zInf`, `zSup`) and `chronologicalPhase` are sortable and filterable.
- Multi-valued fields are `MultiValue` / `SelectManyFieldAnswer` with `valuesLimit` (S5, unchanged since the first version).

**Removed or breaking:** nothing beyond B1–B7 and S1–S5 above.

**Known limits worth telling consumers**
- A list's `answers` carry at most `valuesLimit` values per multi-valued field. Never write back a list you read with `complete: false`; use `add` / `remove`.
- Sorting or filtering on an additional field is a correlated sub-query per row: measured at about 460 ms for 300 000 recording units. Fine today, to be re-measured at 10× that volume.
- `offset` must still be a multiple of `limit`.
- A fixed cost of about 430 table scans per page is not yet analysed (only matters if latency becomes an issue).
