// Generic entity-type abstraction (plan §3). EntityListPanel/EntityDetailPanel/HomePanel consume
// only this + the field-renderer registry — no per-entity branching in the generic components.
// Permission flags are deliberately NOT part of this config: they come straight off each API
// response's own `_permissions` object, never recomputed or guessed client-side.
import type { ReactNode } from "react";
import type { AnswerInputBody, FieldResource } from "../fields/types";
import type { PanelChrome } from "../mountOptions";

export interface PagedResult<T> {
  data: T[];
  totalCount: number;
  limit: number;
  offset: number;
}

/**
 * One column's filter — mirrors ProjectListFilter's Kind whitelist. Lives here (not in
 * panels/tableState.ts, which owns TableState) so both ListParams and TableState can reference it
 * without panels/ becoming something entities/ depends on.
 */
export type FilterValue =
  | { op: "contains"; v: string }
  | { op: "in"; v: string[] }
  | { op: "range"; from?: string; to?: string };

// Scopes a list to one parent entity's own sub-collection — "the recording units OF this
// project", "the documents OF this recording unit" — rather than the entity type's global list.
// `entityType` is the parent's registry key (so the URL builder can look up its own
// `collectionPath`, e.g. "project" -> "projects"); `path` overrides the child segment when it
// isn't the child entity's own `collectionPath` (e.g. an action-unit's finds live at
// "/mobiliers", not "/finds"). Lives here, not in panels/tableState.ts, for the same reason
// FilterValue does: both ListParams and TableState-adjacent code need it without panels/
// becoming something entities/ depends on.
export interface ListScope {
  entityType: string;
  id: string | number;
  path?: string;
}

export interface ListParams {
  offset: number;
  limit: number;
  search?: string;
  sort?: string;
  organizationId?: number;
  // Comma-separated field ids to project into each row's `answers` map (GET /api/v1/projects?fields=…,
  // ProjectAnswersProjector). Undefined omits the param entirely — the API then returns no `answers`
  // key at all, so a config with no dynamic columns costs exactly what it did before this existed.
  fields?: string;
  // Per-column filters, keyed by the same key ProjectListFilter's f.<key> whitelist uses (a
  // column id for a catalog field, or a pinned ColumnDef's own key for name/fullIdentifier).
  filters?: Record<string, FilterValue>;
  // When set, the list is fetched from the parent's own sub-collection rather than this entity
  // type's global list endpoint (plan: generic "related list" tab, e.g. a project's recording
  // units). See entities/listApi.ts for the URL this produces.
  scope?: ListScope;
}

// One neighbour of an entity in its "fiche précédente/suivante" navigation — mirrors
// AbstractSingleEntityPanel.goToPrevious/goToNext (plan: prev/next arrows on the React fiche
// panel). `label` feeds the arrow's tooltip, `resourceUri` comes straight from the server (the
// same rule as PanelChrome.resourceUri elsewhere in this file) — never reconstructed client-side.
export interface EntitySibling {
  id: string | number;
  label: string;
  resourceUri: string;
}

// Either side is undefined when the caller's accessible set has no OTHER entity at all —
// deliberately not JSF's own wrap-to-self behaviour (a self-link is not a useful sibling).
export interface EntitySiblings {
  previous?: EntitySibling;
  next?: EntitySibling;
}

export interface ColumnDef<TSummary> {
  key: string;
  header: string;
  render: (row: TSummary) => ReactNode;
  sortable?: boolean;
  // Marks a pinned column as eligible for a plain "contains" filter, using its own `key` as the
  // f.<key> query param — the only filter kind a pinned column can have, since anything richer
  // (concept/spatial options, a numeric range) needs field metadata a pinned column doesn't carry.
  filterable?: boolean;
  // Marks the one column whose cell opens the row's entity (plan §8 phase 5) — mirrors JSF's
  // CommandLinkColumn, the only clickable cell in the real table. At most one column per entity
  // type should set this; EntityListPanel wires its click handler onto whichever column does.
  // EntityListPanel renders `render`'s output inside a navigation chip (icon + label), matching
  // CommandLinkColumn's own p:chip — so this column's `render` should return the label text only.
  identifier?: boolean;
  // Static, non-interactive content rendered in the same cell but OUTSIDE the clickable part —
  // JSF merges the validation-status badge and the identifier chip into a single column
  // (entityDataTable.xhtml's statusIdActionsCol), and only the chip navigates. Kept generic rather
  // than a `validated` flag: any column may want a leading badge, and only the entity's own
  // columns.tsx knows where that badge's data lives on its rows.
  leading?: (row: TSummary) => ReactNode;
  // Only shown on the unscoped (organization-wide) list — e.g. the row's project, which a
  // project-scoped relation tab would just repeat on every row.
  unscopedOnly?: boolean;
  // Makes the cell a navigation chip to ANOTHER entity (e.g. the row's project): EntityListPanel
  // renders it like the identifier chip, with the target type's own icon, and opens that entity's
  // overview on click. Null for a row with nothing to link to (plain empty cell).
  link?: (row: TSummary) => { entityType: string; id: string | number } | null;
}

