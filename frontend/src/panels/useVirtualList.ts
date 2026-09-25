import { useCallback, useMemo, useRef, useState } from "react";
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

// Set on a real row while one of its visible columns is still being fetched (see useVirtualList's
// column supplements) — the cell renders a skeleton instead of an empty value.
export const PENDING_FIELDS = "__pendingFields";

export function isFieldPending(row: unknown, fieldId: string): boolean {
  const pending = (row as Record<string, unknown> | null)?.[PENDING_FIELDS];
  return Array.isArray(pending) && pending.includes(fieldId);
}

/**
 * Merges column supplements (the same rows, fetched with `fields=<one field>`) into a chunk's base
 * rows, matched by id: the supplement's `answers` are added to the base row's, and its top-level
 * properties fill in only what the base row lacks — the base row is the complete one. A supplement
 * still in flight marks its field pending on every row instead.
 */
export function mergeSupplementRows<T>(
  baseRows: T[],
  supplements: ReadonlyArray<{ fieldId: string; rows: T[] | undefined }>,
): T[] {
  if (supplements.length === 0) return baseRows;
  const pending = supplements.filter((s) => !s.rows).map((s) => s.fieldId);
  const loaded = supplements
    .filter((s) => s.rows)
    .map((s) => new Map(s.rows!.map((r) => [(r as { id?: unknown }).id, r as Record<string, unknown>])));
  return baseRows.map((base) => {
    const row = base as Record<string, unknown>;
    let merged: Record<string, unknown> = row;
    for (const byId of loaded) {
      const extra = byId.get(row.id);
      if (!extra) continue;
      const answers = { ...(merged.answers as object | undefined), ...(extra.answers as object | undefined) };
      merged = { ...extra, ...merged, answers };
    }
    if (pending.length > 0) merged = { ...merged, [PENDING_FIELDS]: pending };
    return merged as T;
  });
}

/** The visible fields a chunk wasn't fetched with — each one needs its own supplement. */
export function missingFields(visible: readonly string[], fetchedWith: readonly string[]): string[] {
  return visible.filter((f) => !fetchedWith.includes(f));
}

export interface UseVirtualListOptions<T> {
  entityType: string;
  // Everything that defines the result set — sort, search, filters, scope. offset/limit are owned
  // here, per chunk, and so are the fields: they change what each row carries, not which rows.
  params: Omit<ListParams, "offset" | "limit" | "fields">;
  // The dynamic columns currently shown (their order doesn't matter here).
  fields?: string[];
  fetch: (params: ListParams) => Promise<PagedResult<T>>;
  enabled: boolean;
}

interface RequestedChunk {
  index: number;
  // The fields this chunk's base request carried, sorted — fixed once requested, so showing or
  // hiding a column never changes (and so never refetches) an already-loaded chunk.
  fields: string[];
}

function fieldsParam(fields: readonly string[]): string | undefined {
  return fields.length > 0 ? fields.join(",") : undefined;
}

/**
 * Backs a lazily virtual-scrolled list: one query per chunk actually scrolled into view, each under
 * the ["entity-list", entityType, …] prefix — so an invalidateQueries(["entity-list", entityType])
 * (a cell edit, a creation) still refreshes every chunk on screen, and a chunk scrolled past and
 * back is served from cache.
 *
 * Columns don't define the result set. A chunk is fetched with the columns visible when it was
 * first requested; a column shown afterwards is fetched on its own for each loaded chunk
 * (`fields=<that field>`, same sort/filters/offset) and merged in by row id — the rows, the scroll
 * position and the loading state all stay put. Hiding a column fetches nothing.
 */
