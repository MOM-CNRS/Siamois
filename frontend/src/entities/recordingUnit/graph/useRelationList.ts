import {useCallback, useMemo} from "react";
import {keepPreviousData, useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../../api/queryKeys";
import {usePersistedPaneState} from "../../../panels/pane/persistedPaneState";
import {
    DEFAULT_LIMIT,
    type FilterValue,
    LEGACY_DEFAULT_LIMIT,
    PAGE_SIZE_OPTIONS
} from "../../../panels/list/tableState";
import {getRelationList, type RelationListParams} from "./api";
import type {RelationKind, RelationRow} from "./types";

// What the list is showing: the same shape every list of the app remembers.
interface ListState {
  search: string;
  filters: Record<string, FilterValue>;
  sort: string;
  offset: number;
  limit: number;
}

const INITIAL: ListState = { search: "", filters: {}, sort: "kind:asc", offset: 0, limit: DEFAULT_LIMIT };

export const KIND_FILTER = "kind";

// The kinds the filter keeps (all of them when it has none).
function kindsOf(filters: Record<string, FilterValue>): RelationKind[] {
  const filter = filters[KIND_FILTER];
  return filter?.op === "in" ? (filter.v as RelationKind[]) : [];
}

export interface RelationListModel extends ListState {
  rows: RelationRow[];
  total: number;
  isLoading: boolean;
  isFetching: boolean;
  error: unknown;
  setSearch: (search: string) => void;
  setFilters: (filters: Record<string, FilterValue>) => void;
  setSort: (sort: string | undefined) => void;
  setPage: (offset: number, limit: number) => void;
}

// The list of a unit's relations: what it shows (search, filters, sort, page — remembered by the pane,
// like any list) and the page that matches, the previous one staying on screen while the next loads.
const RELATION_KINDS = ["parents", "children", "anterior", "posterior", "synchronous"] as const satisfies readonly RelationKind[];
// The columns of the list the server can order by (RelationsList's sortable columns).
const SORTABLE_COLUMNS = ["kind", "label"] as const;

// Saved filters, sort and page size, read back only if they still have the shape and values the list
// offers now: anything else (an older shape, a size no longer offered) opens the list as it is by default.
export function isSavedRelationListState(saved: unknown): boolean {
  if (saved == null || typeof saved !== "object") return false;
  const { filters, sort, limit } = saved as Record<string, unknown>;
  if (filters == null || typeof filters !== "object" || Array.isArray(filters)) return false;
  const kind = (filters as Record<string, FilterValue | undefined>)[KIND_FILTER];
  const kindsKnown = kind == null || (kind.op === "in" && kind.v.every((k) => (RELATION_KINDS as readonly string[]).includes(k)));
  const [sortKey, direction] = typeof sort === "string" ? sort.split(":") : [];
  return (
    kindsKnown &&
    (SORTABLE_COLUMNS as readonly string[]).includes(sortKey) && (direction === "asc" || direction === "desc") &&
    typeof limit === "number" && PAGE_SIZE_OPTIONS.includes(limit)
  );
}

export function useRelationList(unitId: string | number): RelationListModel {
  // Kept for the user like any list's arrangement (filters, sort, page size) — not the search text nor the page.
  const [state, setState] = usePersistedPaneState<ListState, Pick<ListState, "filters" | "sort" | "limit">>(
    "relationList",
    INITIAL,
    {
      toSaved: ({ filters, sort, limit }) => ({ filters, sort, limit }),
      fromSaved: (saved, initial) => ({ ...initial, ...saved, limit: saved.limit === LEGACY_DEFAULT_LIMIT ? initial.limit : saved.limit }),
      isValid: isSavedRelationListState,
    },
  );
  const params: RelationListParams = useMemo(
    () => ({ kinds: kindsOf(state.filters), search: state.search || undefined, sort: state.sort, offset: state.offset, limit: state.limit }),
    [state],
  );
  const query = useQuery({
    queryKey: queryKeys.relationList(unitId, params),
    queryFn: () => getRelationList(unitId, params),
    placeholderData: keepPreviousData,
  });

  // A new search, filter or sort starts again from the first page.
  const setSearch = useCallback((search: string) => setState((s) => ({ ...s, search, offset: 0 })), [setState]);
  const setFilters = useCallback((filters: Record<string, FilterValue>) => setState((s) => ({ ...s, filters, offset: 0 })), [setState]);
  const setSort = useCallback((sort: string | undefined) => setState((s) => ({ ...s, sort: sort ?? INITIAL.sort, offset: 0 })), [setState]);
  const setPage = useCallback((offset: number, limit: number) => setState((s) => ({ ...s, offset, limit })), [setState]);

  return {
    ...state,
    rows: query.data?.data ?? [],
    total: query.data?.meta.total ?? 0,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    error: query.error,
    setSearch,
    setFilters,
    setSort,
    setPage,
  };
}
