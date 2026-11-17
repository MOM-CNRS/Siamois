import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../api/queryKeys";
import {isOwningProjectField} from "../../fields/types";
import type {EntityTypeConfig, FieldCatalog, FilterValue, ListParams, ListScope, PagedResult} from "../../entities/shared/types";
import {pagesInRange, usePagedList} from "./usePagedList";
import {useRowRules, useRuleFields} from "./useRowRules";
import {useTableState} from "./useTableState";
import {differsFromView, parsePersisted, type PersistedListState, toPersisted, viewToBody} from "./listState";
import {getView, type ViewStateBody} from "../../api/resources/views";
import {type UserViewState, useUserViewState} from "./useUserViewState";
import {availableFilters, isSortAvailable} from "./listStateValidity";
import {usePaneState} from "../pane/paneState";
import {DEFAULT_LIMIT, SCROLL_CHUNK_SIZE, type TableState} from "./tableState";
import {availableViews, resolveViewMode} from "./listViews";
import {type RowTypeRef, useProjectIdsOf, useTypeRules} from "./useTypeRules";

export type RowRecord = Record<string, unknown> & { id?: string | number };

export interface UseEntityListDataOptions {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown> | undefined;
  organizationId?: number;
  scope?: ListScope;
  // Where this list's column arrangement is remembered (listPreferences.ts).
  prefsKey: string;
  // An embedded list's tab badge already is the collection total: no count request of its own.
  embedded?: boolean;
  // The saved view the list shows (its own state is the starting point, the user's changes on it come
  // over it); absent for the default list.
  viewId?: number;
  // A list that is a configured tab of a fiche: the filters that define the tab (always applied, whatever
  // the user filters on top, and not part of what is kept for the user), and the tab's key, which keeps
  // its state apart from the other lists of the same type on that fiche.
  fixedFilters?: Record<string, FilterValue>;
  stateKey?: string;
  // A list whose state is nobody's to keep (the preview of a tab being configured): nothing is read from or
  // written to the user's state; it opens on `initialFilters` instead.
  volatile?: boolean;
  initialFilters?: Record<string, FilterValue>;
}

type ListConfig = EntityTypeConfig<unknown, unknown> | undefined;
type TableControls = ReturnType<typeof useTableState>;
type Filters = Record<string, FilterValue>;
// What makes a result set: a change of any of these starts the list again from its first row.
type ResultSetParams = Omit<ListParams, "offset" | "limit" | "fields">;

/**
 * Everything the list reads from the server and from its own table state: the column catalog, the
 * visible columns, the current page, the rules its cells follow and the collection's size. The
 * panel renders from this and owns no query.
 */
