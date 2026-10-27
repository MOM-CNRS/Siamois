import {useCallback, useMemo, useState} from "react";
import {useQueries} from "@tanstack/react-query";
import {queryKeys} from "../../../api/queryKeys";
import {usePersistedPaneState} from "../../../panels/pane/persistedPaneState";
import {type FilterValue, SCROLL_CHUNK_SIZE} from "../../../panels/list/tableState";
import {pagesInRange} from "../../../panels/list/usePagedList";
import {getRelationList, type RelationListParams} from "./api";
import type {RelationKind, RelationList, RelationRow} from "./types";

// What the list is showing: the same shape every list of the app remembers.
interface ListState {
  search: string;
  filters: Record<string, FilterValue>;
  sort: string;
}

const INITIAL: ListState = { search: "", filters: {}, sort: "kind:asc" };

export const KIND_FILTER = "kind";

// The kinds the filter keeps (all of them when it has none).
function kindsOf(filters: Record<string, FilterValue>): RelationKind[] {
  const filter = filters[KIND_FILTER];
  return filter?.op === "in" ? (filter.v as RelationKind[]) : [];
}

export interface RelationListModel extends ListState {
  // The whole result set, in order (virtual scroll): a relation whose page the scroll hasn't reached yet is null.
  rows: (RelationRow | null)[];
  total: number;
  isLoading: boolean;
  isFetching: boolean;
  error: unknown;
  setSearch: (search: string) => void;
  setFilters: (filters: Record<string, FilterValue>) => void;
  setSort: (sort: string | undefined) => void;
  // The rows on screen (and around), as the table's virtual scroller reports them: the pages to fetch.
  setScrollWindow: (first: number, last: number) => void;
}

// The list of a unit's relations: what it shows (search, filters, sort — remembered by the pane, like any
// list) and the relations that match, fetched page by page as the table scrolls to them; while a new
// search, filter or sort loads, the rows on screen stay.
const RELATION_KINDS = ["parents", "children", "anterior", "posterior", "synchronous"] as const satisfies readonly RelationKind[];
// The columns of the list the server can order by (RelationsList's sortable columns).
const SORTABLE_COLUMNS = ["kind", "label"] as const;

// Saved filters and sort, read back only if they still have the shape and values the list offers now:
// anything else (an older shape) opens the list as it is by default. A page size kept before the list
// scrolled virtually is ignored.
export function isSavedRelationListState(saved: unknown): boolean {
  if (saved == null || typeof saved !== "object") return false;
  const { filters, sort } = saved as Record<string, unknown>;
  if (filters == null || typeof filters !== "object" || Array.isArray(filters)) return false;
  const kind = (filters as Record<string, FilterValue | undefined>)[KIND_FILTER];
  const kindsKnown = kind == null || (kind.op === "in" && kind.v.every((k) => (RELATION_KINDS as readonly string[]).includes(k)));
  const [sortKey, direction] = typeof sort === "string" ? sort.split(":") : [];
  return (
    kindsKnown &&
    (SORTABLE_COLUMNS as readonly string[]).includes(sortKey) && (direction === "asc" || direction === "desc")
  );
}

export function useRelationList(unitId: string | number): RelationListModel {
  // Kept for the user like any list's arrangement (filters, sort) — not the search text.
  const [state, setState] = usePersistedPaneState<ListState, Pick<ListState, "filters" | "sort">>(
    "relationList",
    INITIAL,
    {
      toSaved: ({ filters, sort }) => ({ filters, sort }),
      fromSaved: (saved, initial) => ({ ...initial, filters: saved.filters, sort: saved.sort }),
      isValid: isSavedRelationListState,
    },
  );
  const params: Omit<RelationListParams, "offset" | "limit"> = useMemo(
    () => ({ kinds: kindsOf(state.filters), search: state.search || undefined, sort: state.sort }),
    [state],
  );

  // The rows the scroller shows, for this result set: a new search, filter or sort starts again from the top.
  const resultSetKey = JSON.stringify(params);
  const [scrollWindow, setWindow] = useState({ key: resultSetKey, first: 0, last: SCROLL_CHUNK_SIZE });
  const shownWindow = scrollWindow.key === resultSetKey ? scrollWindow : { first: 0, last: SCROLL_CHUNK_SIZE };
  const pages = pagesInRange(shownWindow.first, shownWindow.last, SCROLL_CHUNK_SIZE);
  const results = useQueries({
    queries: pages.map((page) => {
      const pageParams: RelationListParams = { ...params, offset: page * SCROLL_CHUNK_SIZE, limit: SCROLL_CHUNK_SIZE };
      return {
        queryKey: queryKeys.relationList(unitId, pageParams),
        queryFn: () => getRelationList(unitId, pageParams),
        // The same page of the previous result set stays until the new one arrives; another page's rows
        // would show at the wrong place.
        placeholderData: (previous: RelationList | undefined, previousQuery?: { queryKey: readonly unknown[] }) =>
          (previousQuery?.queryKey[previousQuery.queryKey.length - 1] as RelationListParams | undefined)?.offset === pageParams.offset ? previous : undefined,
      };
    }),
  });

  // Rebuilt only when a page settles: useQueries hands back new result objects on every render.
  const signature = `${resultSetKey}|${pages.join(",")}|${results.map((r) => `${r.dataUpdatedAt}:${r.isPlaceholderData ? 1 : 0}`).join(",")}`;
  const { rows, total } = useMemo(() => {
    const first = results.find((r) => r.data != null)?.data;
    if (!first) return { rows: [], total: 0 };
    const all: (RelationRow | null)[] = Array.from({ length: first.meta.total }, () => null);
    results.forEach((r, i) => r.data?.data.forEach((row, j) => (all[pages[i] * SCROLL_CHUNK_SIZE + j] = row)));
    all.length = first.meta.total;
    return { rows: all, total: first.meta.total };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);

  const setSearch = useCallback((search: string) => setState((s) => ({ ...s, search })), [setState]);
  const setFilters = useCallback((filters: Record<string, FilterValue>) => setState((s) => ({ ...s, filters })), [setState]);
  const setSort = useCallback((sort: string | undefined) => setState((s) => ({ ...s, sort: sort ?? INITIAL.sort })), [setState]);
  const setScrollWindow = useCallback(
    (first: number, last: number) =>
      setWindow((current) =>
        current.key === resultSetKey && current.first === first && current.last === last ? current : { key: resultSetKey, first, last },
      ),
    [resultSetKey],
  );

  return {
    ...state,
    rows,
    total,
    isLoading: !results.some((r) => r.data != null) && results.some((r) => r.isLoading),
    isFetching: results.some((r) => r.isFetching),
    error: results.find((r) => r.error != null)?.error ?? null,
    setSearch,
    setFilters,
    setSort,
    setScrollWindow,
  };
}
