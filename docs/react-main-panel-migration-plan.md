# React/PrimeReact migration of the main panel area — starting with Project

> **Follow-up plan saved to the repo 2026-09-21** from the working plan at
> `~/.claude/plans/i-was-migrating-my-eager-meadow.md` — "React project list — path to JSF
> parity" (phases 0–5), the plan that picks up right where the status note below leaves off (list
> feature parity: sort, dynamic columns/filters, click-to-edit, and the client-side overview
> pane). Full text in **§10** at the bottom of this document. Branch: `feat/main-panel-react-migration`.
>
> **Status as of 2026-09-21 (§10 plan)**: all 6 phases (0–5) implemented and staged
> (uncommitted, per project convention — never commit/push without being asked). Backend suite
> 2510 passing, frontend suite 162 passing, `tsc --noEmit` clean.
> - Phase 5 (client-side overview pane) needed two fixes beyond the plan's own text, found only
>   by actually testing in a browser rather than trusting the test suite (jsdom doesn't lay out
>   CSS, so neither bug was test-visible):
>   1. PrimeReact's `Splitter` depends on a `display:flex` rule it injects at runtime inside a
>      `@layer primereact { … }` block, which loses to this app's unlayered legacy CSS regardless
>      of load order (an unlayered rule always beats a layered one at equal specificity) — so the
>      two panes silently fell back to block stacking. Fixed with an explicit inline
>      `display:flex` on `Splitter`/`SplitterPanel` in `App.tsx`, not relying on that injection.
>   2. `focus.xhtml`'s outer React-panel wrapper carried `border-top: 3px solid var(--main-color)`
>      around the **whole mount** (both panes together) — so opening the overview visually framed
>      it together with the main panel in one bordered box. Moved that border off the wrapper and
>      onto just the left/main `SplitterPanel` in `App.tsx`; the overview pane already gets its
>      own border via the existing `.sideview` CSS class, no change needed there.
> - Not yet done: an actual end-to-end run against the real Spring Boot app + DB (only verified
>   via the Vite dev harness with a stubbed `fetch`, plus the two full automated test suites).
>
> ---
>
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

---

## 10. Follow-up plan: React project list — path to JSF parity (treetable excluded)

> Saved verbatim from `~/.claude/plans/i-was-migrating-my-eager-meadow.md`, approved and executed
> 2026-09-21. See the status note at the top of this document for completion state and the two
> post-implementation fixes.

### Context

The JSF→React migration (`feat/main-panel-react-migration`) has landed Home, project list and project detail
behind `AbstractPanel.isReactPanelEnabled()`. The React list (`frontend/src/panels/EntityListPanel.tsx`)
today is a thin lazy `DataTable`: 7 hardcoded columns, free-text search, single-column sort, row click that
*replaces* the main pane. The JSF list (`panel/actionUnitListPanel.xhtml` →
`pages/shared/table/entityDataTable.xhtml`) gives users ~30 toggleable columns, per-column filters, a column
toggler, checkbox selection with a count chip, click-to-edit cells, and a row click that opens the project in
the **right-hand overview pane** while the list stays put.

This plan closes that gap. The treetable view is explicitly out of scope; saved views (`UiViewService`) are
deferred but the table state is designed so they bolt on later.

Three exploration findings shape the plan:

1. **All 33 `ActionUnitForm` fields are `isSystemField(true)`** with real properties on `ActionUnitDTO` — there
   are *no* `CustomFieldAnswer` rows for projects. The `answers` map is therefore a reflective projection, not
   a join. We still go generic (user decision): it is investment so RU/Find/Specimen lists become config-only
   later, and it keeps the toggler/filters/edit overlay entity-agnostic.
2. **JSF only actually filters on `name`, `fullIdentifier` and global search** (`ActionUnitLazyDataModel.prepareFilterDTO`);
   the `filterable(true)` metadata on the other 28 columns is aspirational. Per-column filters are therefore a
   feature expansion — scoped to the 9 default-visible columns.
