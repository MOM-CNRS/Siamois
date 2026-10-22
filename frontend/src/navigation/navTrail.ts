// The pages the user went through, as a Back/Forward trail the header arrows walk: one entry per page
// (main pane and overview), the address bar's own URL as its key. The server's history is a list of
// recent pages (most recent first, each once): it cannot tell "the page before this one" once the user
// has gone back, so the trail is kept here. Kept on `globalThis` (the bundle may run again when JSF
// re-renders the flow) and in sessionStorage, so a page load does not forget it.

export interface TrailEntry {
  // The address bar for this page (encodeUrl): what restoring it opens.
  url: string;
  mainPath: string;
  overviewPath?: string;
}

interface TrailState {
  entries: TrailEntry[];
  // The entry the user is on.
  index: number;
  listeners: Set<() => void>;
}

const MAX_ENTRIES = 50;
const STORAGE_KEY = "siamois-nav-trail";

function load(): Pick<TrailState, "entries" | "index"> {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as Pick<TrailState, "entries" | "index">) : null;
    if (parsed && Array.isArray(parsed.entries) && Number.isInteger(parsed.index)) return parsed;
  } catch {
    // Storage blocked or garbled: start empty.
  }
  return { entries: [], index: -1 };
}

const holder = globalThis as { __siamoisNavTrail?: TrailState };
const trail: TrailState = (holder.__siamoisNavTrail ??= { ...load(), listeners: new Set() });

function changed() {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ entries: trail.entries, index: trail.index }));
  } catch {
    // Not kept across page loads, still works in this one.
  }
  trail.listeners.forEach((listener) => listener());
}

/** The user is on this page. The page the trail already stands on is only refreshed (a reload, a Back/Forward move). */
export function recordVisit(entry: TrailEntry): void {
  const current = trail.entries[trail.index];
  if (current?.url === entry.url) return;
  // Going somewhere new after going back drops what was ahead, like a browser does.
  trail.entries = [...trail.entries.slice(0, trail.index + 1), entry].slice(-MAX_ENTRIES);
  trail.index = trail.entries.length - 1;
  changed();
}

export function previousEntry(): TrailEntry | undefined {
  return trail.entries[trail.index - 1];
}

export function nextEntry(): TrailEntry | undefined {
  return trail.entries[trail.index + 1];
}

/** Moves the trail one page back (-1) or forward (+1) and returns the page to open; undefined at either end. */
export function moveTrail(delta: -1 | 1): TrailEntry | undefined {
  const target = trail.entries[trail.index + delta];
  if (!target) return undefined;
  trail.index += delta;
  changed();
  return target;
}

export function subscribeTrail(listener: () => void): () => void {
  trail.listeners.add(listener);
  return () => trail.listeners.delete(listener);
}

/** A snapshot that changes whenever the trail does, for useSyncExternalStore. */
export function trailVersion(): string {
  return `${trail.index}/${trail.entries.length}/${trail.entries[trail.index]?.url ?? ""}`;
}