export function useEntityListData({ entityType, config, organizationId, scope, prefsKey, embedded, viewId, fixedFilters, stateKey, volatile, initialFilters }: UseEntityListDataOptions) {
  const viewKey = volatile ? undefined : userStateKey(viewId, entityType, scope, stateKey);
  const userState = useUserViewState(viewKey, organizationId);
  const savedView = useSavedView(organizationId, viewId);
  const startingState = useStartingState(savedView.body, userState.saved, initialFilters);

  const table = useTableState({
    defaultSort: config?.list.defaultSort,
    prefsKey,
    shareViewMode: viewId == null,
    userState: { settled: userState.settled && savedView.settled, saved: startingState },
  });
  const { state, columnsSeeded, userStateReady } = table;
  // How the list is shown (flat list, tree, later a map…): chosen in the gear menu, the flat list
  // staying the default. The tree view fetches its own rows: no flat page meanwhile.
  const views = availableViews(config, scope);
  const viewMode = resolveViewMode(state.viewMode, views, config?.list.defaultView);
  const showTree = viewMode === "tree";

  const { catalog, catalogFailed, hasSchema } = useFieldCatalog(entityType, config, organizationId, scope);
  const pinnedFieldIds = usePinnedFieldIds(config);
  useSeedVisibleColumns(catalog, pinnedFieldIds, scope, table.seedVisibleColumns);

  // A list with a schema waits for its columns, rather than fetching once without them and then adding
  // each one (a failed catalog just means no dynamic columns).
  const columnsReady = !hasSchema || columnsSeeded || catalogFailed;
  const baselineReady = userStateReady && columnsReady;
  const listReady = useKeptStateCheck(table, config, catalog, baselineReady);

  const tabFilters = useTabFilters(fixedFilters, baselineReady, config, catalog);
  const params = resultSetParams(state, organizationId, scope, tabFilters);

  // What the cells' rules read on top of the visible columns (rules/dependencies.ts): fetched with
  // the rows although not shown, so a cell can be greyed by an answer whose column is hidden.
  const shownFieldIds = useMemo(() => [...pinnedFieldIds, ...state.visibleColumns], [pinnedFieldIds, state.visibleColumns]);
  const { typeRules, ruleFields, setSeenRows } = useRuleInputs(config, catalog, shownFieldIds);

  // The flat table scrolls through the whole result set (virtual scroll); the cards (and the tree, which
  // fetches its own rows) keep their paginator, and so the page the table state holds.
  const virtual = viewMode !== "cards" && !showTree;
  const { shownWindow, onScrollWindow } = useScrollWindow(JSON.stringify(params));
  // Only the columns currently on screen are ever requested — the projection (and its label-batch
  // resolution cost server-side) scales with what's visible, not with the whole catalog. They're
  // passed to usePagedList apart from the result-set params: showing a column fetches just that
  // column for the rows already loaded, hiding one fetches nothing.
  const fields = useMemo(() => [...state.visibleColumns, ...ruleFields], [state.visibleColumns, ruleFields]);
  const page = usePagedList<RowRecord>({
    entityType,
    params,
    pages: virtual ? pagesInRange(shownWindow.first, shownWindow.last, SCROLL_CHUNK_SIZE) : [Math.floor(state.offset / state.limit)],
    limit: virtual ? SCROLL_CHUNK_SIZE : state.limit,
    virtual,
    fields,
    fetch: (p) => config!.api.list(p) as Promise<PagedResult<RowRecord>>,
    enabled: !showTree && listReady && config != null && columnsReady,
  });
  const { loadedRows, totalCount } = page;
  useEffect(() => setSeenRows(loadedRows), [loadedRows, setSeenRows]);

  useRecoveryFromRefusedState(page.error, userState, table, config);
  const resetToBase = useKeptUserChanges({ baselineReady, state, columnsSeeded, viewKey, organizationId, userState, table, viewBase: savedView.base });
  const modified = viewId != null && savedView.base != null && listReady && differsFromView(state, savedView.base);
  const hasUserState = userState.saved != null;
  const rowRules = useRowRules(catalog?.fields, shownFieldIds, ruleFields, loadedRows, typeRules);
  const collectionTotal = useCollectionTotal({
    entityType,
    config,
    organizationId,
    scope,
    embedded,
    state,
    viewBase: savedView.base,
    viewSettled: savedView.settled,
    totalCount,
  });

  return { table, onScrollWindow, catalog, hasSchema, pinnedFieldIds, params, tabFilters, page, rowRules, collectionTotal, views, viewMode, userState, listReady, view: savedView.view, viewMissing: savedView.missing, modified, hasUserState, resetToBase };
}

// ─── Where the list starts from ─────────────────────────────────────────────────────────────────

/**
 * The key what this user arranged on the list is kept under, server-side per organization: on a saved view,
 * that view; otherwise the organization-wide list and a relation tab of the same type are arranged
 * independently, and a relation tab is arranged per parent fiche (the units of this project, not of every
 * project), and per configured tab of that fiche.
 */
function userStateKey(viewId: number | undefined, entityType: string, scope: ListScope | undefined, stateKey: string | undefined): string {
  if (viewId != null) return `view:${viewId}`;
  if (!scope) return `ctx:${entityType}:global`;
  const tabKey = stateKey ? `:${stateKey}` : "";
  return `ctx:${entityType}:${scope.entityType}:${scope.id}-tab${tabKey}`;
}

/**
 * The saved view the list shows, when it shows one: its state (as kept and as a list state), whether it has
 * been read, and whether it no longer exists.
 */
