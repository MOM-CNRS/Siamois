import type {ListScope} from "../../entities/shared/types";
import type {ListViewMode} from "./listViews";

// What a user arranged on one list — which columns, in which order, and how the row actions are
// laid out — remembered in this browser (localStorage), per entity type and per context: the
// organization-wide list and a relation tab of the same type are arranged independently, since
// the tab already drops columns (ColumnDef.unscopedOnly) and usually wants fewer.
//
// Keyed by organization too: additional field ids belong to one institution, and two organizations
// (or two people sharing a browser and switching organization) must not overwrite each other's layout.
//
// The display mode (list, tree, cards) is kept here too: it belongs to the kind of list, not to the fiche
// it sits in, so it follows the user from one project's units tab to the next.
//
// Browser-local on purpose: a saved view (tableState.ts's `POST /ui-views`) is the server-side
// follow-up; this only has to survive a reload.

export interface ActionBarPrefs {
  // Every non-bookmark action key, in display order (inline first-to-last, then the "…" menu).
  order: string[];
  // The keys shown in the row itself; the rest go into the "…" menu.
  inline: string[];
}

// How a row opens a page: in the side overview (the default) or in the main pane, in focus.
export type OpenMode = "overview" | "focus";

export interface ListPrefs {
  v: 1;
  visibleColumns?: string[];
  actionBar?: ActionBarPrefs;
  viewMode?: ListViewMode;
  openMode?: OpenMode;
}

/** Project lists open in focus, every other list in the overview, until the user chooses. */
export function defaultOpenMode(entityType: string): OpenMode {
  return entityType === "project" ? "focus" : "overview";
}

export function listPrefsKey(entityType: string, scope?: ListScope, organizationId?: number): string {
  return `siamois.list.org${organizationId ?? "-"}.${entityType}.${scope ? `${scope.entityType}-tab` : "global"}`;
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((v) => typeof v === "string");
}

/** Tolerant: storage unavailable, corrupt, or from another version all read as "nothing saved". */
export function loadListPrefs(key: string): ListPrefs {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return { v: 1 };
    const parsed = JSON.parse(raw) as Partial<ListPrefs>;
    if (parsed?.v !== 1) return { v: 1 };
    const prefs: ListPrefs = { v: 1 };
    if (isStringArray(parsed.visibleColumns)) prefs.visibleColumns = parsed.visibleColumns;
    if (parsed.viewMode === "list" || parsed.viewMode === "tree" || parsed.viewMode === "cards") prefs.viewMode = parsed.viewMode;
    if (parsed.openMode === "overview" || parsed.openMode === "focus") prefs.openMode = parsed.openMode;
    const bar = parsed.actionBar;
    if (bar && isStringArray(bar.order) && isStringArray(bar.inline)) prefs.actionBar = { order: bar.order, inline: bar.inline };
    return prefs;
  } catch {
    return { v: 1 };
  }
}

export function saveListPrefs(key: string, patch: Omit<Partial<ListPrefs>, "v">): void {
  try {
    window.localStorage.setItem(key, JSON.stringify({ ...loadListPrefs(key), ...patch, v: 1 }));
  } catch {
    // Private window or storage full: the arrangement just won't outlive this page.
  }
}

/**
 * Lines a saved action layout up with the actions the list actually has now: keys that no longer
 * exist are dropped, and new ones are appended as inline — so an action added to a config later
 * shows up rather than silently landing in the menu. No saved layout: only the bookmark (always inline,
 * not one of these keys) shows in the row; every other action is put away in the "…" menu, in the config's
 * own order, until the user brings some back from the gear.
 */
export function reconcileActionBar(saved: ActionBarPrefs | undefined, keys: string[]): ActionBarPrefs {
  if (!saved) return { order: keys, inline: [] };
  const known = new Set(keys);
  const order = saved.order.filter((k) => known.has(k));
  const added = keys.filter((k) => !order.includes(k));
  return {
    order: [...order, ...added],
    inline: [...saved.inline.filter((k) => known.has(k)), ...added],
  };
}
