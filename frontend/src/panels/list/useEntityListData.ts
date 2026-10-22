import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../../api/queryKeys";
import { isOwningProjectField } from "../../fields/types";
import type { EntityTypeConfig, FilterValue, ListParams, ListScope, PagedResult } from "../../entities/shared/types";
import { usePagedList } from "./usePagedList";
import { useRowRules, useRuleFields } from "./useRowRules";
import { useTableState } from "./useTableState";
import { differsFromView, parsePersisted, toPersisted, viewToBody } from "./listState";
import { getView } from "../../api/resources/views";
import { useUserViewState } from "./useUserViewState";
import { availableFilters, isSortAvailable } from "./listStateValidity";
import { usePaneState } from "../pane/paneState";
import { DEFAULT_LIMIT } from "./tableState";
import { availableViews, resolveViewMode } from "./listViews";
import { useProjectIdsOf, useTypeRules, type RowTypeRef } from "./useTypeRules";

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

/**
 * Everything the list reads from the server and from its own table state: the column catalog, the
 * visible columns, the current page, the rules its cells follow and the collection's size. The
 * panel renders from this and owns no query.
 */
export function useEntityListData({ entityType, config, organizationId, scope, prefsKey, embedded, viewId, fixedFilters, stateKey, volatile, initialFilters }: UseEntityListDataOptions) {
  // What this user arranged on the list, kept server-side per organization: on a saved view, on that view;
  // otherwise the organization-wide list and a relation tab of the same type are arranged independently,
  // and a relation tab is arranged per parent fiche (the units of this project, not of every project).
  const viewKey = volatile
    ? undefined
    : viewId != null
      ? `view:${viewId}`
      : `ctx:${entityType}:${scope ? `${scope.entityType}:${scope.id}-tab${stateKey ? `:${stateKey}` : ""}` : "global"}`;
  const userState = useUserViewState(viewKey, organizationId);

  // A saved view is the starting point; what the user changed on it comes over it, key by key.
  const viewQuery = useQuery({
    queryKey: queryKeys.view(organizationId, viewId),
    queryFn: () => getView(organizationId!, viewId!),
    enabled: viewId != null && organizationId != null,
    staleTime: 60_000,
  });
  const viewBody = useMemo(() => (viewQuery.data ? viewToBody(viewQuery.data.state) : undefined), [viewQuery.data]);
  const viewBase = useMemo(() => parsePersisted(viewBody), [viewBody]);
  const initialSaved = useMemo(() => (initialFilters ? { v: 1, filters: initialFilters } : undefined), [initialFilters]);
  const kept = userState.saved ?? initialSaved;
  const effectiveSaved = viewBody || kept ? { ...viewBody, ...kept } : undefined;
  const viewSettled = viewId == null || organizationId == null || !viewQuery.isPending;
  const viewMissing = viewQuery.isError && (viewQuery.error as { status?: unknown } | null)?.status === 404;

  const table = useTableState({
    defaultSort: config?.list.defaultSort,
    prefsKey,
    shareViewMode: viewId == null,
    userState: { settled: userState.settled && viewSettled, saved: effectiveSaved },
  });
  const { state, seedVisibleColumns, columnsSeeded, userStateReady } = table;
  // How the list is shown (flat list, tree, later a map…): chosen in the gear menu, the flat list
  // staying the default. The tree view fetches its own rows: no flat page meanwhile.
  const views = availableViews(config, scope);
  const viewMode = resolveViewMode(state.viewMode, views, config?.list.defaultView);
  const showTree = viewMode === "tree";

  const hasSchema = config?.list.schema != null;
  const { data: catalog, isError: catalogFailed } = useQuery({
    queryKey: queryKeys.entityListSchema(entityType, organizationId, scope),
    queryFn: () => config!.list.schema!.load({ organizationId, scope }),
    enabled: hasSchema,
  });

  // The catalog fields a pinned column already shows: not offered (or seeded) a second time.
  const pinnedFieldIds = useMemo(
    () => new Set((config?.list.columns ?? []).flatMap((c) => (c.fieldId ? [c.fieldId] : []))),
    [config],
  );

  // Seeds the toggler the first time the catalog loads — from this browser's saved arrangement if
  // there is one, else from the catalog's own defaults (ActionUnitTableColumnDefaults on the
  // project side) — never again, so a user's toggle isn't clobbered by an organizationId change
  // re-fetching the same catalog.
  useEffect(() => {
    if (!catalog) return;
    // The project rows belong to is a column like any other, shown by default only where rows come
    // from several projects: inside a project's own tab it would repeat that project on every row.
    const isProject = (fieldId: string) => {
      const field = catalog.fields[fieldId];
      return field != null && isOwningProjectField(field);
    };
    const defaults = catalog.columns
      .filter((c) => !pinnedFieldIds.has(c.fieldId))
      .filter((c) => (isProject(c.fieldId) ? scope == null : c.visible))
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((c) => c.fieldId);
    seedVisibleColumns(
      defaults,
      catalog.columns.map((c) => c.fieldId).filter((id) => !pinnedFieldIds.has(id)),
    );
  }, [catalog, seedVisibleColumns, scope, pinnedFieldIds]);

  // Once the list's definitions are known (its columns, once the catalog has loaded), what the user kept is
  // checked against them, once: a filter or a sort on a field that no longer exists would be refused by the
  // server (400, « Champ inconnu ») and could not be taken off — it is dropped before the first request.
  const baselineReady = userStateReady && (!hasSchema || columnsSeeded || catalogFailed);
  const [stateChecked, setStateChecked] = usePaneState("userStateChecked", false);
  if (baselineReady && !stateChecked) {
    setStateChecked(true);
    const filters = availableFilters(state.filters, config, catalog);
    if (Object.keys(filters).length !== Object.keys(state.filters).length) table.setFilters(filters);
    if (!isSortAvailable(state.sort, config, catalog)) table.setSort(config?.list.defaultSort);
  }
  const listReady = userStateReady && stateChecked;

  // Only the columns currently on screen are ever requested — the projection (and its label-batch
  // resolution cost server-side) scales with what's visible, not with the whole catalog. They're
  // passed to usePagedList apart from the result-set params: showing a column fetches just that
  // column for the rows already loaded, hiding one fetches nothing.
  // The tab's own filters come over the user's on the same field, and only those the list still knows
  // (an unknown field is refused by the server, and a filter the user cannot take off would block the tab).
  const tabFilters = useMemo(
    () => (fixedFilters && baselineReady ? availableFilters(fixedFilters, config, catalog) : undefined),
    [fixedFilters, baselineReady, config, catalog],
  );
  const filters = tabFilters ? { ...state.filters, ...tabFilters } : state.filters;
  const params: Omit<ListParams, "offset" | "limit" | "fields"> = {
    search: state.search,
    sort: state.sort,
    organizationId,
    filters: Object.keys(filters).length > 0 ? filters : undefined,
    scope,
  };

  // What the cells' rules read on top of the visible columns (rules/dependencies.ts): fetched with
  // the rows although not shown, so a cell can be greyed by an answer whose column is hidden.
  const shownFieldIds = useMemo(() => [...pinnedFieldIds, ...state.visibleColumns], [pinnedFieldIds, state.visibleColumns]);
  // Rules belong to a (project, type): the forms to read them from are those of the projects the rows
  // fetched so far belong to, so the fields they read are requested from the second fetch on.
  const [seenRows, setSeenRows] = useState<RowRecord[]>([]);
  const typeRules = useTypeRules(config?.list.typesSegment, useProjectIdsOf(seenRows as RowTypeRef[]));
  const ruleFields = useRuleFields(catalog?.fields, shownFieldIds, typeRules);

  const page = usePagedList<RowRecord>({
    entityType,
    params,
    offset: state.offset,
    limit: state.limit,
    fields: useMemo(() => [...state.visibleColumns, ...ruleFields], [state.visibleColumns, ruleFields]),
    fetch: (p) => config!.api.list(p) as Promise<PagedResult<RowRecord>>,
    // A list with a schema waits for its columns, rather than fetching once without them and then
    // adding each one (a failed catalog just means no dynamic columns).
    enabled: !showTree && listReady && config != null && (!hasSchema || columnsSeeded || catalogFailed),
  });
  const { rows, totalCount } = page;
  useEffect(() => setSeenRows(rows), [rows]);

  // The user's changes are kept as they happen — not the state the list opened with, nor what merely
  // equals it: the first settled state is the baseline, a later difference is what gets saved.
  // The server may still refuse what was kept (a definition changed in a way the checks above can't see):
  // the user's changes are then given up, once, rather than leaving a list that can never load.
  const recoveredRef = useRef(false);
  const failedStatus = (page.error as { status?: unknown } | null)?.status;
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

  const lastKept = useRef<string | undefined>(undefined);

  // The user's changes are given up: the list goes back to the view's own state, or to the defaults. What
  // it is then is the new baseline, not something to save.
  const { reset: resetTable } = table;
  const resetUserState = userState.reset;
  const resetToBase = useCallback(() => {
    lastKept.current = undefined;
    resetUserState();
    resetTable(viewBase);
  }, [resetUserState, resetTable, viewBase]);
  const modified = viewId != null && viewBase != null && listReady && differsFromView(state, viewBase);
  const hasUserState = userState.saved != null;
  const { save } = userState;
  useEffect(() => {
    if (!baselineReady) return;
    const body = toPersisted(state, columnsSeeded);
    const snapshot = `${viewKey}|${organizationId}|${JSON.stringify(body)}`;
    if (lastKept.current === undefined || !lastKept.current.startsWith(`${viewKey}|${organizationId}|`)) {
      lastKept.current = snapshot;
      return;
    }
    if (snapshot !== lastKept.current) {
      lastKept.current = snapshot;
      save(body);
    }
  }, [baselineReady, state, columnsSeeded, viewKey, organizationId, save]);
  const rowRules = useRowRules(catalog?.fields, shownFieldIds, ruleFields, rows, typeRules);

  // The titlebar's count chip is the whole collection — on a saved view, what the view's own filters keep —
  // not the filtered result set (that one is the toolbar's selected/total chip). While nothing narrows the list
  // they are the same number, so the extra count request (one row) only runs once a search or a filter is
  // active, or the view has filters of its own (what the user adds or removes on top doesn't move the chip).
  // A key under "entity-list" so a create's invalidation refreshes it too. Not for an embedded list: its tab
  // badge already is that total.
  const viewFilters = viewBase?.filters && Object.keys(viewBase.filters).length > 0 ? viewBase.filters : undefined;
  const narrowed = Boolean(state.search) || Object.keys(state.filters).length > 0 || viewFilters != null;
  const unfilteredTotal = useQuery({
    queryKey: queryKeys.entityListTotal(entityType, organizationId, scope, viewFilters),
    queryFn: () =>
      config!.api.list({ offset: 0, limit: 1, organizationId, scope, filters: viewFilters }) as Promise<PagedResult<RowRecord>>,
    select: (result) => result.totalCount,
    enabled: config != null && !embedded && narrowed && viewSettled,
  });
  // A view whose own filters the server refuses (a field that is gone) falls back on the page's total.
  const collectionTotal = narrowed ? (unfilteredTotal.data ?? (unfilteredTotal.isError ? totalCount : undefined)) : totalCount;

  return { table, catalog, hasSchema, pinnedFieldIds, params, page, rowRules, collectionTotal, views, viewMode, userState, listReady, view: viewQuery.data, viewMissing, modified, hasUserState, resetToBase };
}