function useSavedView(organizationId: number | undefined, viewId: number | undefined) {
  const viewQuery = useQuery({
    queryKey: queryKeys.view(organizationId, viewId),
    queryFn: () => getView(organizationId!, viewId!),
    enabled: viewId != null && organizationId != null,
    staleTime: 60_000,
  });
  const body = useMemo(() => (viewQuery.data ? viewToBody(viewQuery.data.state) : undefined), [viewQuery.data]);
  const base = useMemo(() => parsePersisted(body), [body]);
  return {
    view: viewQuery.data,
    body,
    base,
    settled: viewId == null || organizationId == null || !viewQuery.isPending,
    missing: viewQuery.isError && (viewQuery.error as { status?: unknown } | null)?.status === 404,
  };
}

/**
 * The state the list opens on: a saved view is the starting point, and what the user changed on it comes
 * over it, key by key. A list nobody keeps the state of opens on `initialFilters` instead.
 */
function useStartingState(
  viewBody: ViewStateBody | undefined,
  userSaved: ViewStateBody | undefined,
  initialFilters: Filters | undefined,
): ViewStateBody | undefined {
  const initialSaved = useMemo(() => (initialFilters ? { v: 1, filters: initialFilters } : undefined), [initialFilters]);
  const kept = userSaved ?? initialSaved;
  return viewBody || kept ? { ...viewBody, ...kept } : undefined;
}

// ─── Columns ────────────────────────────────────────────────────────────────────────────────────

/**
 * The list's field catalog, for an entity whose columns come from its forms (`config.list.schema`).
 */
function useFieldCatalog(entityType: string, config: ListConfig, organizationId: number | undefined, scope: ListScope | undefined) {
  const hasSchema = config?.list.schema != null;
  const { data: catalog, isError: catalogFailed } = useQuery({
    queryKey: queryKeys.entityListSchema(entityType, organizationId, scope),
    queryFn: () => config!.list.schema!.load({ organizationId, scope }),
    enabled: hasSchema,
  });
  return { catalog, catalogFailed, hasSchema };
}

/**
 * The catalog fields a pinned column already shows: not offered (or seeded) a second time.
 */
function usePinnedFieldIds(config: ListConfig): Set<string> {
  return useMemo(
    () => new Set((config?.list.columns ?? []).flatMap((c) => (c.fieldId ? [c.fieldId] : []))),
    [config],
  );
}

/**
 * Seeds the toggler the first time the catalog loads — from this browser's saved arrangement if there is
 * one, else from the catalog's own defaults (ActionUnitTableColumnDefaults on the project side) — never
 * again, so a user's toggle isn't clobbered by an organizationId change re-fetching the same catalog.
 */
function useSeedVisibleColumns(
  catalog: FieldCatalog | undefined,
  pinnedFieldIds: Set<string>,
  scope: ListScope | undefined,
  seedVisibleColumns: TableControls["seedVisibleColumns"],
) {
  useEffect(() => {
    if (!catalog) return;
    seedVisibleColumns(
      defaultVisibleColumns(catalog, pinnedFieldIds, scope),
      catalog.columns.map((c) => c.fieldId).filter((id) => !pinnedFieldIds.has(id)),
    );
  }, [catalog, seedVisibleColumns, scope, pinnedFieldIds]);
}

/**
 * The catalog columns shown by default, in their order. The project rows belong to is a column like any
 * other, shown by default only where rows come from several projects: inside a project's own tab it would
 * repeat that project on every row.
 */
function defaultVisibleColumns(catalog: FieldCatalog, pinnedFieldIds: Set<string>, scope: ListScope | undefined): string[] {
  const isProject = (fieldId: string) => {
    const field = catalog.fields[fieldId];
    return field != null && isOwningProjectField(field);
  };
  return catalog.columns
    .filter((c) => !pinnedFieldIds.has(c.fieldId))
    .filter((c) => (isProject(c.fieldId) ? scope == null : c.visible))
    .slice()
    .sort((a, b) => a.order - b.order)
    .map((c) => c.fieldId);
}

// ─── Filters and result set ─────────────────────────────────────────────────────────────────────

/**
 * Once the list's definitions are known (its columns, once the catalog has loaded), what the user kept is
 * checked against them, once: a filter or a sort on a field that no longer exists would be refused by the
 * server (400, « Champ inconnu ») and could not be taken off — it is dropped before the first request.
 * Returns whether the list is ready to fetch.
 */
