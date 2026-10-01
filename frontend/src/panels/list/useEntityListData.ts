import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../../api/queryKeys";
import { isOwningProjectField } from "../../fields/types";
import type { EntityTypeConfig, ListParams, ListScope, PagedResult } from "../../entities/types";
import { usePagedList } from "../usePagedList";
import { useRowRules, useRuleFields } from "../useRowRules";
import { useTableState } from "../useTableState";
import { useProjectIdsOf, useTypeRules, type RowTypeRef } from "../useTypeRules";

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
}

/**
 * Everything the list reads from the server and from its own table state: the column catalog, the
 * visible columns, the current page, the rules its cells follow and the collection's size. The
 * panel renders from this and owns no query.
 */
export function useEntityListData({ entityType, config, organizationId, scope, prefsKey, embedded }: UseEntityListDataOptions) {
  const table = useTableState({ defaultSort: config?.list.defaultSort, prefsKey });
  const { state, seedVisibleColumns, columnsSeeded } = table;

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

  // Only the columns currently on screen are ever requested — the projection (and its label-batch
  // resolution cost server-side) scales with what's visible, not with the whole catalog. They're
  // passed to usePagedList apart from the result-set params: showing a column fetches just that
  // column for the rows already loaded, hiding one fetches nothing.
  const params: Omit<ListParams, "offset" | "limit" | "fields"> = {
    search: state.search,
    sort: state.sort,
    organizationId,
    filters: Object.keys(state.filters).length > 0 ? state.filters : undefined,
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
    enabled: config != null && (!hasSchema || columnsSeeded || catalogFailed),
  });
  const { rows, totalCount } = page;
  useEffect(() => setSeenRows(rows), [rows]);
  const rowRules = useRowRules(catalog?.fields, shownFieldIds, ruleFields, rows, typeRules);

  // The titlebar's count chip is the whole collection, not the filtered result set (that one is the
  // toolbar's selected/total chip). While nothing narrows the list they are the same number, so the
  // extra count request (one row) only runs once a search or a filter is active. A key under
  // "entity-list" so a create's invalidation refreshes it too. Not for an embedded list: its tab
  // badge already is that total.
  const narrowed = Boolean(state.search) || Object.keys(state.filters).length > 0;
  const unfilteredTotal = useQuery({
    queryKey: queryKeys.entityListTotal(entityType, organizationId, scope),
    queryFn: () => config!.api.list({ offset: 0, limit: 1, organizationId, scope }) as Promise<PagedResult<RowRecord>>,
    select: (result) => result.totalCount,
    enabled: config != null && !embedded && narrowed,
  });
  const collectionTotal = narrowed ? unfilteredTotal.data : totalCount;

  return { table, catalog, hasSchema, pinnedFieldIds, params, page, rowRules, collectionTotal };
}
