import { useCallback, useMemo } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { queryKeys } from "../../../api/queryKeys";
import { usePaneState } from "../../../panels/paneState";
import { DEFAULT_LIMIT, type FilterValue } from "../../../panels/tableState";
import { getRelationList, type RelationListParams } from "./api";
import type { RelationKind, RelationRow } from "./types";

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
export function useRelationList(unitId: string | number): RelationListModel {
  const [state, setState] = usePaneState<ListState>("relationList", INITIAL);
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
