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

// Passed to a tab's render alongside the entity (plan §8 phase 6) — currently just `refetch`, so
// a tab that mutates the entity (Project's fiche: field edits, identifier rename) can ask
// EntityDetailPanel's own query to reload rather than each tab wiring its own cache invalidation.
export interface DetailTabHelpers {
  refetch: () => void;
}

export interface DetailTabDef<TDetail> {
  key: string;
  label: string;
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
}

export interface EntityTypeConfig<TSummary = unknown, TDetail = unknown> {
  key: string;
  labels: { singular: string; plural: string };
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
      load: (ctx: { organizationId?: number }) => Promise<FieldCatalog>;
    };
    defaultSort?: string;
    searchable: boolean;
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