3. **`GET /api/v1/projects` sort is broken**: `ProjectControllerApi.java:63` declares
   `@RequestParam(name = "name:asc", ...)`, so the query key is literally `name:asc` and `?sort=` never binds.
   Column sorting has never worked; it only *looks* right because the default matches the frontend's default.

---

### Phase 0 — unblock sorting (backend, ship alone first)

Nothing downstream is testable until sort binds.

- `ui/api/openapi/v1/controller/project/ProjectControllerApi.java:63` — `@RequestParam(defaultValue = "name:asc") String sort`,
  matching the sibling `ProjectSettingsControllerApi` / `ProjectRecordingUnitsControllerApi`. Ripple through
  `ProjectApiService.pageAccessibleProjects(..., List<String>)` → `String`.
- `ui/api/openapi/v1/service/ProjectApiService.java` — swap `parseProjectSort` onto the existing
  `parseSortWithStableId(...)` (already used by RU/Place). Without the id tiebreaker every paging test in later
  phases is flaky on duplicate names.
- Same file — `parseSort` currently falls back silently on an unknown field. Make it **400**. Silent fallback is
  exactly why the bug above went unnoticed.
- Widen `ALLOWED_PROJECT_SORT_FIELDS` to the sortable column set. `recordingUnitCount` is the one special case:
  `ActionUnitSpec.orderByRecordingUnitCount(direction)` already exists but carries its order on the
  `Specification`, not the `Sort`, so `ActionUnitService.findAccessibleProjects` needs a branch.

---

### Phase 1 — `answers` on list rows

#### Wire shape: raw values + root catalog, opt-in

Do **not** put the `FieldAnswer` envelope on list rows — `toTypedAnswer` embeds a whole `FieldResource` per
answer (33 fields × 25 rows = 825 copies of the metadata per page). The frontend already fetches the catalog
from `GET /api/v1/organizations/{id}/project-types`.

```
GET /api/v1/projects?fields=default        # also: fields=all | fields=-151,-128
{ "data": [ { ...ProjectResource..., "answers": {
      "-128": 0.42,
      "-389": {"id":"12","resourceType":"concepts","label":"En cours"},
      "-356": [ {...ResourceRef}, {...} ] } } ], "meta": {...} }
```

Scalar for TEXT/INTEGER/DECIMAL/DATETIME, `ResourceRef` for `SELECT_ONE_*`, `ResourceRef[]` for `SELECT_MULTIPLE_*`.
**Opt-in**: absent `fields` → no `answers` key, default list stays as cheap as today. Name it `fields`; do *not*
reuse `includeOnlyFields` (`RecordingUnitsControllerApi.getById:109` accepts and discards it — a documented lie,
worth its own cleanup task).

Files:
- `ui/api/openapi/v1/resource/project/ProjectResource.java` — add `Map<String, Object> answers` (`@JsonInclude(NON_NULL)`),
  and `resourceUri` (one line off the existing `ProjectApiService.actionUnitResourceUri`, needed in phase 5).
- **New** `ui/api/openapi/v1/service/ProjectAnswersProjector.java` (~120 l.) — `List<ActionUnitDTO>` + requested
  field ids + prebuilt label map → `Map<Long, Map<String,Object>>`, reading via `CustomField.valueBinding`
  reflection. Mirror of `FormService.getBindableFieldNames`/`initializeFieldIfNeeded`, batched and read-only.
- `ui/api/openapi/v1/mapper/ProjectResponseMapper.java` — overload `toResource(..., answers)`; change
  `toConceptFieldValue` to take a resolved-label map instead of calling `LabelService` per concept.

#### Batching (the actual perf work)

- **New** `domain/services/vocabulary/ConceptLabelBatchResolver.java` + two methods on
  `infrastructure/database/repositories/vocabulary/label/ConceptLabelRepository`:
  `findPrefLabelsByLangCodeAndConceptIdIn` / `findAllAltLabelsByLangCodeAndConceptIdIn`. Collect every concept id
  on the page (type, status, system, fieldStatus, developmentNature, periods, subjects) → 2 queries. This also
  speeds up today's list, where `LabelService.findLabelOf` is 1–2 uncached queries *per concept*.
