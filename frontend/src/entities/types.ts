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

export interface DetailTabDef<TDetail> {
  key: string;
  label: string;
  render: (entity: TDetail) => ReactNode;
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
}