// One entry in a list's field catalog — visibility/order default plus the field metadata needed to
// render and label a dynamic column. Mirrors fr.siamois.ui.api.openapi.v1.resource.project.
// ProjectTableColumnResource (server truth for defaults) joined with the root `fields` catalog from
// GET /api/v1/organizations/{id}/project-types.
export interface FieldCatalogColumn {
  fieldId: string;
  // Stable, historical column id (e.g. "status", "mainLocation") — the filter query-param key
  // (f.<columnId>), NOT the field id. Kept distinct because a filter's wire key predates the
  // field catalog and is shared with the JSF table (ActionUnitTableColumnDefaults).
  columnId: string;
  visible: boolean;
  order: number;
}

export interface FieldCatalog {
  fields: Record<string, FieldResource>;
  columns: FieldCatalogColumn[];
}

// Passed to an entity's `list.createForm` factory — deliberately an overlay-hosted form (a
// PrimeReact OverlayPanel anchored to the list's own "Créer" button), not a JSF-style modal
// dialog: the user asked for the simpler, lighter affordance on the React side rather than
// reproducing GenericNewUnitDialogBean's dialog. `onCreated` both closes the overlay (the caller,
// EntityListPanel, owns that) and lets the form hand back the new entity's id; the form itself
// decides what belongs in its own fields (deliberately fewer than the JSF dialog's — see
// entities/project/CreateForm.tsx for what Project's own trims and why).
export interface CreateFormContext {
  organizationId?: number;
  // EntityListPanel's own `scope` prop, threaded straight through (same object, not rebuilt) —
  // a scoped create form (an RU or a Find created FROM a project's own relation tab) reads its
  // parent id off `scope.id` rather than needing a separate prop of its own. Undefined for a
  // top-level, unscoped list (Project's own).
  scope?: ListScope;
  onCreated: (id: string | number) => void;
  onCancel: () => void;
}

// Passed to a tab's render alongside the entity (plan §8 phase 6) — `refetch` so a tab that
// mutates the entity (Project's fiche: field edits, identifier rename) can ask EntityDetailPanel's
// own query to reload rather than each tab wiring its own cache invalidation. The rest
// (organizationId/onNavigate/onOpenOverview/overviewEntityId) is what a related-list tab needs to
// render an embedded EntityListPanel exactly as App.tsx's own top-level list does — plumbed
// straight through from EntityDetailPanel's own props, not rebuilt per tab.
export interface DetailTabHelpers {
  refetch: () => void;
  organizationId?: number;
  onNavigate?: (entityType: string, id?: string | number) => void;
  onOpenOverview?: (entityType: string, id: string | number) => void;
  overviewEntityId?: string | number;
}

export interface DetailTabDef<TDetail> {
  key: string;
  label: string;
  // A count pastille on the tab header itself (actionUnitTabView.xhtml's
  // panelModel.unit.recordingUnitCount badge) — optional, most tabs (the fiche) have none.
  badge?: (entity: TDetail) => ReactNode;
  render: (entity: TDetail, helpers: DetailTabHelpers) => ReactNode;
}

// Passed to an entity's home.widgets factory (plan §8 phase 7) — organizationId comes from
// MountOptions, onNavigate from App's own client-side router (no `id` = navigate to that
// entity's list, matching EntityListPanel's row-click signature otherwise) so a widget's links
// switch panels in place instead of a full page navigation.
export interface HomeWidgetContext {
  organizationId?: number;
  onNavigate?: (entityType: string, id?: string | number) => void;
}

export interface HomeWidgetDef {
  key: string;
  render: () => ReactNode;
  // homePanel.xhtml is two separate sibling p:panels, not one — "Mes derniers projets"
  // (myActionUnits, a standalone toggleable panel) and "Accéder aux bases de données" (dbAccess,
  // one toggleable panel holding a grid of every entity's welcomeCard.xhtml-style count tile).
  // "panel" (default) renders standalone, already wrapped in its own <Panel> by the widget
  // itself (RecentProjectsWidget does this). "card" widgets are collected by HomePanel and
  // placed together inside the one shared "Accéder aux bases de données" panel/grid, mirroring
  // dbAccessPanelGrid — never each in a separate top-level panel.
  kind?: "panel" | "card";
  // Position among the "card" widgets — widgets otherwise arrive in mount.ts's registration
  // order, not homePanel.xhtml's. Lower first; absent sorts last.
  order?: number;
}

