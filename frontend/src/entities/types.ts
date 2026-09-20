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
// MountOptions, onNavigate from the same bridge EntityListPanel already uses, so a "recent
// projects" widget's rows link to the real detail route the same way list rows do.
export interface HomeWidgetContext {
  organizationId?: number;
  onNavigate?: (entityType: string, id: string | number) => void;
}

export interface HomeWidgetDef {
  key: string;
  render: () => ReactNode;
}

export interface EntityTypeConfig<TSummary = unknown, TDetail = unknown> {
  key: string;
  labels: { singular: string; plural: string };
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
