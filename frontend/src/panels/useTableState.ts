import { useCallback, useRef, useState } from "react";
import { createTableState, type FilterValue, type TableState } from "./tableState";
import { loadListPrefs, saveListPrefs } from "./listPreferences";

export interface UseTableStateOptions {
  defaultSort?: string;
  // listPreferences key: when set, the visible columns (and their order) are restored from and
  // saved to this browser's storage.
  prefsKey?: string;
}

/**
 * Owns one EntityListPanel's TableState and the handful of mutations its toolbar/DataTable trigger.
 * A thin wrapper rather than TableState's own concern, so tableState.ts stays a pure, hook-free
 * shape usable from tests, saved views, and URL encode/decode alike.
 */
export function useTableState({ defaultSort, prefsKey }: UseTableStateOptions) {
  const [state, setState] = useState<TableState>(() => createTableState({ sort: defaultSort }));

  const setSort = useCallback((sort: string | undefined) => {
    setState((s) => ({ ...s, sort }));
  }, []);

  const setSearch = useCallback((search: string) => {
    setState((s) => {
      const next = search || undefined;
      // Skip the update when the debounced value settles back to what it already was — avoids an
      // extra query firing on every keystroke's trailing edge.
      if (next === s.search) return s;
      return { ...s, search: next };
    });
  }, []);

  const setVisibleColumns = useCallback(
    (visibleColumns: string[]) => {
      setState((s) => ({ ...s, visibleColumns }));
      if (prefsKey) saveListPrefs(prefsKey, { visibleColumns });
    },
    [prefsKey],
  );

  const setFilters = useCallback((filters: Record<string, FilterValue>) => {
    setState((s) => ({ ...s, filters }));
  }, []);

  // Seeds visibleColumns once — never overwrites a set the user (or a restored view) already
  // populated, including an explicit "hide everything" (`[]` is not empty in the sense this guards
  // against; only "never touched" is). A saved arrangement wins over the schema's own defaults,
  // minus any column the catalog no longer has (`knownFieldIds`).
  const seededRef = useRef(false);
  // Also exposed as state: a list with a schema holds its first request until the columns are
  // known, so it doesn't fetch once without them and again with them.
  const [columnsSeeded, setColumnsSeeded] = useState(false);
  const seedVisibleColumns = useCallback(
    (fieldIds: string[], knownFieldIds?: string[]) => {
      if (seededRef.current) return;
      seededRef.current = true;
      const saved = prefsKey ? loadListPrefs(prefsKey).visibleColumns : undefined;
      const known = knownFieldIds ? new Set(knownFieldIds) : undefined;
      const visibleColumns = saved ? saved.filter((id) => !known || known.has(id)) : fieldIds;
      setState((s) => ({ ...s, visibleColumns }));
      setColumnsSeeded(true);
    },
    [prefsKey],
  );

  return { state, setSort, setSearch, setVisibleColumns, setFilters, seedVisibleColumns, columnsSeeded };
}