function useKeptStateCheck(table: TableControls, config: ListConfig, catalog: FieldCatalog | undefined, baselineReady: boolean): boolean {
  const { state } = table;
  const [stateChecked, setStateChecked] = usePaneState("userStateChecked", false);
  if (baselineReady && !stateChecked) {
    setStateChecked(true);
    const filters = availableFilters(state.filters, config, catalog);
    // Compared by keys: a filter may also have moved to the key its column filters by now.
    if (Object.keys(filters).join() !== Object.keys(state.filters).join()) table.setFilters(filters);
    if (!isSortAvailable(state.sort, config, catalog)) table.setSort(config?.list.defaultSort);
  }
  return table.userStateReady && stateChecked;
}

/**
 * The tab's own filters, only those the list still knows (an unknown field is refused by the server, and a
 * filter the user cannot take off would block the tab).
 */
function useTabFilters(
  fixedFilters: Filters | undefined,
  baselineReady: boolean,
  config: ListConfig,
  catalog: FieldCatalog | undefined,
): Filters | undefined {
  return useMemo(
    () => (fixedFilters && baselineReady ? availableFilters(fixedFilters, config, catalog) : undefined),
    [fixedFilters, baselineReady, config, catalog],
  );
}

/**
 * The result set the list shows: the user's search, sort and filters, the tab's own filters coming over the
 * user's on the same field.
 */
function resultSetParams(
  state: TableState,
  organizationId: number | undefined,
  scope: ListScope | undefined,
  tabFilters: Filters | undefined,
): ResultSetParams {
  const filters = tabFilters ? { ...state.filters, ...tabFilters } : state.filters;
  return {
    search: state.search,
    sort: state.sort,
    organizationId,
    filters: Object.keys(filters).length > 0 ? filters : undefined,
    scope,
  };
}

// ─── Rows ───────────────────────────────────────────────────────────────────────────────────────

/**
 * What the cells' rules need before the rows are fetched. Rules belong to a (project, type): the forms to
 * read them from are those of the projects the rows fetched so far belong to (reported through
 * `setSeenRows`), so the fields they read are requested from the second fetch on.
 */
function useRuleInputs(config: ListConfig, catalog: FieldCatalog | undefined, shownFieldIds: string[]) {
  const [seenRows, setSeenRows] = useState<RowRecord[]>([]);
  const typeRules = useTypeRules(config?.list.typesSegment, useProjectIdsOf(seenRows as RowTypeRef[]));
  const ruleFields = useRuleFields(catalog?.fields, shownFieldIds, typeRules);
  return { typeRules, ruleFields, setSeenRows };
}

/**
 * The rows the virtual scroller's window covers, reported by the scroller as the user scrolls. A new result
 * set (`resultSetKey`) starts again at the top.
 */
function useScrollWindow(resultSetKey: string) {
  const [scrollWindow, setScrollWindow] = useState({ key: resultSetKey, first: 0, last: SCROLL_CHUNK_SIZE });
  const shownWindow = scrollWindow.key === resultSetKey ? scrollWindow : { first: 0, last: SCROLL_CHUNK_SIZE };
  const onScrollWindow = useCallback(
    (first: number, last: number) =>
      setScrollWindow((current) =>
        current.key === resultSetKey && current.first === first && current.last === last
          ? current
          : { key: resultSetKey, first, last },
      ),
    [resultSetKey],
  );
  return { shownWindow, onScrollWindow };
}

// ─── What is kept for the user ──────────────────────────────────────────────────────────────────

/**
 * The server may still refuse what was kept (a definition changed in a way the checks before the first
 * request can't see): the user's changes are then given up, once, rather than leaving a list that can never
 * load.
 */
function useRecoveryFromRefusedState(pageError: unknown, userState: UserViewState, table: TableControls, config: ListConfig) {
  const recoveredRef = useRef(false);
  const failedStatus = (pageError as { status?: unknown } | null)?.status;
  const { saved, reset } = userState;
  const { setFilters, setSort, setPage } = table;
  useEffect(() => {
    if (failedStatus !== 400 || saved == null || recoveredRef.current) return;
    recoveredRef.current = true;
    setFilters({});
    setSort(config?.list.defaultSort);
    setPage(0, DEFAULT_LIMIT);
    reset();
  }, [failedStatus, saved, reset, setFilters, setSort, setPage, config]);
}

