// A list panel's whole client-side state as one serializable object (plan §f) — paging, sort,
// search, which columns are visible, and (phase 3) per-column filters. Kept separate from
// EntityListPanel's own React state management (useTableState.ts) so the shape can be
// encoded/decoded independently of any hook: a saved view later becomes `POST /ui-views { state }`
// with zero component changes, and a `?s=` URL param makes a list view shareable/restorable
// without wiring full route ownership into this phase.

import type { FilterValue } from "../entities/types";

export type { FilterValue };

export interface TableState {
  // Bumped whenever the shape changes — decodeTableState refuses anything else outright rather
  // than guessing, so a stale `?s=` or saved view degrades to "start over", never to a crash or a
  // silently wrong filter.
  v: 1;
  offset: number;
  limit: number;
  sort?: string;
  search?: string;
  // Ordered field ids — omitted entirely (`[]`) means "pinned columns only, no dynamic ones",
  // matching a config with no `list.schema` at all.
  visibleColumns: string[];
  filters: Record<string, FilterValue>;
}

export const DEFAULT_LIMIT = 10;

export function createTableState(overrides: Partial<TableState> = {}): TableState {
  return {
    v: 1,
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

function isFilterValue(value: unknown): value is FilterValue {
  if (value == null || typeof value !== "object") return false;
  const op = (value as { op?: unknown }).op;
  if (op === "contains") return typeof (value as { v?: unknown }).v === "string";
  if (op === "in") return Array.isArray((value as { v?: unknown }).v);
  if (op === "range") return true;
  return false;
}

function isTableState(value: unknown): value is TableState {
  if (value == null || typeof value !== "object") return false;
  const v = value as Record<string, unknown>;
  if (v.v !== 1) return false;
  if (typeof v.offset !== "number" || typeof v.limit !== "number") return false;
  if (!Array.isArray(v.visibleColumns) || !v.visibleColumns.every((c) => typeof c === "string")) return false;
  if (v.filters == null || typeof v.filters !== "object") return false;
  return Object.values(v.filters as Record<string, unknown>).every(isFilterValue);
}

// btoa/atob are ASCII-only; the encodeURIComponent/unescape round trip keeps this safe for
// non-ASCII search text or labels without pulling in a UTF-8 base64 library.
export function encodeTableState(state: TableState): string {
  const json = JSON.stringify(state);
  const base64 = btoa(unescape(encodeURIComponent(json)));
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Tolerant by design: a corrupt or outdated `?s=`/saved view yields `null`, never a thrown error. */
export function decodeTableState(encoded: string): TableState | null {
  try {
    const padded = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(escape(atob(padded)));
    const parsed: unknown = JSON.parse(json);
    return isTableState(parsed) ? parsed : null;
  } catch {
    return null;
  }
}