export function useVirtualList<T>({ entityType, params, fields = [], fetch, enabled }: UseVirtualListOptions<T>) {
  const paramsKey = JSON.stringify(params);
  // `enabled` is part of the reset key: chunks requested while disabled (a schema-bearing list
  // waiting for its columns) would otherwise keep the column set they had then.
  const resetKey = `${enabled}|${paramsKey}`;
  const sortedFields = useMemo(() => [...fields].sort(), [fields.join(",")]);
  const fieldsRef = useRef(sortedFields);
  fieldsRef.current = sortedFields;

  const [requested, setRequested] = useState<{ key: string; chunks: RequestedChunk[] }>(() => ({
    key: resetKey,
    chunks: [{ index: 0, fields: sortedFields }],
  }));

  // A new result set (sort/search/filter change) starts over from the first chunk; the chunks
  // loaded for the previous one are meaningless at their old offsets. Reset during render (React's
  // "adjusting state when a prop changes" pattern), so no frame ever renders stale chunks against
  // the new totalCount.
  const isCurrent = requested.key === resetKey;
  const chunks = isCurrent ? requested.chunks : [{ index: 0, fields: sortedFields }];
  if (!isCurrent) setRequested({ key: resetKey, chunks });

  const baseQueries = chunks.map((chunk) => {
    const chunkParams: ListParams = {
      ...params,
      offset: chunk.index * CHUNK_SIZE,
      limit: CHUNK_SIZE,
      fields: fieldsParam(chunk.fields),
    };
    return { queryKey: ["entity-list", entityType, chunkParams], queryFn: () => fetch(chunkParams), enabled };
  });
  const supplementSpecs = chunks.flatMap((chunk, chunkPos) =>
    missingFields(sortedFields, chunk.fields).map((fieldId) => ({ chunkPos, fieldId, index: chunk.index })),
  );
  const supplementQueries = supplementSpecs.map(({ index, fieldId }) => {
    const chunkParams: ListParams = { ...params, offset: index * CHUNK_SIZE, limit: CHUNK_SIZE, fields: fieldId };
    return { queryKey: ["entity-list", entityType, chunkParams], queryFn: () => fetch(chunkParams), enabled };
  });

  const results = useQueries({ queries: [...baseQueries, ...supplementQueries] });
  const baseResults = results.slice(0, chunks.length);
  const supplementResults = results.slice(chunks.length);

  const totalCount = baseResults.find((r) => r.data != null)?.data?.totalCount ?? 0;
  const dataSignature = results.map((r) => `${r.dataUpdatedAt}/${r.errorUpdatedAt}`).join(",");
  const chunksSignature = chunks.map((c) => `${c.index}:${c.fields.join("+")}`).join(",");
  const supplementSignature = supplementSpecs.map((s) => `${s.chunkPos}:${s.fieldId}`).join(",");

  const rows = useMemo(
    () =>
      assembleRows<T>(
        totalCount,
        chunks.map((chunk, pos) => {
          const base = baseResults[pos]?.data?.data;
          const supplements = supplementSpecs
            // A failed supplement stops being pending (its cells stay empty; the error shows).
            .map((spec, i) => {
              const r = supplementResults[i];
              return { spec, rows: r?.data?.data ?? (r?.error ? [] : undefined) };
            })
            .filter(({ spec }) => spec.chunkPos === pos)
            .map(({ spec, rows }) => ({ fieldId: spec.fieldId, rows }));
          return { index: chunk.index, rows: base ? mergeSupplementRows(base, supplements) : undefined };
        }),
      ),
    // `results` itself is a new array every render; what matters is which chunks and supplements
    // there are and when they last changed, so the deps name exactly that.
    [totalCount, chunksSignature, supplementSignature, dataSignature],
  );

  const requestRange = useCallback(
    (first: number, last: number) => {
      const needed = chunksForRange(first, last);
      setRequested((current) => {
        if (current.key !== resetKey) return current;
        const missing = needed.filter((c) => !current.chunks.some((chunk) => chunk.index === c));
        if (missing.length === 0) return current;
        // A chunk first requested now carries every column visible now — no supplements needed.
        return { key: current.key, chunks: [...current.chunks, ...missing.map((index) => ({ index, fields: fieldsRef.current }))] };
      });
    },
    [resetKey],
  );

  return {
    rows,
    totalCount,
    // Only the very first chunk blocks the table; later ones show as placeholder rows instead, and
    // a column being added shows as skeleton cells.
    isLoading: baseResults[0]?.isLoading ?? false,
    // Any request in flight — a newly scrolled-to chunk, a column being added, or a background
    // refetch after an invalidation (cell edit, creation). Drives the list's progress bar.
    isFetching: results.some((r) => r.isFetching),
    error: results.find((r) => r.error)?.error ?? null,
    requestRange,
    // Identifies the current result set, e.g. to scroll back to the top when it changes. Columns
    // aren't part of it.
    resultSetKey: paramsKey,
  };
}
