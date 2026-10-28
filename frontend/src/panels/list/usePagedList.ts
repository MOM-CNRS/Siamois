import {useMemo, useState} from "react";
import {keepPreviousData, useQueries} from "@tanstack/react-query";
import type {ListParams, PagedResult} from "../../entities/shared/types";
import {queryKeys} from "../../api/queryKeys";

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

// A row of a scrolled list whose page isn't fetched yet: drawn as a skeleton, never selected nor opened.
const PLACEHOLDER_ROW = "__skeletonRow";

export function placeholderRow(index: number): Record<string, unknown> & { id: string } {
  return { id: `__skeleton-${index}`, [PLACEHOLDER_ROW]: true };
}

export function isPlaceholderRow(row: unknown): boolean {
  return (row as Record<string, unknown> | null)?.[PLACEHOLDER_ROW] === true;
}

/** The pages (of `limit` rows) a virtual scroller's window [first, last) touches — at least the first one. */
export function pagesInRange(first: number, last: number, limit: number): number[] {
  const from = Math.floor(Math.max(first, 0) / limit);
  const to = Math.max(from, Math.floor((Math.max(last, first + 1) - 1) / limit));
  return Array.from({ length: to - from + 1 }, (_, i) => from + i);
}

export interface UsePagedListOptions<T> {
  entityType: string;
  // Everything that defines the result set — sort, search, filters, scope. The fields are passed
  // apart: they change what each row carries, not which rows.
  params: Omit<ListParams, "offset" | "limit" | "fields">;
  // The pages to fetch, by index, each `limit` rows long: the one page shown (a paginated list), or
  // every page the window of a virtual scroller touches.
  pages: number[];
  limit: number;
  // The rows as the whole result set, for a virtual scroller: `totalCount` rows, the ones of a page not
  // fetched (yet) being placeholders. Otherwise the rows of the pages asked for, end to end.
  virtual?: boolean;
  // The dynamic columns currently shown (their order doesn't matter here).
  fields?: string[];
  fetch: (params: ListParams) => Promise<PagedResult<T>>;
  enabled: boolean;
}

function fieldsParam(fields: readonly string[]): string | undefined {
  return fields.length > 0 ? fields.join(",") : undefined;
}

const NO_PAGES: Record<number, string[]> = {};

/**
 * Backs a server-paginated list: one query per page, under the ["entity-list", entityType, …]
 * prefix — so an invalidateQueries(["entity-list", entityType]) (a cell edit, a creation) refreshes
 * it. While a new result set loads (or, paginated, the next page), the rows on screen stay; a page a
 * virtual scroller reaches for the first time shows placeholders until it arrives.
 *
 * Columns don't define the result set. A page is fetched with the columns visible when it was
 * first requested; a column shown afterwards is fetched on its own (`fields=<the missing fields>`, same
 * sort/filters/offset, one request for all of them) and merged in by row id — the rows and the loading state stay put. Hiding a
 * column fetches nothing.
 */
