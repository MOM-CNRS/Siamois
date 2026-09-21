# React/PrimeReact migration of the main panel area — starting with Project

> **Saved to the repo 2026-09-21** from the working plan at
> `~/.claude/plans/okey-bring-back-this-idempotent-torvalds.md`, so it survives a context clear.
> Branch: `feat/main-panel-react-migration`.
>
> **Status as of 2026-09-21** (this section is not part of the original plan — added so the doc
> stays useful after a context reset):
> - Phases 1–8 (§8 below) are implemented: scaffold, generic `EntityListPanel`/`EntityDetailPanel`/
>   `HomePanel`, the Project API prep, `entities/project/config.tsx`, List, Detail (fiche tab),
>   Home, and the JSF integration (`isReactPanelEnabled()`, `focus.xhtml` branch, `ActionUnitController`
>   routing directly to `focus.xhtml`, client-side navigation in `App.tsx`).
> - Several corrections were made **after** phase 8 that this plan predates and does not describe:
>   - Routing goes through `focus.xhtml` directly (`ActionUnitController` builds a `forward:/pages/
>     focus.xhtml?main=<base64 path>` token) — **not** through `flow.xhtml`, which really is dead,
>     confirmed by testing the live app rather than just reading the plan's own claim.
>   - Client-side navigation: `App.tsx` owns a router (`history.pushState`, no page reload) —
>     clicking a list row or a Home widget link swaps panels via JS only, matching the whole point
>     of the migration (no JSF round-trip per click). The old `onNavigate`-does-`window.location.href`
>     approach was wrong and removed.
>   - PrimeReact component fidelity: two follow-up passes were needed after Home widgets and the
>     panel toolbar first shipped as unstyled plain HTML — every panel now uses real PrimeReact
>     components (`Panel`, `Chip`, `Toolbar`, `Button`, etc.), audited against the actual `.xhtml`
>     templates, not guessed. **This is now a standing rule, added to §3 below.**
>   - Panel headers: the generic toolbar (bookmark/duplicate/refresh/create/settings) is **part of
>     each panel's own header**, not a strip rendered above the panel by the caller. A shared
>     `PanelHeaderBar` component (title content + toolbar, one node) is passed as each PrimeReact
>     `<Panel>`'s `header` prop — not PrimeReact's separate `icons` slot, which is the wrong piece
>     of that component's API for this (see `frontend/src/components/PanelHeaderBar.tsx`).
>   - `EntityDetailPanel` has an actual title (icon + entity singular label), matching
>     `EntityListPanel`'s icon+label header, in front of the entity-specific header content
>     (identifier/type/name/location chips, via the new `config.detail.header` slot).
>   - `HomePanel` is **not** one wrapping "Accueil" panel — `homePanel.xhtml` is actually a title
>     plus two separate sibling `p:panel`s ("Mes derniers projets" and "Accéder aux bases de
>     données"). `HomeWidgetDef` now has a `kind: "panel" | "card"` discriminator: `"panel"`
>     widgets render standalone (already self-wrapped in their own `<Panel>`), `"card"` widgets are
>     grouped into one shared "Accéder aux bases de données" panel/grid.
> - Not yet done: phase 9 (automated Java-side tests for this round of JSF template changes have
>   not been re-run since the `reactAction_listCreate_` remoteCommand was added — low risk, no Java
>   files touched, but unconfirmed), phase 10 (manual verification), phase 11 (cleanup notes).
> - Known, deliberate gaps (not oversights): category-chip inline editing (needs a concept-
>   autocomplete API that doesn't exist yet), prev/next navigation, and the Validate toggle — all
>   three need backend work with no REST endpoint today.

---

> **Restored 2026-09-20** from `~/.claude/plans/swirling-sparking-token.md`. Re-verified against `main` @ `41eb6d879`:
> - Templates confirmed at `src/main/resources/META-INF/resources/{pages/focus.xhtml,panel/panelContent.xhtml,panel/homePanel.xhtml}` (the earlier plan wrote these as `src/main/webapp/...` — wrong prefix, corrected throughout).
> - The PATCH/DELETE permission bug is still live: `ProjectApiService.java:227` and `:272` both call `hasOrganizationPermission(..., ORGANIZATION_MANAGE_ACTIONS)`.
> - `frontend/` on disk is untracked and contains **only stale RU build artifacts** (`dist/recording-unit-panel.*`, `tsconfig.tsbuildinfo`) — no source, nothing started. Safe to overwrite with the fresh scaffold.

## Context

Siamois's UI below the top/search bar is 100% JSF/PrimeFaces (Mojarra), driven by a session-scoped "open panels" stack (`FlowBean`) rendering `AbstractPanel` subclasses. Small field edits are already slow on the *side/overview* panel due to Mojarra full-state restore — that problem was addressed for the Recording Unit side panel on branch `feat/ru-react-panel-phase1` (unmerged, 2 commits ahead of `main`; `main` has no frontend source). This task is a *different, wider* surface: replace the **main panel content area** (Home, entity List, entity Detail) with a **generic, entity-agnostic** React/PrimeReact infrastructure, first wired up for **Project** (domain entity: `ActionUnit`), so Recording Unit, Spatial Unit, Furniture, Place, etc. can plug into the same infra later without rearchitecting.

Two scope decisions made with the user before finalizing:
- **No project map in phase 1.** Confirmed: no map library (Leaflet/OpenLayers/Mapbox) or map endpoint exists anywhere today — only raw `geom` on `ProjectResource`. Nothing to preserve; a map would be new functionality. Deferred entirely.
- **Fresh scaffold on `main`.** Rather than depend on the unmerged `feat/ru-react-panel-phase1`, this work sets up its own `frontend/` on `main`. (Reconciliation risk — see §9.)

---

## 1. Current architecture (as found)

**Terminology**: "Project" in the UI = domain entity `ActionUnit`. There is no `ProjectPanel`; it's `ActionUnitPanel`/`ActionUnitListPanel`. The unrelated `ProjectListBean` under Settings (institution admin CRUD) is not in scope.

**Panel/navigation architecture:**
- `flow.xhtml` (multi-panel tab-stack template) is **dead/unused in practice** — out of scope, don't design around it. It remains a forward target in several redirection controllers, but the live rendered entry point is `pages/focus.xhtml`.
- **`pages/focus.xhtml` + `FocusViewBean`** (`ui/bean/FocusViewBean.java`) is the real single-panel route (`f:viewParam main/s/back`). It renders its own header toolbar directly (bookmark/duplicate/refresh/create/settings/close, ~lines 193–307) around `focusViewBean.mainPanel`, then includes `panel/panelContent.xhtml` for the body.
- **`panelContent.xhtml`** — a `p:splitter` with two `p:splitterPanel`s: left = `panelModel.display()` (`panel-splitter-panel-l`, *the main panel*), right = `panelModel.parentOrOverview.display()` (`panel-splitter-panel-r`) inside a `sideview`/`sideview-titlebar` block with its own toolbar (close-overview, fullscreen, bookmark, duplicate, refresh, settings). **The same `AbstractPanel.display()` mechanism drives both panes** — no separate class hierarchy for main vs overview.
- `FlowBean` (`ui/bean/panel/FlowBean.java`, session-scoped) is **still live** as the panel-object registry/lifecycle owner (`fullscreenPanelIndex`, `isWriteMode`, `fullScreen(panel)`, `closePanel`, `addPanelToOverview`) even though its own template isn't rendered. `PanelFactory` (`ui/bean/panel/PanelFactory.java`) instantiates prototype-scoped panels per entity type, used by both `FlowBean` and `FocusViewBean`.
- Redirection controllers (`ui/redirection/{WelcomeController,ActionUnitController,RecordingUnitController,...}`) map plain URLs (`GET /action-unit/{id}`, `GET /welcome`) into the panel machinery.

**Practical effect**: there is no multi-open-panel tab UI to preserve — at any time there's one main panel plus optionally one overview panel, side by side in a resizable splitter. A much simpler target for React to own wholesale.

**Existing generic abstractions (the genericity level to match/exceed in React):**

| Concern | Base class | Project impl |
|---|---|---|
| Any open panel | `AbstractPanel` | — |
| Entity list | `AbstractListPanel<T extends AbstractEntityDTO>` (`ui/bean/panel/models/panel/list/`) | `ActionUnitListPanel` + `ActionUnitTableDefinitionFactory` + `ActionUnitTableViewModel` + a `BaseLazyDataModel<T>` subclass (server-side pagination/sort/filter) |
| Entity detail | `AbstractSingleEntity<T>` → `AbstractSingleEntityPanel<T>` (`ui/bean/panel/models/panel/single/`) | `ActionUnitPanel` — dynamic tabs (`recordingTab`, `containerTab`, `phaseTab`, + Documents [excluded]); identifier inline-edit, revision history, prev/next, validate toggle all inherited |
| Home | `WelcomePanel` (extends `AbstractPanel`) | `panel/homePanel.xhtml` — card grid, per-entity counts, "see list"/"create" via `NavBean` |

**Templates** (all under `src/main/resources/META-INF/resources/`): `panel/actionUnitListPanel.xhtml` + `panel/header/actionUnitListPanelHeader.xhtml`; `panel/tabview/actionUnitTabView.xhtml` (generic `p:tabView` over `panelModel.tabs`); `panel/homePanel.xhtml` + `panel/header/homePanelHeader.xhtml`. Shared table chrome: `pages/shared/table/{tableToolbar,columnTemplates,filterTemplate}.xhtml`.

**REST API already under `/api/v1`** (JWT, stateless chain, separate from the JSF session chain):
- `GET /api/v1/projects` — `offset`, `limit` (def. 20), `organizationId`, `search`, `sort` (`name:asc`) → `ProjectListResponse{data: ProjectResource[], meta{totalCount,limit,offset}}` + `X-Total-Count`. Backed by `ProjectApiService.pageAccessibleProjects`.
- `GET /api/v1/projects/{id}` → `ProjectResponse{data: ProjectResource}`.
- `POST /api/v1/projects`, `PATCH /api/v1/projects/{id}`, `DELETE /api/v1/projects/{id}` (409 if it has children/recording units).
- `GET /api/v1/projects/form?organizationId=` → `{form: FormResource, fields: Map<String,FieldResource>}` — already field-configuration-shaped (id/label/answerType/hint/isSystemField/valueBinding/fieldCode), just a map with no explicit order.
- Sub-resources: `.../phases`, `.../recording-unit-types`, `.../find-types`, `ProjectConceptsControllerApi`, `ProjectRecordingUnitsControllerApi`, `ProjectDocumentsControllerApi` (exist; verify exact shapes per-endpoint during implementation).
- Stubbed `501`: `GET /api/v1/projects/{id}/geopackage`, `GET /api/v1/organizations/{id}/projects`.
- `ProjectResource` fields: `resourceType, id, name, fullIdentifier, identifier, beginDate, endDate, type, mainLocation, spatialContext, organization, geom, _counts{children,recordingUnits}, _links{self,recordingUnits,children}`.
- Permissions: manual `ProfilePermissionService` calls inside `ProjectApiService` (no `@PreAuthorize`). **Confirmed bug** — see §5.
- **No optimistic locking on PATCH**: `ProjectPatchRequest` has no revision/version field (checked `ActionUnit`/`ActionUnitDTO` too), unlike RU's `expectedRevision`/`syncRevision`. **Decided: leave it** — revision sync is reserved for the offline mobile app. Last-write-wins, matching current behavior.

**Project form configuration — "organization-level" but hardcoded, not stored:**
- RU/Mobilier/Phase/Container go through `ConfigurableTable` (`UE, MOBILIER, PHASE, CONTENANT` — **no Project entry**) + `EffectiveFormResolver`, a real per-institution-configurable pipeline.
- Project instead: `ActionUnit.NEW_UNIT_FORM = ActionUnitNewForm.build()` / `DETAILS_FORM = ActionUnitDetailsForm.build()` — Java-code constants, identical for every organization. `RecordingUnitOpenApiService.buildProjectUiForm/buildProjectFormBundle` just serializes them.
- So "organization-level" today means *one global form*, not *configurable per organization*. No backend storage work this phase — only a response-shape change (§5–6).

---

## 2. Migration boundary

- **Stays JSF**: top/search bar, `focus.xhtml`'s outer shell + `f:viewParam` routing, `FlowBean`/`FocusViewBean`/`PanelFactory` lifecycle, redirection controllers, Settings pages, Documents tab, Stratigraphy tab.
- **Becomes React** (Project only, this phase): the **entire panel body** rendered by `panelContent.xhtml` — both panes, in a resizable two-pane splitter (§3) — for three panel kinds: Home, List, Detail, gated on the main panel's entity type (§7, incl. the overview-pane fallback rule). Every other entity type keeps its JSF rendering unchanged.
- **Explicitly excluded**: Documents tab, Stratigraphy tab, Project map, the Settings-level form *configuration UI* (only the read API is in scope), per-project custom field configuration.
- **Included Project functionality**: list (search/sort/pagination/filter, columns, row actions, create — **flat table only**), detail (**fiche tab only** — identifier inline-edit, validate toggle, revision history, prev/next), Home (**recent/my projects + the Project card only**) minus the map, plus the panel toolbar actions (bookmark/duplicate/refresh/create/settings/close-overview/fullscreen) — bookmark via REST rather than the JSF bridge (§7.3).
- **Deferred to a second pass**: the list's **tree/hierarchical view** (`ActionUnitTableViewModel.isTreeViewSupported() = true`, PrimeFaces `LazyTreeTable`). Phase 1 ships the flat table only; `EntityListPanel` has no tree mode. Needed later: a `rootOnly` param on `GET /api/v1/projects` (mirroring `BaseLazyDataModel.rootOnly`, same search/sort/paginate pipeline) and `GET /api/v1/projects/{id}/children` (URL already stubbed in `ProjectResourceLinks.children`, no controller behind it).

---

## 3. React architecture

**New scaffold** at `frontend/` on `main` (currently stale artifacts only — safe to overwrite):
- Vite + React 18 + TypeScript + PrimeReact (same stack/versions as the RU branch, for eventual reconciliation).
- `frontend-maven-plugin` + `maven-resources-plugin` in `pom.xml`, output to `META-INF/resources/resources/react-main-panel/` (distinct from the RU branch's `react-ru-panel/`, so both can coexist once merged).
- Single Vite entry `src/mount.ts` exposing `window.SiamoisMainPanel = { mount, unmount }` — one generic mount parameterized by panel kind + entity type, not one per entity.

**React owns the whole splitter, not just the main pane.** The mount replaces `panelContent.xhtml`'s entire body: PrimeReact's `Splitter`/`SplitterPanel` (`primereact/splitter`) is the direct equivalent of `p:splitter`/`p:splitterPanel`, and both panes are driven by the same generic infra — left = main panel's entity, right = `parentOrOverview`'s entity when open.

- **Class-name preservation is a general rule, not a shortlist**: keep the **same DOM structure and custom class names** as the current JSF markup *everywhere*, not just the ones already spotted (`panel-splitter-panel-l`/`-r`, `sideview`, `sideview-titlebar`, `sideview-topbar-button`, `panel-docked`). The **only** intentional structural deviation is the splitter. PrimeFaces's and PrimeReact's own generated classes (`ui-*` vs `p-*`) necessarily differ. Reason: makes a *future* theme pass cheap — re-point existing CSS instead of rewriting it. **Action**: audit the real custom classes on `panelContent.xhtml`, `focus.xhtml`'s toolbar, `homePanel.xhtml`, `actionUnitListPanel.xhtml`, `actionUnitTabView.xhtml` and their header partials before building each component — don't guess.
- **Theming stays out of scope**: PrimeReact stock theme (`lara-light-blue`), no `--siamois-*` mapping. The visual mismatch with surrounding JSF chrome is accepted for now.
- **"No theming" does not mean "no PrimeReact components."** Every new piece of UI must use PrimeReact's actual component for whatever PrimeFaces component the JSF markup uses — `p:card`→`Card`, `p:panel`→`Panel`, `p:chip`→`Chip`, `p:commandButton`→`Button`, `p:splitter`→`Splitter`, etc. — not plain HTML (`<div>`, `<ul>`, `<button>`, `<a>`) with the legacy class name attached and nothing else. A legacy JSF class name on a plain element renders as unstyled text, since PrimeReact's stock theme targets its own components' generated classes (`p-*`), not PrimeFaces's (`ui-*`)/custom ones. **Practice going forward, learned from phase 7/8's Home widgets and toolbar shipping as unstyled HTML the first time**: before building any new panel/widget, find the real PrimeFaces component(s) it currently uses (audit the actual `.xhtml`, same as the class-name-preservation rule already requires) and use their direct PrimeReact counterpart, reproducing the same structure (header/body/footer, icon+label+badge rows, centered footer buttons, etc.) — not a freehand re-design. The legacy class name still rides along via `className` for the eventual theme pass; PrimeReact's own classes are what actually renders today.
- **Panel headers own their toolbar** (added after phase 8, see status note above): every panel (Home/List/Detail) is a PrimeReact `<Panel>`; its `header` prop is a single `PanelHeaderBar` combining that panel's own title content (icon/label/identifying chips) with the generic toolbar (bookmark/duplicate/refresh/create/settings) — one node, matching the real markup's single `sideview-titlebar` div. Never split across `<Panel>`'s `header`/`icons` props, and never rendered as a separate strip by the caller above the panel.
- **Overview-pane fallback rule**: the right pane renders in React only when its entity type is *also* in the registry. If a Project's overview holds an unmigrated type, the whole panel instance falls back to legacy `panelContent.xhtml` rendering rather than a hybrid render.

**Auth/API layer** — re-implement the pattern proven on the RU branch (small, self-contained, no branch dependency): `api/basePath.ts` (context path from JSF), `api/client.ts` (`apiFetch<T>`, bearer token, 401→refresh), `auth/sessionAuth.ts` + `auth/tokenStore.ts` (reusing the **already-generic** `SessionAuthController`/`AuthService.sessionToken()` backend as-is).

**Entity abstraction — registry/config pattern** (avoids `if (entityType === ...)` branching):

```ts
interface EntityTypeConfig<TSummary, TDetail> {
  key: string;                     // "project" | "recordingUnit" | ...
  labels: { singular: string; plural: string };
  api: {
    list(params: ListParams): Promise<PagedResult<TSummary>>;
    get(id: string | number): Promise<TDetail>;
    formConfig?(orgId: number): Promise<FieldConfigResponse>;
  };
  list: { columns: ColumnDef<TSummary>[]; defaultSort?: string; searchable: boolean };
  detail: { tabs: DetailTabDef<TDetail>[] };
  routes: { list: string; detail: (id) => string };
}
```

Permission flags are **not** in this config — they're read straight off each response's `_permissions` (`ProjectResource._permissions.{canEdit,canDelete}`, `OrganizationResource._permissions.canCreateProjects`, §5). No client-side permission model to keep in sync with the server's.

- `entities/registry.ts` — `Map<string, EntityTypeConfig>`, one module per entity (`entities/project/config.tsx` for phase 1).
- Generic components consume only the registry + a field-renderer registry (`fields/registry.tsx`, the "answerType → component" pattern validated on the RU branch's `fieldRegistry.tsx`), extended with a `valueBinding` resolver that reads/writes either a fixed `TDetail` property path (Project: `name`, `identifier`, `beginDate`, …) or a dynamic answer-map key (future RU case) — matching how `FieldResource.valueBinding` already models this.
- Generic panels: `<EntityListPanel entityType="project" />`, `<EntityDetailPanel entityType="project" entityId={id} />`, `<HomePanel />` (widget-slot based, `widgets: HomeWidgetDef[]`, so a map widget can be added later without touching `HomePanel`).

**State/data**: `@tanstack/react-query` for list/detail caching, pagination and mutations shared across entity types — the RU branch didn't need it (single entity, thin hooks), but a registry serving N types benefits from one consistent caching/loading layer. **No router library**: panel identity/open-close stays server-owned by `FlowBean`; React gets `{panelKind, entityType, entityId}` at mount plus an `onNavigate(entityType, id)` callback bridged back to JSF, reusing existing redirection-controller URLs (`/action-unit/{id}`) as plain navigations. **Note (see status above): this last point was superseded** — `App.tsx` now does client-side navigation itself via `history.pushState`, not a page navigation, once the target entity type is in the registry.

---

## 4. Project implementation

- **Home**: `<HomePanel />` renders the whole Home body, with exactly **two widgets** in phase 1 — (1) **recent/my projects** (reuses `GET /api/v1/projects?organizationId=…`; check whether the API supports a "mine"/recency filter, else filter client-side the way `WelcomePanel.myActionUnits` does today), and (2) the **Project card** (count + "see list"/"create", the latter gated by `OrganizationResource._permissions.canCreateProjects`). The other entity types' count cards in `homePanel.xhtml` (Spatial Unit, Recording Unit, Specimen, Phase, Container) are **deliberately not carried over** — they return as widgets as those entities migrate. No map widget.
- **List**: `<EntityListPanel entityType="project" />`; `entities/project/config.tsx` supplies columns (mirroring `ActionUnitTableDefinitionFactory`), search (`search` param), sort, pagination via `GET /api/v1/projects`. Row click → URL navigation. "Create" gated by `OrganizationResource._permissions.canCreateProjects`; row edit/delete gated by each row's `ProjectResource._permissions`.
- **Detail — fiche tab only**: `<EntityDetailPanel entityType="project" entityId={id} />` renders only the "Détails"/fiche tab, schema-driven from the new form-configuration API (§5), plus identifier inline-edit, validate toggle and revision history (part of the fiche header, not separate tabs). Relationship tabs (recording-units/containers/phases), Documents and Stratigraphy are **not rendered at all** this phase — not even stubs. `EntityDetailPanel`'s tab mechanism stays generic for when they're picked up.
- **Generic vs Project-specific**: `EntityListPanel`, `EntityDetailPanel`, `HomePanel`, the field-renderer registry and the registry mechanism are entity-agnostic. Only `entities/project/config.tsx` (+ its column/tab defs) is Project-specific — that split is the reusable-infra deliverable.

---

## 5. API changes

| API | Current state | Action |
|---|---|---|
| `GET /api/v1/projects` | Exists, paginated/sorted/searched | Reuse as-is; verify `search` covers what the JSF filter row supports (name/identifier) |
| `GET /api/v1/projects/{id}` | Exists | Reuse as-is |
| `PATCH /api/v1/projects/{id}` | Exists | Reuse endpoint, **fix its permission check** (below) |
| `POST /api/v1/projects` | Exists (`hasActionUnitCreatePermission`, line 164) | Reuse as-is |
| `GET /api/v1/projects/form` | Returns `{form, fields: Map}`; **verified zero in-repo consumers** beyond its own controller/service/response classes | **Fully superseded and removed** — `project-types` now serves form/layout + fields + fieldConfigs together. (An external/mobile client can't be ruled out from the repo alone.) |
| `GET /api/v1/organizations/{id}/project-types` (**new**) | Doesn't exist — modeled on `GET /api/v1/projects/{id}/recording-unit-types` (`data[]` + sibling `_default`), but **organization-scoped** | **New, carries everything the fiche needs**: `data: []` (empty for now), `_default: { form: { layoutJson }, fieldConfigs: [...] }`, plus a root-level shared `fields` catalog (§6). `form`/layout lives *inside* each type entry (and `_default`), not at the root |
| Project history/revision | Not confirmed for Project (exists for RU: `GET /api/v1/recording-units/{id}/history`) | **Verify**; if absent add `GET /api/v1/projects/{id}/history` mirroring it (`HistoryAuditService` is already entity-agnostic) |
| `.../recording-units`, `.../phases`, `.../containers` | Exist in some form | **No action** — relationship tabs out of scope this phase |
| `PATCH`/`DELETE` permission check | **Confirmed bug, still live**: `ProjectApiService.java:227` and `:272` only check `hasOrganizationPermission(ORGANIZATION_MANAGE_ACTIONS)`, unlike JSF's gate (`ActionUnitPanel.java:160`, `hasActionUnitWritePermission`) which also allows project-scoped `PROJECT_MANAGE_SETTINGS`. A project-scoped manager gets 403 from the API today but can edit in JSF. **User confirmed the REST wiring was never finished — JSF's bean logic is the source of truth.** | **Fix**: switch both to `hasActionUnitWritePermission(userInfo, project)` |
| `ProjectResource._permissions` (**new**) | No REST resource exposes a permissions block today — new pattern | **New**: `{ canEdit, canDelete }`, both via `hasActionUnitWritePermission` (identical values — no separate delete-permission concept exists, JSF included). List rows batched per-page via `ProfilePermissionService.actionUnitIdsWithPermission` (mirrors `EntityTableViewModel.canEditByActionUnit`, avoids N+1); detail = one direct check |
| `OrganizationResource._permissions` (**new**) | Doesn't exist; `GET /api/v1/organizations` works, `GET /api/v1/organizations/{id}` is `501` | **New**: `{ canCreateProjects }` via a new `(PersonDTO, InstitutionDTO)` overload of `hasActionUnitCreatePermission` (today only a `UserInfo`-based form), mirroring how `hasOrganizationPermission` already has both forms; batched across a list page via unioned `institutionIdsWithOrganizationPermission` calls, same principle as `institutionIdsPersonCanAccess` |
| `.../geopackage`, `GET /api/v1/organizations/{id}/projects` | Stubbed `501` | **No action** |
| `POST /api/auth/session-token` | Exists, generic | Reuse as-is |
| Bookmark create/delete | No REST endpoint — `BookmarkService.save`/`deleteBookmark(userInfo, resourceUri, titleCode)` only called from session-scoped JSF beans | **New, generic**: `POST /api/v1/bookmarks {resourceUri, titleCode}`, `DELETE /api/v1/bookmarks?resourceUri=…` — thin wrappers, no new persistence logic, no toggle endpoint (the client always knows current state). `resourceUri` for Project is the exact string JSF uses: `/action-unit/{id}` (`ActionUnitPanel.entityRessourceUri()`), **not** the REST path (`_links.self` differs) |
| `ProjectResource.bookmarked` (**new**) | Doesn't exist; status only checked JSF-side via `BookmarkService.isRessourceBookmarkedByUser` (single-URI, no bulk variant) | **New**: `bookmarked: boolean` on list and detail. Detail = one check. List = **needs a new bulk method on `BookmarkService`** (e.g. `findBookmarkedResourceUris(userInfo, Collection<String>)`), mirroring the `actionUnitIdsWithPermission` batching — otherwise N+1 per page |

---

## 6. Data schemas

The "field vs field-configuration" split mirrors the real backend model already used for UE/Mobilier/Phase/Container:
- `CustomField` (name/type/binding) is genuinely **shared/reusable** — FK-referenced by many per-type configs.
- `FieldFormConfig` (`fr.siamois.domain.models.form.config.FieldFormConfig`) = `CustomField` + `FormConfig` + `isActive`/`isMandatory`/`isInstitutionLocked`/`position` — genuinely **per-type**, a join row scoped to one `FormConfig`.

So: shared catalog at the root, per-type configs nested under each type (including `_default`). Nesting costs nothing while `data[]` is empty and avoids a breaking reshape the day a second real project type exists.

`GET /api/v1/organizations/{id}/project-types` — **one call returns layout + fields + configs**:
```json
{
  "data": [ ],
  "_default": {
    "form": { "resourceType": "forms", "layoutJson": "…" },
    "fieldConfigs": [
      { "field": "3", "active": true, "institutionLocked": true },
      { "field": "4", "active": true, "institutionLocked": true }
    ]
  },
  "fields": {
    "3": { "id": "3", "resourceType": "fields", "label": "Nom", "answerType": "TEXT", "valueBinding": "name", "isSystemField": true },
    "4": { "id": "4", "resourceType": "fields", "label": "Type", "answerType": "SELECT_ONE_FROM_FIELD_CODE", "valueBinding": "type", "isSystemField": true, "fieldCode": "SIA.PROJECT_TYPE" }
  }
}
```
- **No `order`/`section`** on fields or fieldConfigs — `layoutJson` already encodes row/column/field arrangement. (An early draft duplicated order into the fields array; corrected after review.)
- **No `mandatory`/`readOnly` on Project's fieldConfigs** — for Project these are **layout-column** properties (`CustomColUiDto.isRequired` / `.readOnly`), read from `layoutJson` alongside the arrangement. `mandatory` remains *permitted* by the generic schema for RU/Mobilier/Phase/Container where `FieldFormConfig.isMandatory` is a genuine config-row property. Project's fieldConfigs carry `active` + `institutionLocked` only.
- **No branch/collection detail in fieldConfigs**, even for concept fields — that resolution (`FieldConfigurationService`, `ConceptFieldConfig`/`ConceptFieldFormConfig`) is entirely server-side, already hidden behind `GET /api/v1/projects/{id}/concepts?fieldCode=…` (the endpoint the RU frontend's `autocomplete.ts` uses). The client only needs `fieldCode` (already on `FieldResource`). Confirmed sufficient: every concept field on `ActionUnitForm` (`ACTION_UNIT_TYPE_FIELD`, `PERIODS_FIELD`, `SUBJECTS_FIELD`, `STATUS_FIELD`, `SYSTEM_FIELD`, `FIELD_STATUS_FIELD`, `DEVELOPMENT_NATURE_FIELD`) is a `…FromFieldCode` variant.
- Phase 1 populates only `_default`, simulated from the hardcoded `ActionUnitDetailsForm`/`ActionUnitNewForm` (`form.layoutJson` serialized exactly as `buildProjectUiForm` already does, `active: true` throughout, `institutionLocked: true` for all fields since nothing is persisted/editable). `data: []` stays empty — Project is explicitly **not** being plugged into the real `ConfigurableTable`/`FieldFormConfig` machinery this phase.

---

## 7. JSF → React migration strategy

Generalizes the RU branch's pattern, but at the level of the **whole panel body** (both panes), reflecting that React now owns the splitter:

1. Add `isReactPanelEnabled()` on `AbstractPanel`, default `false`, overridden `true` on `ActionUnitPanel`, `ActionUnitListPanel`, `WelcomePanel`.
2. In `focus.xhtml`, where it does `<ui:include src="/panel/panelContent.xhtml">` (~line 315), branch on `focusViewBean.mainPanel.isReactPanelEnabled() && (parentOrOverview == null || parentOrOverview.isReactPanelEnabled())`: if true, render a mount `<div>` + `<script src=".../react-main-panel/…">` + inline script calling `window.SiamoisMainPanel.mount(container, { panelKind, entityType, entityId, overviewEntityType, overviewEntityId, organizationId, csrfToken, basePath, bookmarked: panelModel.isBookmarked(), actions: {...} })`; if false, fall through to the existing include, unchanged. **`bookmarked` is the only initial toolbar state passed this way** — read once at mount, not re-fetched via `p:remoteCommand`, since the toggle goes through REST.
3. `actions` bridged via `p:remoteCommand` exactly as the RU branch established (close-overview/fullscreen/duplicate/refresh/create/settings — pulled from *both* `focus.xhtml`'s toolbar and `panelContent.xhtml`'s overview toolbar, since React renders both), calling straight into the `FlowBean`/`AbstractPanel` methods JSF's own chrome calls. **`FlowBean`/`FocusViewBean` keep owning panel lifecycle**; React never reimplements open/close/duplicate/fullscreen bookkeeping. **Bookmark is deliberately not bridged**: it doesn't touch `FlowBean`'s panel-stack state at all (`Bookmark` is a plain `(person, institution, resourceUri)` row), so React calls the new REST endpoints directly.
4. Row-click / relationship-link navigation goes through plain URL navigation to existing redirection routes (`/action-unit/{id}`, `/recording-unit/{id}`, …) rather than client-side routing — this keeps non-migrated types working with zero extra bridge code. Opening a *new* overview from inside React goes through the bridged `addPanelToOverview`, then a full remount that re-evaluates the fallback rule.
5. Session/auth: the same JWT-over-session-token bridge as RU. No new auth model.
6. **Mount timing — the deferred load is removed for React panels.** `focus.xhtml`'s body is normally AJAX-deferred (`p:outputPanel deferredMode="visible" loaded="#{focusViewBean.mainPanel.loaded}"`, `p:ajax event="load"`, loading-skeleton facet), which would mean the mount `<div>` doesn't exist until an AJAX event fires. Per decision: when `isReactPanelEnabled()` is true, render the mount `<div>` + script **directly and synchronously** in the initial response — no deferred `p:outputPanel`, no skeleton facet, no `p:ajax event="load"`. Unmigrated entity types keep the existing deferred behavior in the same template, untouched. (Different from the RU branch's mount trigger — don't copy that blindly.)

Each future entity migration then becomes: write one `entities/<type>/config.tsx`, flip that entity's `isReactPanelEnabled()` to `true`. No changes to `FlowBean`, `FocusViewBean`, `PanelFactory`, redirection controllers, or the generic React components. The overview fallback triggers less often as more types migrate.

**Housekeeping (not a deliverable)**: `flow.xhtml` and its tab-stack template appear dead — worth a separate cleanup pass, not bundled here.

> **Note (see status above)**: point 4's "row-click goes through plain URL navigation" was superseded — `App.tsx` does client-side navigation (`history.pushState`) whenever the target entity type is registered, and `ActionUnitController` (not `flow.xhtml`) is the actual redirect target confirmed live.

---

## 8. Implementation phases

1. **Scaffold & build integration** — `frontend/` Vite+React+TS+PrimeReact, Maven wiring (`react-main-panel` resource path), `mount.ts` skeleton, auth bridge, `apiFetch` client, PrimeReact stock theme (no `--siamois-*` mapping).
2. **Generic panel infrastructure** — registry types, `EntityListPanel`, `EntityDetailPanel` (tabs + field-renderer registry + `valueBinding` resolver), `HomePanel` (widget-slot shell).
3. **API preparation** — fix the PATCH/DELETE permission bug; add `GET /api/v1/organizations/{id}/project-types` (layout + fields + fieldConfigs) and remove `GET /api/v1/projects/form`; add `_permissions` + `bookmarked` to `ProjectResource` (plus the `BookmarkService` bulk check) and `_permissions` to `OrganizationResource`; add `POST`/`DELETE /api/v1/bookmarks`; verify/add the history endpoint.
4. **Project entity config** — `entities/project/config.tsx`: columns, the single fiche tab, routes, API bindings. No permission logic (read off `_permissions` at render).
5. **Project List** — wire `EntityListPanel` end-to-end against `GET /api/v1/projects`.
6. **Project Detail** — `EntityDetailPanel`, fiche tab only: schema-driven Details, identifier edit, validate toggle, prev/next.
7. **Home** — recent/my-projects widget + Project card only.
8. **JSF integration** — `isReactPanelEnabled()`, the `focus.xhtml` branch (with the overview fallback check), `p:remoteCommand` bridge for both toolbars, PrimeReact `Splitter` wiring.
9. **Automated tests** — Java: the permission fix (both the project-scoped positive case and that org-wide still works) and the new `project-types`/`_permissions`/history endpoints, **run under Java 17** (`mvn` defaults to a newer JDK here, which breaks Mockito's inline mock maker — gotcha from the RU migration). React: Vitest coverage for the registry / field-renderer / `valueBinding` resolver, mirroring the RU branch's pure-logic coverage. Not optional.
10. **Manual verification** — see below.
11. **Cleanup / migration notes** — document the registry pattern and the `isReactPanelEnabled()` switch so the next entity is a config-only change; flag branch reconciliation (§9). **Do not remove the old JSF Project templates/beans** — dead-code removal happens once, after every entity type has migrated.

---

## Verification

- **Build**: `mvn -DskipTests package` produces the React bundle under `META-INF/resources/resources/react-main-panel/`; confirm the RU `react-ru-panel/` path is untouched.
- **Java tests** (Java 17): `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test -Dtest='ProjectApiService*,*ProjectTypes*,*Bookmark*'` — permission fix in both directions, `project-types` shape, `_permissions`/`bookmarked` batching (assert one query per page, not per row).
- **React tests**: `npm test` in `frontend/` (Vitest) — registry resolution, field renderer per `answerType`, `valueBinding` read/write.
- **End-to-end, in the running app**: Home → recent projects + Project card; List → search, sort, paginate, create, row click into detail; Detail → edit a field, inline-edit identifier, toggle validate, prev/next, bookmark on/off (verify the row lands in the Favoris sidebar with `resourceUri = /action-unit/{id}`).
- **Permission gating**: as a project-scoped-only user, confirm edit now works through the API (this is the bug fix); as an org-wide user, confirm `canCreateProjects` is correct per organization.
- **Non-regression**: open an unmigrated entity type (e.g. Spatial Unit) as the main panel, and a Project with an unmigrated overview — both must render the legacy JSF path unchanged, still deferred-loading as today.

---

## 9. Risks and open questions

- **Branch reconciliation**: this creates a *second*, independent `frontend/` on `main` while `feat/ru-react-panel-phase1` has its own unmerged one. When that branch merges, the two trees (different mount entries, different resource paths, shared PrimeReact conventions) must be reconciled into one app with multiple mount points. Flagged, not solved — the fresh-scaffold path was chosen knowingly. **Don't merge or depend on that branch without asking first.**
- **Resolved**: `layoutJson` is served by `project-types`; `GET /api/v1/projects/form` is fully superseded and removed (no in-repo consumers).
- **Resolved**: `mandatory`/`readOnly` come from the layout for Project, not from fieldConfigs.
- **Resolved**: Home is React but scoped down to recent/my projects + the Project card — a deliberate reduction vs today's JSF Home, not an oversight.
- **Resolved**: no optimistic locking on `ProjectPatchRequest` — reserved for the offline mobile app.
- **Resolved**: dead JSF templates/beans stay in place until the whole main-panel migration is done.
- **Don't over-build**: `ActionUnitNewForm`/`ActionUnitDetailsForm` contain **zero** `enabledWhen`/`dependsOn` specs — the Project fiche does **not** need the conditional-rules engine the RU branch ported (`rulesEngine.ts`). Don't port it "for parity." It does use `isRequired` (×3) and `readOnly(true)` (×1) on layout columns, which the fiche must honor.
- **Home panel decomposition**: whether `homePanel.xhtml`'s card grid splits cleanly or Home stays JSF longer — decide while actually looking at `homePanel.xhtml`/`WelcomePanel` in phase 7. Not architecturally blocking given the widget-slot design. **Resolved (see status above)**: `homePanel.xhtml` is a title plus two sibling `p:panel`s, not one — `HomePanel`/`HomeWidgetDef` now model that with a `kind: "panel" | "card"` split.
- **Deferred, confirmed not blocking Project**: concept fields configured purely via branch/collection on their own field id (no `fieldCode`) have no REST autocomplete path — `GET /api/v1/projects/{id}/concepts` is `fieldCode`-only and never reaches `FieldConfigurationService.fetchAutocomplete(CustomFieldConcept, input, actionUnitId, valueConceptId)`. Same gap flagged during the RU migration, still open. Every concept field on `ActionUnitForm` is `fieldCode`-based, so it doesn't block this phase. **Don't build it unless asked.** Proposed fix when needed: accept `fieldId` as an alternative selector, plus optional `valueConceptId`.
- **Rule of thumb from this work**: when unsure what a REST endpoint's permission check *should* be, check the equivalent JSF bean/panel first — the REST layer is newer and less trustworthy on this front.
