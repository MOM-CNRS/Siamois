import { useMemo, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { ListParams, PagedResult } from "../entities/types";
import { queryKeys } from "../api/queryKeys";

// Set on a row while one of its visible columns is still being fetched (see usePagedList's column
// supplements) — the cell renders a skeleton instead of an empty value.
const PENDING_FIELDS = "__pendingFields";

export function isFieldPending(row: unknown, fieldId: string): boolean {
  const pending = (row as Record<string, unknown> | null)?.[PENDING_FIELDS];
  return Array.isArray(pending) && pending.includes(fieldId);
}

/**
 * Merges a column supplement (the same rows, fetched with `fields=<the missing fields>`) into a page's
 * base rows, matched by id: the supplement's `answers` are added to the base row's, and its
 * top-level properties fill in only what the base row lacks — the base row is the complete one. A
 * supplement still in flight (`rows` undefined) marks its fields pending on every row instead.
 */
export function mergeSupplementRows<T>(
  baseRows: T[],
  supplement: { fieldIds: readonly string[]; rows: T[] | undefined } | null,
): T[] {
  if (!supplement || supplement.fieldIds.length === 0) return baseRows;
  const pending = supplement.rows ? [] : [...supplement.fieldIds];
  const byId = new Map((supplement.rows ?? []).map((r) => [(r as { id?: unknown }).id, r as Record<string, unknown>]));
  return baseRows.map((base) => {
    const row = base as Record<string, unknown>;
    let merged: Record<string, unknown> = row;
    const extra = byId.get(row.id);
    if (extra) {
      const answers = { ...(merged.answers as object | undefined), ...(extra.answers as object | undefined) };
      merged = { ...extra, ...merged, answers };
    }
    if (pending.length > 0) merged = { ...merged, [PENDING_FIELDS]: pending };
    return merged as T;
  });
}

/** The visible fields a page wasn't fetched with — each one needs its own supplement. */
export function missingFields(visible: readonly string[], fetchedWith: readonly string[]): string[] {
  return visible.filter((f) => !fetchedWith.includes(f));
}

export interface UsePagedListOptions<T> {
  entityType: string;
  // Everything that defines the result set — sort, search, filters, scope. The fields are passed
  // apart: they change what each row carries, not which rows.
  params: Omit<ListParams, "offset" | "limit" | "fields">;
  offset: number;
  limit: number;
  // The dynamic columns currently shown (their order doesn't matter here).
  fields?: string[];
  fetch: (params: ListParams) => Promise<PagedResult<T>>;
  enabled: boolean;
}

function fieldsParam(fields: readonly string[]): string | undefined {
  return fields.length > 0 ? fields.join(",") : undefined;
}

/**
 * Backs a server-paginated list: one query for the page, under the ["entity-list", entityType, …]
 * prefix — so an invalidateQueries(["entity-list", entityType]) (a cell edit, a creation) refreshes
 * it. While the next page loads, the previous one stays on screen.
 *
 * Columns don't define the result set. A page is fetched with the columns visible when it was
 * first requested; a column shown afterwards is fetched on its own (`fields=<the missing fields>`, same
 * sort/filters/offset, one request for all of them) and merged in by row id — the rows and the loading state stay put. Hiding a
 * column fetches nothing.
 */
export function usePagedList<T>({ entityType, params, offset, limit, fields = [], fetch, enabled }: UsePagedListOptions<T>) {
  // Keyed on the field ids, not the array: callers pass a fresh array on every render.
  const fieldsKey = fields.join(",");
  const sortedFields = useMemo(() => (fieldsKey ? fieldsKey.split(",").sort() : []), [fieldsKey]);

  // The fields the current page's base request carries — fixed for as long as the same page of the
  // same result set is shown, so showing or hiding a column never refetches it. `enabled` is part
  // of the key: a page requested while disabled (a schema-bearing list waiting for its columns)
  // would otherwise keep the column set it had then.
  const pageKey = `${enabled}|${JSON.stringify(params)}|${offset}|${limit}`;
  const [requested, setRequested] = useState(() => ({ key: pageKey, fields: sortedFields }));
  // Reset during render (React's "adjusting state when a prop changes" pattern), so no frame
  // requests the new page with the old page's fields.
  const isCurrent = requested.key === pageKey;
  const baseFields = isCurrent ? requested.fields : sortedFields;
  if (!isCurrent) setRequested({ key: pageKey, fields: baseFields });

  const pageParams: ListParams = { ...params, offset, limit };
  const baseParams: ListParams = { ...pageParams, fields: fieldsParam(baseFields) };
  const base = useQuery({
    queryKey: queryKeys.entityListPage(entityType, baseParams),
    queryFn: () => fetch(baseParams),
    enabled,
    placeholderData: keepPreviousData,
  });

  // One request for every column shown since the page was fetched, not one per column: the server
  // projects them together (and resolves their labels in one batch).
  const supplementFields = missingFields(sortedFields, baseFields);
  const supplementParams: ListParams = { ...pageParams, fields: fieldsParam(supplementFields) };
  const supplement = useQuery({
    queryKey: queryKeys.entityListPage(entityType, supplementParams),
    queryFn: () => fetch(supplementParams),
    enabled: enabled && supplementFields.length > 0,
  });

  const baseRows = base.data?.data;
  const supplementFieldsKey = supplementFields.join(",");
  const supplementData = supplement.data?.data;
  const supplementFailed = supplement.error != null;
  const rows = useMemo(() => {
    if (!baseRows) return [];
    if (!supplementFieldsKey) return baseRows;
    // A failed supplement stops being pending (its cells stay empty; the error shows).
    const fetched = supplementData ?? (supplementFailed ? [] : undefined);
    return mergeSupplementRows(baseRows, { fieldIds: supplementFieldsKey.split(","), rows: fetched });
  }, [baseRows, supplementFieldsKey, supplementData, supplementFailed]);

  return {
    rows,
    totalCount: base.data?.totalCount ?? 0,
    // Only the very first load blocks the table; a page change keeps the previous rows on screen,
    // and a column being added shows as skeleton cells. isPending rather than isLoading: a list
    // holding its first request until its columns are known is not fetching yet, but it has no
    // rows either — it must show the loading skeleton, not a flash of "no results".
    isLoading: base.isPending && !base.isError,
    // Any request in flight — a page change, a column being added, or a background refetch after
    // an invalidation (cell edit, creation). Drives the list's progress bar.
    isFetching: base.isFetching || supplement.isFetching,
    error: base.error ?? supplement.error ?? null,
  };
}