export function usePagedList<T>({ entityType, params, pages, limit, virtual = false, fields = [], fetch, enabled }: UsePagedListOptions<T>) {
  // Keyed on the field ids, not the array: callers pass a fresh array on every render.
  const fieldsKey = fields.join(",");
  const sortedFields = useMemo(() => (fieldsKey ? fieldsKey.split(",").sort() : []), [fieldsKey]);

  // The fields each page's base request carries — fixed for as long as the same result set is shown,
  // so showing or hiding a column never refetches a page; a page reached later is fetched with the
  // columns visible then. `enabled` is part of the key: a page requested while disabled (a
  // schema-bearing list waiting for its columns) would otherwise keep the column set it had then.
  const resultKey = `${enabled}|${JSON.stringify(params)}|${limit}`;
  const [requested, setRequested] = useState(() => ({ key: resultKey, byPage: NO_PAGES }));
  const known = requested.key === resultKey ? requested.byPage : NO_PAGES;
  const unseen = pages.filter((p) => !(p in known));
  const byPage = unseen.length === 0 ? known : { ...known, ...Object.fromEntries(unseen.map((p) => [p, sortedFields])) };
  // Set during render (React's "adjusting state when a prop changes" pattern), so no frame requests a
  // new page with another page's fields.
  if (requested.key !== resultKey || requested.byPage !== byPage) setRequested({ key: resultKey, byPage });

  const pageRequests = pages.map((page) => {
    const pageParams: ListParams = { ...params, offset: page * limit, limit };
    const baseFields = byPage[page] ?? sortedFields;
    // One request for every column shown since the page was fetched, not one per column: the server
    // projects them together (and resolves their labels in one batch).
    const supplementFields = missingFields(sortedFields, baseFields);
    return {
      page,
      baseParams: { ...pageParams, fields: fieldsParam(baseFields) },
      supplementParams: { ...pageParams, fields: fieldsParam(supplementFields) },
      supplementFields,
    };
  });
  const results = useQueries({
    queries: pageRequests.flatMap((request) => [
      {
        queryKey: queryKeys.entityListPage(entityType, request.baseParams),
        queryFn: () => fetch(request.baseParams),
        enabled,
        // A paginated list keeps the page it shows until the next one arrives. A scrolled one keeps a
        // page's previous rows only for the same page of a new result set (a sort, a search): another
        // page's rows would show at the wrong place.
        placeholderData: virtual
          ? (previous: PagedResult<T> | undefined, previousQuery?: { queryKey: readonly unknown[] }) =>
              (previousQuery?.queryKey[2] as ListParams | undefined)?.offset === request.baseParams.offset ? previous : undefined
          : keepPreviousData,
      },
      {
        queryKey: queryKeys.entityListPage(entityType, request.supplementParams),
        queryFn: () => fetch(request.supplementParams),
        enabled: enabled && request.supplementFields.length > 0,
      },
    ]),
  });

  // What the rows are built from changes only when a request settles (or a placeholder comes or goes):
  // useQueries hands back new result objects on every render, which would rebuild every row each time.
  const signature = `${resultKey}|${virtual}|${pageRequests.map((r) => `${r.page}:${r.supplementFields.join(",")}`).join(";")}|${results
    .map((r) => `${r.dataUpdatedAt}:${r.isPlaceholderData ? 1 : 0}:${r.error ? 1 : 0}`)
    .join(",")}`;
  const assembled = useMemo(() => {
    const loaded: { page: number; rows: T[] }[] = [];
    let totalCount: number | undefined;
    pageRequests.forEach((request, i) => {
      const base = results[2 * i];
      const supplement = results[2 * i + 1];
      const baseRows = base.data?.data;
      if (!baseRows) return;
      totalCount ??= base.data!.totalCount;
      // A failed supplement stops being pending (its cells stay empty; the error shows).
      const fetched = supplement.data?.data ?? (supplement.error != null ? [] : undefined);
      const rows = request.supplementFields.length > 0
        ? mergeSupplementRows(baseRows, { fieldIds: request.supplementFields, rows: fetched })
        : baseRows;
      loaded.push({ page: request.page, rows });
    });
    const loadedRows = loaded.flatMap((p) => p.rows);
    let rows = loadedRows;
    if (virtual && totalCount != null) {
      rows = Array.from({ length: totalCount }, (_, i) => placeholderRow(i) as T);
      for (const { page, rows: pageRows } of loaded) pageRows.forEach((row, i) => (rows[page * limit + i] = row));
      // A page of a new result set still showing the previous one's rows may hold more than are left.
      rows.length = totalCount;
    }
    return { rows, loadedRows, totalCount: totalCount ?? 0 };
    // Rebuilt from the signature: `results` and `pageRequests` are new on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);

  const bases = results.filter((_, i) => i % 2 === 0);
  return {
    // The rows to draw: a scrolled list's whole result set (placeholders where not fetched), or the page shown.
    rows: assembled.rows,
    // Only the rows fetched — what the cells' rules are evaluated on.
    loadedRows: assembled.loadedRows,
    totalCount: assembled.totalCount,
    // Only the very first load blocks the table; a page change keeps the previous rows on screen,
    // and a column being added shows as skeleton cells. isPending rather than isLoading: a list
    // holding its first request until its columns are known is not fetching yet, but it has no
    // rows either — it must show the loading skeleton, not a flash of "no results".
    isLoading: !bases.some((r) => r.data != null) && bases.some((r) => r.isPending && !r.isError),
    // Any request in flight — a page reached, a column being added, or a background refetch after
    // an invalidation (cell edit, creation). Drives the list's progress bar.
    isFetching: results.some((r) => r.isFetching),
    error: results.find((r) => r.error != null)?.error ?? null,
  };
}
