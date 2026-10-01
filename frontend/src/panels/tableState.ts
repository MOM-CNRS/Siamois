// A list panel's whole client-side state as one serializable object (plan §f) — paging, sort,
// search, which columns are visible, and (phase 3) per-column filters. Kept separate from
// EntityListPanel's own React state management (useTableState.ts) so the shape can be
// encoded/decoded independently of any hook: a saved view later becomes `POST /ui-views { state }`
// with zero component changes, and a `?s=` URL param makes a list view shareable/restorable
// without wiring full route ownership into this phase.

import type { FilterValue } from "../entities/types";

export type { FilterValue };

export interface TableState {
  v: 3;
  // Always a multiple of `limit`: the list endpoints page with PageRequest.of(offset / limit, limit)
  // and reject any other offset (ProjectApiService#validatePagedListRequest).
  offset: number;
  limit: number;
  sort?: string;
  search?: string;
  // Ordered field ids — omitted entirely (`[]`) means "pinned columns only, no dynamic ones",
  // matching a config with no `list.schema` at all.
  visibleColumns: string[];
  filters: Record<string, FilterValue>;
}

// Rows-per-page choices; the server caps a page at 200 (ProjectApiService.MAX_PAGE_SIZE).
export const PAGE_SIZE_OPTIONS = [25, 50, 100, 200];
export const DEFAULT_LIMIT = 25;

export function createTableState(overrides: Partial<TableState> = {}): TableState {
  return {
    v: 3,
    offset: 0,
    limit: DEFAULT_LIMIT,
    visibleColumns: [],
    filters: {},
    ...overrides,
  };
}

/**
 * Encodes `filters` as the `f.*` query-param contract phase 3 wires GET /api/v1/projects to accept:
 * repeatable `f.<key>` for "in", a bare `f.<key>` for "contains", `f.<key>.from`/`.to` for a range.
 * Exposed now (unused by the API today, phase 2 ships no filter UI) so the wire contract and the
 * state shape that will drive it land together, rather than the contract being invented later
 * against whatever shape `filters` happened to have by then.
 */
export function filtersToQueryParams(filters: Record<string, FilterValue>): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, filter] of Object.entries(filters)) {
    switch (filter.op) {
      case "contains":
        if (filter.v) params.set(`f.${key}`, filter.v);
        break;
      case "in":
        for (const v of filter.v) params.append(`f.${key}`, v);
        break;
      case "range":
        if (filter.from) params.set(`f.${key}.from`, filter.from);
        if (filter.to) params.set(`f.${key}.to`, filter.to);
        break;
    }
  }
  return params;
}