- Lazy `@ManyToMany` (`periods`, `subjects`, `spatialContext`): **do not** add a multi-collection `@EntityGraph`
  to the `Pageable` query — Hibernate falls back to in-memory paging (HHH000104). Two-step in
  `ActionUnitService.findAccessibleProjects:759`: page ids with the existing spec, then `findAllById` with the
  graph, then reorder. Gate the graph on `fields` being requested.

#### Fix the DECIMAL answer gap here

Six columns (`zmin`, `zmax`, `openingRate`, `prescribedArea`, `excavatedArea`, `accessibleArea`) are
`CustomFieldDecimal`, which is missing from `FieldAnswer`'s `@JsonSubTypes`. Reads fall through to
`default -> TextFieldAnswer` and **writes are silently dropped** (`coerceAnswerValue` has no decimal branch) —
a live data-loss bug on recording units today, independent of this migration.

- **New** `resource/form/DecimalFieldAnswer.java`; add to `permits`, `@JsonSubTypes(names={"DECIMAL"})`, `@Schema(oneOf=)`.
- `service/RecordingUnitOpenApiService.java` — `case "DECIMAL"` in `toTypedAnswer`; `CustomFieldDecimal` branch
  in `coerceAnswerValue`.

#### Frontend: resolve the envelope mismatch once

`frontend/src/fields/types.ts` — `resolveValueBinding` currently reads a *raw* value from `entity.answers[id]`,
but RU/Find detail responses carry the *envelope*. One helper reconciles raw list rows, enveloped detail
responses and today's flat `ProjectResource` through a single path, with no changes in `FicheTab.tsx`:

```ts
function unwrapAnswer(v: unknown) {
  if (v && typeof v === "object" && "answerType" in v) {
    const a = v as { value?: unknown; values?: unknown };
    return "values" in a ? a.values : a.value;
  }
  return v;
}
// read: answers[field.id] when defined → else (isSystemField ? entity[valueBinding] : undefined)
```

---

### Phase 2 — dynamic columns, toggler, selection, table state

#### Column set: schema-driven, defaults owned by the backend

`EntityTypeConfig.list.columns` stays but narrows to *pinned, non-form* columns — the identifier chip and the
recordingUnit relation count. Everything else comes from the catalog. In `frontend/src/entities/types.ts`:

```ts
export interface FieldCatalog {
  fields: Record<string, FieldResource>;
  columns: { fieldId: string; visible: boolean; order: number }[];
}
list: {
  columns: ColumnDef<TSummary>[];                                   // pinned, hand-written
  schema?: { load: (ctx: { organizationId?: number }) => Promise<FieldCatalog> };
  defaultSort?: string;
  searchable: boolean;
}
```

**Default visibility lives in Java**, not a TS const that drifts. Extract the visible/hidden/order truth out of
`ui/table/definitions/ActionUnitTableDefinitionFactory.applyTo` into a new `ActionUnitTableColumnDefaults`
consumed by *both* the JSF factory and `RecordingUnitOpenApiService.buildProjectTypes`, exposed on
`/organizations/{id}/project-types` under `_default.tableColumns`.

Default-visible (mirrors the factory): identifier (pinned, non-toggleable), name, recordingUnit count, status,
oaCode, mainLocation, openingRate, periods, subjects, scientificManager. The other 23 are toggleable-hidden.
Only request `fields=` for currently-visible columns — that is what makes the toggler pay for itself.

#### Files

- `frontend/src/panels/EntityListPanel.tsx` — merge pinned `ColumnDef`s with catalog-derived ones.
- **New** `frontend/src/fields/display.tsx` — `renderAnswerValue(field, value)` for read-only cells (scalars,
  concept labels, comma-joined `ResourceRef[]`). Cheaper and clearer than reusing the form renderers for cells.
