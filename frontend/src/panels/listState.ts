import type { ViewStateBody, ViewTableState } from "../api/views";
import { LEGACY_DEFAULT_LIMIT, PAGE_SIZE_OPTIONS, type FilterValue, type TableState } from "./tableState";
import type { ListViewMode } from "./list/listViews";

// What of a list's TableState is kept for the user (GET/PUT /api/v1/view-states), and how it reads
// back. The page (`offset`) and the search text are deliberately not: coming back to page 7 of
// yesterday's search is a surprise, coming back to yesterday's filters and sort is the point.

export interface PersistedListState {
  limit?: number;
  sort?: string;
  filters?: Record<string, FilterValue>;
  visibleColumns?: string[];
  viewMode?: ListViewMode;
}

const VERSION = 1;

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((v) => typeof v === "string");
}

export function isFilterValue(value: unknown): value is FilterValue {
  if (value == null || typeof value !== "object") return false;
  const f = value as Record<string, unknown>;
  switch (f.op) {
    case "contains":
      return typeof f.v === "string";
    case "in":
      return isStringArray(f.v);
    case "range":
      return (f.from === undefined || typeof f.from === "string") && (f.to === undefined || typeof f.to === "string");
    default:
      return false;
  }
}

/** Tolerant: a state from another version, or hand-edited, reads as the parts that are valid. */
export function parsePersisted(raw: ViewStateBody | undefined): PersistedListState | undefined {
  if (raw == null || raw.v !== VERSION) return undefined;
  const parsed: PersistedListState = {};
  // Only a size the list still offers: the server refuses any other (a page is capped, offset is a multiple of it).
  // (A kept 25 is the former default, not a choice: see LEGACY_DEFAULT_LIMIT.)
  if (typeof raw.limit === "number" && PAGE_SIZE_OPTIONS.includes(raw.limit) && raw.limit !== LEGACY_DEFAULT_LIMIT) parsed.limit = raw.limit;
  if (typeof raw.sort === "string" && raw.sort) parsed.sort = raw.sort;
  if (isStringArray(raw.visibleColumns)) parsed.visibleColumns = raw.visibleColumns;
  if (raw.viewMode === "list" || raw.viewMode === "tree" || raw.viewMode === "cards") parsed.viewMode = raw.viewMode;
  if (raw.filters != null && typeof raw.filters === "object" && !Array.isArray(raw.filters)) {
    const filters: Record<string, FilterValue> = {};
    for (const [key, value] of Object.entries(raw.filters)) {
      if (isFilterValue(value)) filters[key] = value;
    }
    parsed.filters = filters;
  }
  return parsed;
}

/**
 * The part of `state` worth keeping. The columns are left out until they are seeded: before that
 * `visibleColumns` is the empty placeholder, not a choice of the user.
 */
export function toPersisted(state: TableState, columnsSeeded: boolean): ViewStateBody {
  const body: ViewStateBody = { v: VERSION, limit: state.limit, filters: state.filters };
  if (state.sort !== undefined) body.sort = state.sort;
  if (state.viewMode !== undefined) body.viewMode = state.viewMode;
  if (columnsSeeded) body.visibleColumns = state.visibleColumns;
  return body;
}

/**
 * A saved view's state as a saved list state (what `parsePersisted` reads), so a view and what the user
 * changed on it merge key by key. A view has no page and no search to apply: they are not part of it.
 */
export function viewToBody(view: ViewTableState): ViewStateBody {
  const body: ViewStateBody = { v: VERSION };
  if (view.limit !== undefined) body.limit = view.limit;
  if (view.sort !== undefined) body.sort = view.sort;
  if (view.visibleColumns !== undefined) body.visibleColumns = view.visibleColumns;
  if (view.viewMode !== undefined) body.viewMode = view.viewMode;
  if (view.listFilters !== undefined) body.filters = view.listFilters;
  return body;
}

/** The list's state as a saved view stores it (TableViewState v2). */
export function toViewState(state: TableState, columnsSeeded: boolean): ViewTableState {
  const view: ViewTableState = { version: 2, limit: state.limit, listFilters: state.filters };
  if (state.sort !== undefined) view.sort = state.sort;
  if (state.viewMode !== undefined) view.viewMode = state.viewMode;
  if (columnsSeeded) view.visibleColumns = state.visibleColumns;
  return view;
}

/**
 * Whether the list is no longer as `base` (a saved view's own state) has it. Only what the view says
 * counts: a view that doesn't name its columns doesn't care which are shown.
 */
export function differsFromView(state: TableState, base: PersistedListState | undefined): boolean {
  if (base == null) return false;
  const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
  if (base.limit !== undefined && base.limit !== state.limit) return true;
  if (base.sort !== undefined && base.sort !== state.sort) return true;
  if (base.visibleColumns !== undefined && !same(base.visibleColumns, state.visibleColumns)) return true;
  if (base.viewMode !== undefined && base.viewMode !== (state.viewMode ?? "list")) return true;
  return !same(base.filters ?? {}, state.filters);
}