interface KeptUserChangesOptions {
  baselineReady: boolean;
  state: TableState;
  columnsSeeded: boolean;
  viewKey: string | undefined;
  organizationId: number | undefined;
  userState: UserViewState;
  table: TableControls;
  viewBase: PersistedListState | undefined;
}

/**
 * The user's changes are kept as they happen — not the state the list opened with, nor what merely equals
 * it: the first settled state is the baseline, a later difference is what gets saved. Returns how to give
 * the changes up: the list goes back to the view's own state, or to the defaults, and what it is then is
 * the new baseline, not something to save.
 */
function useKeptUserChanges({ baselineReady, state, columnsSeeded, viewKey, organizationId, userState, table, viewBase }: KeptUserChangesOptions) {
  const lastKept = useRef<string | undefined>(undefined);

  const { reset: resetTable } = table;
  const { reset: resetUserState, save } = userState;
  const resetToBase = useCallback(() => {
    lastKept.current = undefined;
    resetUserState();
    resetTable(viewBase);
  }, [resetUserState, resetTable, viewBase]);

  useEffect(() => {
    if (!baselineReady) return;
    const body = toPersisted(state, columnsSeeded);
    const listPrefix = `${viewKey}|${organizationId}|`;
    const snapshot = `${listPrefix}${JSON.stringify(body)}`;
    if (!lastKept.current?.startsWith(listPrefix)) {
      lastKept.current = snapshot;
      return;
    }
    if (snapshot !== lastKept.current) {
      lastKept.current = snapshot;
      save(body);
    }
  }, [baselineReady, state, columnsSeeded, viewKey, organizationId, save]);

  return resetToBase;
}

// ─── Collection size ────────────────────────────────────────────────────────────────────────────

interface CollectionTotalOptions {
  entityType: string;
  config: ListConfig;
  organizationId: number | undefined;
  scope: ListScope | undefined;
  embedded: boolean | undefined;
  state: TableState;
  viewBase: PersistedListState | undefined;
  viewSettled: boolean;
  totalCount: number;
}

/**
 * The titlebar's count chip is the whole collection — on a saved view, what the view's own filters keep —
 * not the filtered result set (that one is the toolbar's selected/total chip). While nothing narrows the list
 * they are the same number, so the extra count request (one row) only runs once a search or a filter is
 * active, or the view has filters of its own (what the user adds or removes on top doesn't move the chip).
 * A key under "entity-list" so a create's invalidation refreshes it too. Not for an embedded list: its tab
 * badge already is that total.
 */
function useCollectionTotal({ entityType, config, organizationId, scope, embedded, state, viewBase, viewSettled, totalCount }: CollectionTotalOptions) {
  const viewFilters = ownFiltersOf(viewBase);
  const narrowed = Boolean(state.search) || Object.keys(state.filters).length > 0 || viewFilters != null;
  const unfilteredTotal = useQuery({
    queryKey: queryKeys.entityListTotal(entityType, organizationId, scope, viewFilters),
    queryFn: () =>
      config!.api.list({ offset: 0, limit: 1, organizationId, scope, filters: viewFilters }) as Promise<PagedResult<RowRecord>>,
    select: (result) => result.totalCount,
    enabled: config != null && !embedded && narrowed && viewSettled,
  });
  return collectionTotalOf(narrowed, unfilteredTotal, totalCount);
}

/**
 * The saved view's own filters, when it has any.
 */
function ownFiltersOf(viewBase: PersistedListState | undefined): Filters | undefined {
  return viewBase?.filters && Object.keys(viewBase.filters).length > 0 ? viewBase.filters : undefined;
}

/**
 * The collection's size: the page's total while nothing narrows the list, otherwise the unfiltered count
 * once known. A view whose own filters the server refuses (a field that is gone) falls back on the page's total.
 */
function collectionTotalOf(narrowed: boolean, unfilteredTotal: { data?: number; isError: boolean }, totalCount: number): number | undefined {
  if (!narrowed) return totalCount;
  if (unfilteredTotal.data != null) return unfilteredTotal.data;
  return unfilteredTotal.isError ? totalCount : undefined;
}
