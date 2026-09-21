import { useCallback, useRef, useState } from "react";
import { createTableState, type FilterValue, type TableState } from "./tableState";

export interface UseTableStateOptions {
  defaultSort?: string;
}

/**
 * Owns one EntityListPanel's TableState and the handful of mutations its toolbar/DataTable trigger.
 * A thin wrapper rather than TableState's own concern, so tableState.ts stays a pure, hook-free
 * shape usable from tests, saved views, and URL encode/decode alike.
 */
export function useTableState({ defaultSort }: UseTableStateOptions) {
  const [state, setState] = useState<TableState>(() => createTableState({ sort: defaultSort }));

  const setPage = useCallback((offset: number, limit: number) => {
    setState((s) => ({ ...s, offset, limit }));
  }, []);

  const setSort = useCallback((sort: string | undefined) => {
    setState((s) => ({ ...s, sort }));
  }, []);

  const setSearch = useCallback((search: string) => {
    setState((s) => {
      const next = search || undefined;
      // Skip the update (and the offset reset it implies) when the debounced value settles back
      // to what it already was — avoids an extra query firing on every keystroke's trailing edge.
      if (next === s.search) return s;
      return { ...s, search: next, offset: 0 };
    });
  }, []);

  const setVisibleColumns = useCallback((visibleColumns: string[]) => {
    setState((s) => ({ ...s, visibleColumns }));
  }, []);

  const setFilters = useCallback((filters: Record<string, FilterValue>) => {
    setState((s) => ({ ...s, filters, offset: 0 }));
  }, []);

  // Seeds visibleColumns from the schema's own defaults, once — never overwrites a set the user
  // (or a restored view) already populated, including an explicit "hide everything" (`[]` is not
  // empty in the sense this guards against; only "never touched" is).
  const seededRef = useRef(false);
  const seedVisibleColumns = useCallback((fieldIds: string[]) => {
    if (seededRef.current) return;
    seededRef.current = true;
    setState((s) => ({ ...s, visibleColumns: fieldIds }));
  }, []);

  return { state, setPage, setSort, setSearch, setVisibleColumns, setFilters, seedVisibleColumns };
}
