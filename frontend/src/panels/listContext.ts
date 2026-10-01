import type { ListParams } from "../entities/types";

// Which list an entity was opened from, and where it sat in it: what "Fiche précédente/suivante"
// walks. The table knows both — its own request (sort, search, filters, scope) and the clicked
// row's absolute position — so the neighbours are simply the rows at position ±1 of that same
// request, whatever the entity type or the list (organization-wide, a relation tab…).
//
// Kept per entity in sessionStorage, not in App state: the fiche can show in the main pane or the
// overview, and it must survive an F5 and the browser's Back (which reloads) — a stored entry per
// entity does all of that, and moving to a neighbour just writes the neighbour's entry. Per tab,
// so two tabs never share a walk. Every failure reads as "no context": the arrows then fall back
// to the entity's default order (creation time, newest first).

export type ListRequest = Omit<ListParams, "offset" | "limit" | "fields">;

export interface ListContext {
  // The list request the entity was opened from (everything but the paging and the columns).
  params: ListRequest;
  // The entity's absolute position in that request's result set.
  index: number;
}

const STORAGE_KEY = "siamois.listContext.v1";
// Enough for every fiche of a long walk plus Back; the oldest entries go first.
const MAX_ENTRIES = 40;

interface Stored {
  v: 1;
  // Insertion order = age: the last key is the most recent.
  entries: Record<string, ListContext>;
}

function entryKey(entityType: string, id: string | number): string {
  return `${entityType}:${id}`;
}

function read(): Stored {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return { v: 1, entries: {} };
    const parsed = JSON.parse(raw) as Partial<Stored>;
    if (parsed?.v !== 1 || typeof parsed.entries !== "object" || parsed.entries == null) return { v: 1, entries: {} };
    return { v: 1, entries: parsed.entries as Record<string, ListContext> };
  } catch {
    return { v: 1, entries: {} };
  }
}

function isContext(value: unknown): value is ListContext {
  const v = value as ListContext | undefined;
  return v != null && typeof v.index === "number" && Number.isInteger(v.index) && v.index >= 0 && typeof v.params === "object" && v.params != null;
}

/** Remembers that `entityType`/`id` was opened at position `context.index` of `context.params`. */
export function rememberListContext(entityType: string, id: string | number, context: ListContext): void {
  try {
    const stored = read();
    const key = entryKey(entityType, id);
    delete stored.entries[key]; // re-inserted last: it is the most recent again
    stored.entries[key] = context;
    const keys = Object.keys(stored.entries);
    for (const old of keys.slice(0, Math.max(0, keys.length - MAX_ENTRIES))) delete stored.entries[old];
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
  } catch {
    // Storage unavailable or full: the arrows just use the default order.
  }
}

/** Where this entity was last opened from, if it was opened from a list in this tab. */
export function recallListContext(entityType: string, id: string | number): ListContext | undefined {
  const context = read().entries[entryKey(entityType, id)];
  return isContext(context) ? context : undefined;
}