- **New** `frontend/src/components/table/ColumnToggler.tsx` — PrimeReact `MultiSelect` inside the gear
  `OverlayPanel`, mirroring `p:columnToggler` / `EntityTableViewModel.onToggle`.
- `frontend/src/entities/project/columns.tsx` — trim to the two pinned columns.
- `frontend/src/entities/project/projectTypes.ts` — surface `_default.tableColumns`.
- Selection: `selectionMode="checkbox"` + a `selected/total` `Chip`, mirroring `selectedCountChip` (~2 h).
  Note JSF's `handleSelectionChange()` is a no-op — this is decoration until a bulk action exists.
- Paginator: `rowsPerPageOptions={[10,25,50]}`, **default 10** (React currently defaults to 20 — a silent
  parity divergence).
- Debounce the search box (~300 ms) — today every keystroke is a new query key.

#### Table state as one serializable object (land before phase 3)

**New** `frontend/src/panels/tableState.ts` + `useTableState.ts`:

```ts
export interface TableState {
  v: 1;                                   // versioned from day one, or saved views become unmigratable
  offset: number; limit: number;
  sort?: string;
  search?: string;
  visibleColumns: string[];               // ordered
  filters: Record<string, FilterValue>;   // {op:"contains",v} | {op:"in",v:string[]} | {op:"range",from?,to?}
}
toQueryParams(s): URLSearchParams         // owns the f.* encoding
encodeTableState(s) / decodeTableState(s) // base64url, tolerant (null on bad v)
```

`ListParams` grows `filters` and `fields`; the query key becomes `["entity-list", entityType, state]`.
Two payoffs beyond saved views: list URLs become shareable, and `popstate` can stop doing
`window.location.reload()` (decode `?s=` → `setState`). Reconciliation on mount: `data-*` mount options win
where present, `?s=` fills the rest. Saved views later = `POST /ui-views { state: TableState }`, zero component
changes.

---

### Phase 3 — per-column filters (default-visible columns only)

#### Contract

Flat, repeatable, namespaced `f.` on `GET /api/v1/projects`:

```
f.status=12&f.status=44      # repeatable → OR within key, AND across keys
f.name=foss                  # text, case-insensitive contains
f.zmin.from=10&f.zmin.to=40  # numeric range
f.beginDate.from=2024-01-01  # date range, ISO-8601
```

Bind with `@RequestParam MultiValueMap<String,String>`, parse in a **new**
`ui/api/openapi/v1/request/project/ProjectListFilter.java` against a whitelist of `key → (entityPath, kind)`.
**400 on unknown key or unparseable value** — do not repeat the sort param's silent fallback.

#### JPA mapping

**New** `infrastructure/database/repositories/specs/ActionUnitFilterSpec.java` (keep `ActionUnitSpec` as-is;
it is already shared with the JSF path). Six generic builders keyed by kind + a whitelist table:

| kind | phase-3 targets | spec |
|---|---|---|
| text contains | name, fullIdentifier, oaCode, scientificManager | `lower(col) like %v%` |
| concept-one in | status | `root.get(p).get("id").in(ids)` |
| concept-many in | periods, subjects | **EXISTS subquery** |
| spatial-one in | mainLocation | `root.get(p).get("id").in(ids)` |
| numeric range | openingRate | `between` / `ge` / `le` |
| date range | beginDate, endDate | same |

Use `EXISTS` subqueries for `@ManyToMany`, never `join` + `distinct` — `distinct` breaks the count query that
`findAll(spec, pageable)` issues and silently inflates `totalCount`. Also respect
`ProjectApiService.validatePagedListRequest`'s `offset % limit == 0` constraint when filters change page size.

Realistic size: ~250 lines of Java + a spec test class. It is small *only because* every target is a real entity
column. **Flag for the future:** when RU/Find want this UX over real `CustomFieldAnswer` rows, that is the hard
version (per-answer-type value column, EXISTS on `custom_field_answer` joined to field id). The wire contract
above is deliberately shaped so it can be swapped in behind the same `f.<key>` params with no client change.

