import { useCallback, useMemo, useState } from "react";
import { useQueries } from "@tanstack/react-query";
import type { ListParams, PagedResult } from "../entities/types";

// Rows fetched per request while scrolling. The list endpoints page with
// PageRequest.of(offset / limit, limit) and reject an offset that isn't a multiple of the limit
// (ProjectApiService#validatePagedListRequest), so every request is one aligned chunk — never an arbitrary
// [first, last] window. 50 stays well under the server's 200 cap (ProjectApiService.MAX_PAGE_SIZE)
// while covering a couple of screens per round trip.
export const CHUNK_SIZE = 50;

// Stand-in for a row whose chunk hasn't arrived yet. The virtual scroller needs an array as long as
// the whole result set so the scrollbar is to scale; the holes are filled with these rather than
// left undefined, so every row-level callback (dataKey, rowClassName, cell bodies) always receives
// an object. The id is namespaced so it can never collide with a real one.
export interface PlaceholderRow {
  id: string;
  __placeholder: true;
}

export function isPlaceholderRow(row: unknown): row is PlaceholderRow {
  return row != null && (row as { __placeholder?: unknown }).__placeholder === true;
}

/** Chunk indices covering rows [first, last) — `last` exclusive, as the virtual scroller reports it. */
export function chunksForRange(first: number, last: number, chunkSize = CHUNK_SIZE): number[] {
  // The scroller can report a NaN bound before it has measured itself; fall back to one chunk from
  // `first` rather than requesting nothing.
  if (!Number.isFinite(first)) first = 0;
  if (!Number.isFinite(last)) last = first + chunkSize;
  const start = Math.max(0, Math.floor(first / chunkSize));
  const end = Math.max(start, Math.floor(Math.max(first, last - 1) / chunkSize));
  const chunks: number[] = [];
  for (let i = start; i <= end; i++) chunks.push(i);
  return chunks;
}

/** Lays loaded chunks out at their offsets in a totalCount-long array, placeholders elsewhere. */
export function assembleRows<T>(
  totalCount: number,
  chunks: ReadonlyArray<{ index: number; rows: T[] | undefined }>,
  chunkSize = CHUNK_SIZE,
): (T | PlaceholderRow)[] {
  const rows: (T | PlaceholderRow)[] = new Array(totalCount);
  for (const { index, rows: chunkRows } of chunks) {
    if (!chunkRows) continue;
    const offset = index * chunkSize;
    for (let i = 0; i < chunkRows.length && offset + i < totalCount; i++) rows[offset + i] = chunkRows[i];
  }
  for (let i = 0; i < totalCount; i++) {
    if (rows[i] === undefined) rows[i] = { id: `__placeholder_${i}`, __placeholder: true };
  }
  return rows;
}

export interface UseVirtualListOptions<T> {
  entityType: string;
  // Everything that defines the result set — sort, search, filters, fields, scope. offset/limit are
  // owned here, per chunk.
  params: Omit<ListParams, "offset" | "limit">;
  fetch: (params: ListParams) => Promise<PagedResult<T>>;
  enabled: boolean;
}

/**
 * Backs a lazily virtual-scrolled list: one query per chunk actually scrolled into view, each under
 * the same ["entity-list", entityType, params] key a single page used to have — so an
 * invalidateQueries(["entity-list", entityType]) (a cell edit, a creation) still refreshes every
 * chunk on screen, and a chunk scrolled past and back is served from cache.
 */
export function useVirtualList<T>({ entityType, params, fetch, enabled }: UseVirtualListOptions<T>) {
  const paramsKey = JSON.stringify(params);
  const [requested, setRequested] = useState<{ key: string; chunks: number[] }>({ key: paramsKey, chunks: [0] });

  // A new result set (sort/search/filter/columns change) starts over from the first chunk; the
  // chunks loaded for the previous one are meaningless at their old offsets. Reset during render
  // (React's "adjusting state when a prop changes" pattern), so no frame ever renders stale chunks
  // against the new totalCount.
  const chunks = requested.key === paramsKey ? requested.chunks : [0];
  if (requested.key !== paramsKey) setRequested({ key: paramsKey, chunks: [0] });

  const results = useQueries({
    queries: chunks.map((index) => {
      const chunkParams: ListParams = { ...params, offset: index * CHUNK_SIZE, limit: CHUNK_SIZE };
      return {
        queryKey: ["entity-list", entityType, chunkParams],
        queryFn: () => fetch(chunkParams),
        enabled,
      };
    }),
  });

  const totalCount = results.find((r) => r.data != null)?.data?.totalCount ?? 0;
  const dataSignature = results.map((r) => r.dataUpdatedAt).join(",");

  const rows = useMemo(
    () => assembleRows<T>(totalCount, chunks.map((index, i) => ({ index, rows: results[i]?.data?.data }))),
    // `results` itself is a new array every render; what matters is which chunks and when they
    // last changed, so the deps name exactly that.
    [totalCount, chunks.join(","), dataSignature],
  );

  const requestRange = useCallback(
    (first: number, last: number) => {
      const needed = chunksForRange(first, last);
      setRequested((current) => {
        if (current.key !== paramsKey) return current;
        const missing = needed.filter((c) => !current.chunks.includes(c));
        return missing.length === 0 ? current : { key: current.key, chunks: [...current.chunks, ...missing] };
      });
    },
    [paramsKey],
  );

  return {
    rows,
    totalCount,
    // Only the very first chunk blocks the table; later ones show as placeholder rows instead.
    isLoading: results[0]?.isLoading ?? false,
    // Any chunk in flight — a newly scrolled-to one, or a background refetch after an
    // invalidation (cell edit, creation). Drives the list's progress bar.
    isFetching: results.some((r) => r.isFetching),
    error: results.find((r) => r.error)?.error ?? null,
    requestRange,
    // Identifies the current result set, e.g. to scroll back to the top when it changes.
    resultSetKey: paramsKey,
  };
}
