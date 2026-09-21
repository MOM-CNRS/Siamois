// Generic entity-type abstraction (plan §3). EntityListPanel/EntityDetailPanel/HomePanel consume
// only this + the field-renderer registry — no per-entity branching in the generic components.
// Permission flags are deliberately NOT part of this config: they come straight off each API
// response's own `_permissions` object, never recomputed or guessed client-side.
import type { ReactNode } from "react";

export interface PagedResult<T> {
  data: T[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface ListParams {
  offset: number;
  limit: number;
  search?: string;
  sort?: string;
  organizationId?: number;
}

export interface ColumnDef<TSummary> {
  key: string;
  header: string;
  render: (row: TSummary) => ReactNode;
  sortable?: boolean;
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
  };
  list: {
    columns: ColumnDef<TSummary>[];
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