Note `scientificManager` is a `CustomFieldText`, not a person ref — no person autocomplete needed.

#### Option sources

`GET /api/v1/projects/{id}/concepts?fieldCode=` is project-scoped and wrong for a cross-project list filter.
Add `GET /api/v1/organizations/{orgId}/concepts?fieldCode=&q=&limit=` delegating to the same service with
institution scope (verify field-code config resolves per institution in `FormConfigService` first). Places
already have the right shape (`/places/autocomplete?organizationId=`).

Frontend: **new** `frontend/src/components/table/ColumnFilter.tsx` (dispatches on `field.answerType` →
`MultiSelect` / `AutoComplete` / range inputs / `Calendar` range) and **new** `frontend/src/fields/optionSources.ts`
(`answerType + fieldCode` → a `useQuery` fn).

> **Superseded (post-phase review).** The filter UI is *not* a JSF-style per-column input row behind a
> "Filtres activés/désactivés" switch in the gear overlay. It is a Notion-style chip bar in the table's own
> header (**new** `frontend/src/components/table/FilterChipBar.tsx`): one chip per active filter — click to
> edit, × to drop — plus a single "Ajouter un filtre" chip offering the columns not yet filtered. No enable/
> disable toggle at all. `ColumnFilter` stays the per-kind widget, now rendered inside a chip's editor
> overlay, and its selected-option *labels* are owned by `EntityListPanel` (`FilterValue` is ids-only, and a
> chip has to print names). The gear consequently does one thing only — show/hide columns — and sits
> **before** the search box, sized larger than it.

---

### Phase 4 — click-to-edit overlay

One shared editor owned by `EntityListPanel`, anchored to the clicked cell and initialised from
`(row, field)` — no pencil buttons, no per-cell inline widgets.

- **New** `frontend/src/components/table/CellEditOverlay.tsx` — state `{ row, field, anchor } | null`.

> **Superseded (post-phase review).** Not a PrimeReact `OverlayPanel`: that always drops *below* its target
> with an arrow, i.e. a popover rather than an in-place editor. The editor is a `position: fixed` box
> portalled to `document.body` at the clicked cell's own client rect, so it renders **on top of** the cell.
> It shows the field widget and nothing else — no label, **no Save/Cancel buttons**. Saving happens on value
> change: immediately for `SELECT_*`/`DATETIME` (one interaction = one final value), and on Enter or on focus
> leaving the editor for text/number (per-keystroke saving would be one PATCH + one refetch per character).
> Escape still cancels; a failed save keeps the editor open with the server's message. An unchanged value
> issues no PATCH. Editable cells get a hover/focus style and a pointer cursor so the affordance is visible
> before clicking.
- **Fix a collision first:** `EntityListPanel`'s `onRowClick` currently fires on *any* cell. Once cells are
  editable, navigation must move onto the identifier column's body only — matching JSF, where only the
  `CommandLinkColumn` navigates. Non-editable cells then do nothing.

#### Renderers need an edit mode

Today only TEXT/INTEGER/DECIMAL/DATETIME are registered (`frontend/src/fields/registerDefaultRenderers.ts`);
every `SELECT_*` silently falls to the read-only `FallbackRenderer`. Add `SELECT_ONE_FROM_FIELD_CODE` (Dropdown,
async concepts), `SELECT_MULTIPLE_FROM_FIELD_CODE` (MultiSelect), `SELECT_ONE_SPATIAL_UNIT` (AutoComplete on
places). Defer `SELECT_MULTIPLE_SPATIAL_UNIT_TREE` (tree picker). Add `hasEditableFieldRenderer(answerType)`
alongside the existing `hasFieldRenderer`.

**This is the biggest frontend chunk and it is on the critical path twice** — it also retires `FicheTab`'s
"Non disponible" / read-only stubs. Split it: **4a** = renderers + option sources (lands value in the fiche
immediately), **4b** = the overlay.

#### Write path

- `ui/api/openapi/v1/request/project/ProjectPatchRequest.java` — add `Map<String, AnswerInput> answers`,
  mirroring `RecordingUnitPatchRequest`.