export interface EntityTypeConfig<TSummary = unknown, TDetail = unknown> {
  key: string;
  labels: { singular: string; plural: string };
  // The REST collection segment for this entity ("projects", "recording-units") — used to build a
  // scoped list URL generically (entities/listApi.ts: `/api/v1/{parent.collectionPath}/{id}/{path
  // ?? child.collectionPath}`) without a per-relation lookup table. Every registered entity has
  // one, whether or not anything scopes to it yet.
  collectionPath: string;
  // Bootstrap icon class matching this entity's AbstractPanel.icon (e.g. "bi bi-arrow-down-square"
  // for Project) — used by EntityListPanel's header, mirroring panel/header/*ListPanelHeader.xhtml
  // (icon + title + count chip), not something list rows/columns already carry.
  icon: string;
  api: {
    list(params: ListParams): Promise<PagedResult<TSummary>>;
    get(id: string | number): Promise<TDetail>;
    // Generic by-field-id write path the list's click-to-edit overlay (CellEditOverlay) uses —
    // optional: an entity with no schema (and therefore no dynamic, form-driven columns to edit)
    // has no need for it, and omitting it simply means the overlay never opens for that entity.
    patchAnswers?(id: string | number, answers: Record<string, AnswerInputBody>): Promise<TDetail>;
    // The "fiche précédente/suivante" pair for this entity (plan: prev/next navigation). Optional:
    // an entity type that doesn't declare it renders no arrows at all — EntityDetailPanel checks
    // for its presence before even querying, matching how `patchAnswers` gates the edit overlay.
    siblings?(id: string | number, ctx: { organizationId?: number }): Promise<EntitySiblings>;
    // The titlebar's "Dupliquer" (JSF's panelModel.canDuplicate() — recording units only today).
    // Resolves to the copy, which the panel then opens in the overview like JSF does.
    duplicate?(id: string | number): Promise<TDetail & { id: string | number }>;
  };
  list: {
    // Pinned, hand-written columns — structural ones with no field-catalog equivalent (an
    // identifier chip that's also a navigation link, a relation count) — always rendered first,
    // never toggleable.
    columns: ColumnDef<TSummary>[];
    // Optional: entities with a form-driven column set (Project's ~25 ActionUnitForm fields)
    // supply this to unlock dynamic columns, the column toggler, and the answers projection.
    // Omitted entirely, EntityListPanel behaves exactly as it did before this existed — pinned
    // columns only, no toggler, no `fields=` param.
    schema?: {
      // `scope` is threaded through from EntityListPanel's own `scope` prop when present (a
      // catalog can be parent-scoped rather than organization-scoped — RecordingUnit's
      // recording-unit-types catalog lives at GET /api/v1/projects/{id}/recording-unit-types, not
      // under the organization).
      load: (ctx: { organizationId?: number; scope?: ListScope }) => Promise<FieldCatalog>;
    };
    defaultSort?: string;
    searchable: boolean;
    // An overlay-hosted creation form for this entity's OWN list toolbar "Créer" button
    // (deliberately an overlay, not a JSF-style modal dialog — see CreateFormContext's own
    // rationale). Optional: an entity that doesn't supply this keeps EntityListPanel's older
    // behavior exactly — the `onCreate` prop (bridged to the legacy JSF new-unit dialog) is used
    // instead, unchanged, so migrating one entity's create flow to React never touches another's.
    createForm?: (ctx: CreateFormContext) => ReactNode;
    // When set, `createForm` needs a parent scope (a project): on the unscoped list the "Créer"
    // button is shown disabled with this message instead — JSF's own
    // ToolbarCreateConfig.unavailableMessageKeySupplier for the same lists.
    createRequiresScope?: string;
  };
  detail: {
    tabs: DetailTabDef<TDetail>[];
    // Content for the panel's own header (icon/identifier/type/name/location chips), matching
    // actionUnitPanelHeader.xhtml — rendered inside EntityDetailPanel's PrimeReact <Panel> header,
    // alongside the toolbar (icons), NOT inside any one tab. Optional: a bare entity type with no
    // header content just gets the toolbar alone.
    header?: (entity: TDetail, helpers: DetailTabHelpers) => ReactNode;
    // Derives the overview pane's PanelToolbarSlot.chrome client-side (plan §8 phase 5), for a
    // detail opened via EntityListPanel's onOpenOverview rather than seeded from MountOptions —
    // there is no server round-trip to build it from. Optional: an entity with no overview entry
    // point (nothing calls openOverview for it) doesn't need one.
    chrome?: (entity: TDetail) => PanelChrome;
    // Where the titlebar's "Créer" creates a sibling of this entity (JSF's creationUnitKind button
    // creates the same kind in the same project): the project scope its `list.createForm`
    // expects. Omitted for a top-level kind (Project, Place) — the form then runs unscoped.
    createScope?: (entity: TDetail) => ListScope | undefined;
    // Project only: the id to open the settings page for, when the caller may
    // (`_permissions.canManageSettings`); undefined hides the gear.
    settingsProjectId?: (entity: TDetail) => string | number | undefined;
  };
  routes: {
    list: string;
    detail: (id: string | number) => string;
  };
  // Optional — most future entities won't have a Home presence on day one, unlike list/detail
  // which every registered entity needs.
  home?: {
    widgets: (ctx: HomeWidgetContext) => HomeWidgetDef[];
  };
}
