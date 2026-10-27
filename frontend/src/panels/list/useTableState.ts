import {useCallback, useRef, useState} from "react";
import {usePaneState} from "../pane/paneState";
import {createTableState, type FilterValue, type TableState} from "./tableState";
import {loadListPrefs, saveListPrefs} from "./listPreferences";
import {parsePersisted, type PersistedListState} from "./listState";
import type {ListViewMode} from "./listViews";
import type {ViewStateBody} from "../../api/resources/views";

export interface UseTableStateOptions {
  defaultSort?: string;
  // listPreferences key: when set, the visible columns (and their order) are restored from and
  // saved to this browser's storage.
  prefsKey?: string;
  // The display mode is the kind of list's (prefsKey), not this pane's: a saved view keeps its own.
  shareViewMode?: boolean;
  // The user's state on this list, kept server-side (useUserViewState): applied once, when it has
  // been read. Omitted: nothing to wait for, nothing to apply.
  userState?: { settled: boolean; saved: ViewStateBody | undefined };
}

/**
 * Owns one EntityListPanel's TableState and the handful of mutations its toolbar/DataTable trigger.
 * A thin wrapper rather than TableState's own concern, so tableState.ts stays a pure, hook-free
 * shape usable from tests, saved views, and URL encode/decode alike.
 */
export function useTableState({ defaultSort, prefsKey, shareViewMode, userState }: UseTableStateOptions) {
  const [state, setState] = usePaneState<TableState>("table", () => createTableState({ sort: defaultSort }));

  // The user's saved state is applied once per pane (a list mounting again in focus mode keeps what
  // it showed). Until then the list holds its first request, so it doesn't fetch once with the
  // defaults and again with the saved state. Rendered during the settle, not in an effect, so that
  // the state and the flag change in the same commit.
  const [userStateApplied, setUserStateApplied] = usePaneState("userStateApplied", false);
  // What the user left on this list is where a list starts when it is opened with no context. A list that
  // is retargeted while mounted (the arrows, another row of the main list: the fiche moves on to another
  // entity) keeps what it shows instead, so the fiche stays consistent with the navigation; only the
  // display mode of the kind of list is looked up, for a list the user never arranged.
  const [appliedOnce, setAppliedOnce] = useState(false);
  const waitingForUserState = userState != null && !userStateApplied;
  const persisted = userState ? parsePersisted(userState.saved) : undefined;
  if (waitingForUserState && userState.settled) {
    setUserStateApplied(true);
    if (!appliedOnce) {
      setAppliedOnce(true);
      const rememberedMode = shareViewMode && prefsKey ? loadListPrefs(prefsKey).viewMode : undefined;
      if (persisted || rememberedMode) {
        setState((s) => ({
          ...s,
          limit: persisted?.limit ?? s.limit,
          sort: persisted?.sort ?? s.sort,
          filters: persisted?.filters ?? s.filters,
          viewMode: persisted?.viewMode ?? rememberedMode ?? s.viewMode,
          offset: 0,
        }));
      }
    }
  }
  const userStateReady = userState == null || userStateApplied;
  // The user's own columns, for the seeding below; a ref, as they are read once, when the catalog arrives.
  const savedColumnsRef = useRef<string[] | undefined>(undefined);
  savedColumnsRef.current = persisted?.visibleColumns;

  // A new result set (sort, search, filters) starts back at the first page; the page the user was
  // on belonged to the previous one. Showing or hiding columns keeps the page.
  const setPage = useCallback((offset: number, limit: number) => {
    setState((s) => ({ ...s, offset, limit }));
  }, [setState]);

  const setSort = useCallback((sort: string | undefined) => {
    setState((s) => ({ ...s, sort, offset: 0 }));
  }, [setState]);

  const setSearch = useCallback((search: string) => {
    setState((s) => {
      const next = search || undefined;
      // Skip the update when the debounced value settles back to what it already was — avoids an
      // extra query firing on every keystroke's trailing edge.
      if (next === s.search) return s;
      return { ...s, search: next, offset: 0 };
    });
  }, [setState]);

  const setVisibleColumns = useCallback(
    (visibleColumns: string[]) => {
      setState((s) => ({ ...s, visibleColumns }));
      if (prefsKey) saveListPrefs(prefsKey, { visibleColumns });
    },
    [prefsKey, setState],
  );

  const setFilters = useCallback((filters: Record<string, FilterValue>) => {
    setState((s) => ({ ...s, filters, offset: 0 }));
  }, [setState]);

  // Also remembered for the kind of list (listPreferences), so the next fiche's tab opens in the same mode.
  const setViewMode = useCallback((viewMode: ListViewMode) => {
    setState((s) => (s.viewMode === viewMode ? s : { ...s, viewMode }));
    if (prefsKey && shareViewMode) saveListPrefs(prefsKey, { viewMode });
  }, [prefsKey, shareViewMode, setState]);

  // Seeds visibleColumns once — never overwrites a set the user (or a restored view) already
  // populated, including an explicit "hide everything" (`[]` is not empty in the sense this guards
  // against; only "never touched" is). The user's saved arrangement wins, then this browser's
  // (listPreferences, what was kept before it moved server-side), then the schema's own defaults,
  // minus any column the catalog no longer has (`knownFieldIds`).
  // Also exposed as state: a list with a schema holds its first request until the columns are
  // known, so it doesn't fetch once without them and again with them. Remembered with the table
  // state, so a list mounting again (focus mode) keeps its columns instead of re-seeding them.
  const [columnsSeeded, setColumnsSeeded] = usePaneState("columnsSeeded", false);
  const seededRef = useRef(columnsSeeded);
  // Bumped by `reset`, so whoever seeds the columns (an effect keyed on `seedVisibleColumns`) runs again.
  const [resetNonce, setResetNonce] = useState(0);
  const seedVisibleColumns = useCallback(
    (fieldIds: string[], knownFieldIds?: string[]) => {
      if (seededRef.current || !userStateReady) return;
      seededRef.current = true;
      const saved = savedColumnsRef.current ?? (prefsKey ? loadListPrefs(prefsKey).visibleColumns : undefined);
      const known = knownFieldIds ? new Set(knownFieldIds) : undefined;
      const visibleColumns = saved ? saved.filter((id) => !known || known.has(id)) : fieldIds;
      setState((s) => ({ ...s, visibleColumns }));
      setColumnsSeeded(true);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [prefsKey, setState, setColumnsSeeded, userStateReady, resetNonce],
  );

  // Back to `base` — a saved view's own state, or nothing for the default list: the defaults, over which
  // base applies. The columns are seeded again (from base, else the catalog) once the catalog is read.
  const reset = useCallback(
    (base?: PersistedListState) => {
      savedColumnsRef.current = base?.visibleColumns;
      seededRef.current = false;
      setColumnsSeeded(false);
      setState(() => {
        const fresh = createTableState({ sort: base?.sort ?? defaultSort, filters: base?.filters ?? {} });
        if (base?.limit !== undefined) fresh.limit = base.limit;
        if (base?.viewMode !== undefined) fresh.viewMode = base.viewMode;
        return fresh;
      });
      setResetNonce((n) => n + 1);
    },
    [defaultSort, setColumnsSeeded, setState],
  );

  return {
    state,
    setPage,
    setSort,
    setSearch,
    setVisibleColumns,
    setFilters,
    setViewMode,
    seedVisibleColumns,
    columnsSeeded,
    userStateReady,
    reset,
  };
}