- `ProjectApiService.patchProject` (~l.302) — after `applyProjectPatch`, `applyAnswerPatch(dto, patch.getAnswers())`:
  resolve fieldId → `CustomField` from `ActionUnit.DETAILS_FORM`, coerce, write via `valueBinding` reflection,
  **400 on unknown field id**. Extract the private `ruCoerce*` helpers out of `RecordingUnitOpenApiService` into
  a shared `AnswerCoercion` component. Document precedence: flat properties first, `answers` second (name,
  identifier, beginDate, endDate are reachable both ways).
- **Permissions:** gate on `row._permissions.canEdit`. The identifier is special — JSF's `handleLinkEdit` gates
  on `ORGANIZATION_MANAGE_ACTIONS || PROJECT_MANAGE_SETTINGS`, which is *not* `hasActionUnitWritePermission`.
  Add `canEditIdentifier` to `ProjectResourcePermissions` rather than shipping a UI that 403s on save. Surface
  the `409 ActionUnitAlreadyExistsException` inline in the overlay (JSF's duplicate-identifier parity).
- **Skip optimistic locking.** `ActionUnit` has no `syncRevision` (only `RecordingUnit` does); adding one is a
  Liquibase migration touching every write path. Instead: PATCH returns the full resource → `setQueryData` to
  patch the row in place, then invalidate `["entity-list", entityType]` (so sort/filter re-evaluate) and
  `["entity-detail", "project", id]` (so an open overview refreshes).

---

### Phase 5 — client-side overview pane

Row click on the identifier opens the project in the **right** splitter pane; the list stays. Recommended
approach: **hybrid — optimistic client render + a new remoteCommand that keeps the bean in sync.**

Pure client-side would lose three things: F5 correctness (the server panel stack has no overview, so the
remount emits no `data-overview-*` and the pane vanishes), the overview toolbar's create/duplicate/settings
actions (they dereference `panelModel.parentOrOverview`, which stays null), and the workspace-restore contract.
The bridge is cheap, and `FlowBean` already has the exact entry point with the exact flag:
`addActionUnitToOverview(id, targetPanel, tabIndex, updateMainPanel=false)` (`FlowBean.java:364`) — the same
call JSF's own row-click makes, and `updateMainPanel=false` means the server re-render never touches the
React-owned main pane.

- `src/main/resources/META-INF/resources/panel/reactPanelActions.xhtml` — add
  `reactAction_setOverview_${panelModel.panelIndex}`, id passed as a remoteCommand param,
  `process="@this" update="@none"`.
- `src/main/resources/META-INF/resources/resources/js/reactPanelBootstrap.js` — expose as `actions.setOverview(entityType, id)`.
- `frontend/src/mountOptions.ts` — `PanelActions.setOverview?: (entityType, id) => void`.
- `frontend/src/App.tsx` — `overview` becomes **state**, seeded from `options.overviewEntityType/Id` instead of
  being permanently bound to it. `openOverview` sets state immediately, fires `setOverview` fire-and-forget,
  pushes the `?s=<base64url>` URL. `closeOverview` clears state and calls the existing
  `reactAction_overview_closeOverview`. Guard the current `entityId ?? ""` fallback so a null overview never
  renders `EntityDetailPanel`.
- `frontend/src/panels/EntityListPanel.tsx` — new `onOpenOverview` prop, called from the identifier cell
  (`onNavigate` stays for home widgets / explicit "open full").
- **Chrome derived client-side:** add `EntityTypeConfig.chrome?: (detail) => PanelChrome` returning
  `{ resourceUri, title: fullIdentifier ?? name, bookmarked }`. `resourceUri` comes from the new
  `ProjectResource.resourceUri` (phase 1) — do not hardcode `/action-unit/` in TypeScript. `bookmarked` is
  already on the wire.
- **Row highlight:** `rowClassName={(row) => row.id === overview?.entityId ? "overview-open" : ""}`, plus
  `search-match` when `search` is non-empty. Skip `row-newly-duplicated` (meaningless until duplicate is
  client-side).
- **Caveat:** the remoteCommand is async. Disable the overview toolbar actions while a `setOverview` is in
  flight, or a fast double-click acts on the previous entity.

Because the bean stays in sync, the three bean-bound overview actions keep working unchanged — they resolve
`parentOrOverview` server-side at invoke time.

---

### Sequencing

| Phase | Content | Depends on | Size |
|---|---|---|---|
| 0 | sort param bug, stable id, 400-on-bad-sort, sortable set | — | 0.5 d, ship alone |
| 1 | `answers` projection + `?fields=`, label batching, two-step graph, DECIMAL subtype, `unwrapAnswer` | 0 | 3–4 d |
| 2 | backend column defaults, dynamic columns, toggler, selection, `tableState.ts`, paginator/debounce | 1 | 3–4 d |
| 3 | `f.*` contract, `ActionUnitFilterSpec`, org-scoped concepts endpoint, filter UI | 0, 2 | 3–4 d |
| 4a | SELECT_* edit renderers + option sources | 1, 2 | 2–3 d |
| 4b | `CellEditOverlay`, `ProjectPatchRequest.answers`, `canEditIdentifier` | 4a | 2 d |
| 5 | `setOverview` bridge, App overview state, chrome derivation, row classes | 2 | 2 d |

Out of scope, tracked for later: treetable (`rootOnly` + the dangling `/projects/{id}/children` controller),
saved views (`UiViewService` over REST), multi-sort, bulk actions on the selection, `row-newly-duplicated`.
Export (CSV/XLS/PDF) does **not** exist in JSF — not a port target.

---

### Verification

**Per phase, backend:** `mvn -q -Dtest=ProjectControllerApiTest,ProjectApiServiceTest test`. Phase 0 needs a new
case asserting `?sort=name:desc` actually reverses (none exists today — that is how the bug survived). Phase 1
needs a projection test plus a query-count assertion (`@DataJpaTest` + Hibernate statistics) proving a 25-row
page with `fields=default` stays flat, not N+1. Phase 3 needs an `ActionUnitFilterSpec` test class, including a
`totalCount` assertion on a `@ManyToMany` filter to catch the `distinct`-inflation trap.

**Per phase, frontend:** `cd frontend && npm test` — extend `src/panels/EntityListPanel.test.tsx` (currently 8
cases) for dynamic columns, toggler, filters, selection and the overlay; `src/fields/` tests for `unwrapAnswer`
against all three shapes (raw, envelope, flat system field); `tableState.test.ts` round-trip on encode/decode.

**End to end**, per phase, against a real app run:
1. `mvn spring-boot:run`, log in, select an institution.
2. Open `/action-unit` in focus mode (React branch — confirm via `ActionUnitListPanel.isReactPanelEnabled()`).
3. Phase 0/1: click each sortable header, confirm the order actually changes and paging is stable across pages
   with duplicate names. Check the network tab for `?sort=` and `?fields=`.
4. Phase 2: toggle columns in the gear overlay, confirm the `fields=` param shrinks/grows with them; check
   selection chip counts; confirm 10/25/50 rows-per-page.
5. Phase 3: apply each filter kind, cross-check `totalCount` against the paginator and against the JSF list.
6. Phase 4: click a cell, edit, save; confirm the row updates in place and the open overview refreshes; confirm
   a duplicate identifier surfaces the 409 inline; confirm a user without `canEdit` gets no overlay.
7. Phase 5: click an identifier — the project opens on the right, the list stays, the row gets `overview-open`.
   Then F5 and confirm the overview survives (that is the whole point of the `setOverview` bridge); then use the
   overview's create/duplicate/settings buttons and confirm they act on the *right* entity.
8. Regression each time: open a **non-migrated** entity in the overview and confirm `focus.xhtml` still falls
   back to the full legacy JSF panel.

Deliver as staged-uncommitted changes on `feat/main-panel-react-migration`; no commits, no push.
