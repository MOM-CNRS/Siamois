// The pages the user went through, as a Back/Forward trail the header arrows walk: one entry per page
// (main pane and overview), the address bar's own URL as its key. The server's history is a list of
// recent pages (most recent first, each once): it cannot tell "the page before this one" once the user
// has gone back, so the trail is kept here. Like that history, there is one trail per organisation: the
// arrows never lead to a page of another one. Kept on `globalThis` (the bundle may run again when JSF
// re-renders the flow) and in sessionStorage, so a page load does not forget it.

export interface TrailEntry {
  // The address bar for this page (encodeUrl): what restoring it opens.
  url: string;
  mainPath: string;
  overviewPath?: string;
}

interface Trail {
  entries: TrailEntry[];
  // The entry the user is on.
  index: number;
}

interface TrailStore {
  // By storage key, one per organisation.
  trails: Map<string, Trail>;
  listeners: Set<() => void>;
}

const MAX_ENTRIES = 50;
const STORAGE_KEY_PREFIX = "siamois-nav-trail-";

function storageKey(organizationId: number | undefined): string {
  return `${STORAGE_KEY_PREFIX}${organizationId ?? "none"}`;
}

function load(key: string): Trail {
  try {
    const raw = window.sessionStorage.getItem(key);
    const parsed = raw ? (JSON.parse(raw) as Trail) : null;
    if (parsed && Array.isArray(parsed.entries) && Number.isInteger(parsed.index)) return parsed;
  } catch {
    // Storage blocked or garbled: start empty.
  }
  return { entries: [], index: -1 };
}

const holder = globalThis as { __siamoisNavTrail?: TrailStore };
const store: TrailStore = (holder.__siamoisNavTrail ??= { trails: new Map(), listeners: new Set() });

function trailOf(organizationId: number | undefined): Trail {
  const key = storageKey(organizationId);
  const known = store.trails.get(key);
  if (known) return known;
  const loaded = load(key);
  store.trails.set(key, loaded);
  return loaded;
}

function changed(organizationId: number | undefined, trail: Trail) {
  try {
    window.sessionStorage.setItem(storageKey(organizationId), JSON.stringify(trail));
  } catch {
    // Not kept across page loads, still works in this one.
  }
  store.listeners.forEach((listener) => listener());
}

/** The user is on this page. The page the trail already stands on is only refreshed (a reload, a Back/Forward move). */
export function recordVisit(organizationId: number | undefined, entry: TrailEntry): void {
  const trail = trailOf(organizationId);
  const current = trail.entries[trail.index];
  if (current?.url === entry.url) return;
  // Going somewhere new after going back drops what was ahead, like a browser does.
  trail.entries = [...trail.entries.slice(0, trail.index + 1), entry].slice(-MAX_ENTRIES);
  trail.index = trail.entries.length - 1;
  changed(organizationId, trail);
}

export function previousEntry(organizationId: number | undefined): TrailEntry | undefined {
  const trail = trailOf(organizationId);
  return trail.entries[trail.index - 1];
}

export function nextEntry(organizationId: number | undefined): TrailEntry | undefined {
  const trail = trailOf(organizationId);
  return trail.entries[trail.index + 1];
}

/** Moves the trail one page back (-1) or forward (+1) and returns the page to open; undefined at either end. */
export function moveTrail(organizationId: number | undefined, delta: -1 | 1): TrailEntry | undefined {
  const trail = trailOf(organizationId);
  const target = trail.entries[trail.index + delta];
  if (!target) return undefined;
  trail.index += delta;
  changed(organizationId, trail);
  return target;
}

export function subscribeTrail(listener: () => void): () => void {
  store.listeners.add(listener);
  return () => store.listeners.delete(listener);
}

/** A snapshot that changes whenever the organisation's trail does, for useSyncExternalStore. */
export function trailVersion(organizationId: number | undefined): string {
  const trail = trailOf(organizationId);
  return `${trail.index}/${trail.entries.length}/${trail.entries[trail.index]?.url ?? ""}`;
}
