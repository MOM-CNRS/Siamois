import {getEntityType} from "../../entities/registry";
import type {DetailTabDef, FilterValue} from "../../entities/shared/types";
import {parseFicheTabs} from "./ficheConfig";

/** One line of the tabs editor: a tab of the fiche as the project arranges it, hidden ones included. */
export interface EditableTab {
  key: string;
  // The name the project gave it; empty = the code's own name.
  label: string;
  // The code's name (a built-in tab), or what a list tab is called when left unnamed.
  defaultLabel: string;
  // A removed built-in tab stays in the configuration as hidden, so it can be put back.
  hidden: boolean;
  // Whether the tab can be removed: the lists (those the project added, and the built-in relation tabs).
  // The fiche's own tabs (details, graph…) cannot.
  removable: boolean;
  // A list tab the project added: what it lists and the filters that define it.
  kind?: "list";
  target?: string;
  baseFilters?: Record<string, FilterValue>;
}

export const CUSTOM_KEY_PREFIX = "custom:";

/** The types a fiche may add as list tabs: those its built-in relation tabs already list. */
export function relationTargets(code: DetailTabDef<never>[]): string[] {
  return [...new Set(code.flatMap((tab) => (tab.relation ? [tab.relation.target] : [])))].filter((t) => getEntityType(t) != null);
}

export function targetLabel(target: string): string {
  return getEntityType(target)?.labels.plural ?? target;
}

/**
 * The editor's lines for the configuration `saved`: its tabs in order, then the code's tabs it does not name
 * (shown at the end, as the fiche does). A name the code no longer has, or a list of a type that is gone, is left out:
 * saving drops it.
 */
export function editableTabs(code: DetailTabDef<never>[], saved: unknown): EditableTab[] {
  const byKey = new Map(code.map((tab) => [tab.key, tab]));
  const rows: EditableTab[] = [];
  const named = new Set<string>();
  for (const tab of parseFicheTabs(saved)) {
    named.add(tab.key);
    if (tab.kind === "list") {
      if (getEntityType(tab.target!) == null) continue;
      rows.push({
        key: tab.key,
        label: tab.label ?? "",
        defaultLabel: targetLabel(tab.target!),
        hidden: tab.hidden === true,
        removable: true,
        kind: "list",
        target: tab.target,
        baseFilters: tab.baseFilters ?? {},
      });
      continue;
    }
    const builtin = byKey.get(tab.key);
    // A tab that cannot be removed is always shown, whatever an older configuration said.
    if (builtin) {
      const removable = builtin.relation != null;
      rows.push({ key: tab.key, label: tab.label ?? "", defaultLabel: builtin.label, hidden: removable && tab.hidden === true, removable });
    }
  }
  for (const tab of code) {
    if (!named.has(tab.key)) rows.push({ key: tab.key, label: "", defaultLabel: tab.label, hidden: false, removable: tab.relation != null });
  }
  return rows;
}

/** What is saved for these lines: every tab, in order, so a later reorder of the code's tabs does not move them. */
export function toConfigTabs(rows: EditableTab[]): Record<string, unknown> {
  return {
    v: 1,
    tabs: rows.map((row) => {
      const label = row.label.trim();
      const tab: Record<string, unknown> = { key: row.key };
      if (label) tab.label = label;
      if (row.hidden) tab.hidden = true;
      if (row.kind === "list") {
        tab.kind = "list";
        tab.target = row.target;
        tab.baseFilters = row.baseFilters ?? {};
      }
      return tab;
    }),
  };
}

/** A new list tab; its name is never empty, so it can be told from the others. */
export function newListTab(target: string, label: string, baseFilters: Record<string, FilterValue>): EditableTab {
  const name = label.trim() || targetLabel(target);
  return {
    key: `${CUSTOM_KEY_PREFIX}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
    label: name,
    defaultLabel: name,
    hidden: false,
    removable: true,
    kind: "list",
    target,
    baseFilters,
  };
}

/** Moves a shown tab by `delta` places among the shown ones (-1 up, +1 down); the same lines when it can't move. */
export function moveTab(rows: EditableTab[], key: string, delta: number): EditableTab[] {
  const shown = shownTabs(rows);
  const from = shown.findIndex((row) => row.key === key);
  const neighbour = shown[from + delta];
  if (from < 0 || neighbour == null) return rows;
  const i = rows.findIndex((row) => row.key === key);
  const j = rows.findIndex((row) => row.key === neighbour.key);
  const next = rows.slice();
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}

/**
 * Drops a shown tab on another one: it takes that place among the shown tabs (after it when moving down,
 * before it when moving up). The same lines when either is unknown or they are the same.
 */
export function moveTabTo(rows: EditableTab[], key: string, targetKey: string): EditableTab[] {
  if (key === targetKey) return rows;
  const shown = shownTabs(rows);
  const from = shown.findIndex((row) => row.key === key);
  const to = shown.findIndex((row) => row.key === targetKey);
  if (from < 0 || to < 0) return rows;
  const moved = rows.find((row) => row.key === key)!;
  const rest = rows.filter((row) => row.key !== key);
  const at = rest.findIndex((row) => row.key === targetKey);
  rest.splice(from < to ? at + 1 : at, 0, moved);
  return rest;
}

/** At least one tab stays visible: a fiche without any would be empty. */
export function hasVisibleTab(rows: EditableTab[]): boolean {
  return rows.some((row) => !row.hidden);
}

/** What the editor shows: the tabs that are not removed. */
export function shownTabs(rows: EditableTab[]): EditableTab[] {
  return rows.filter((row) => !row.hidden);
}

/** The built-in tabs that were removed, which can be put back. */
export function restorableTabs(rows: EditableTab[]): EditableTab[] {
  return rows.filter((row) => row.hidden && row.kind == null);
}

/** Removes a tab: a built-in one is hidden (it can come back), a list added by the project is dropped. */
export function removeTab(rows: EditableTab[], key: string): EditableTab[] {
  return rows.flatMap((row) => {
    if (row.key !== key || !row.removable) return [row];
    return row.kind === "list" ? [] : [{ ...row, hidden: true }];
  });
}

/** Puts a removed built-in tab back, at the end. */
export function restoreTab(rows: EditableTab[], key: string): EditableTab[] {
  const restored = rows.find((row) => row.key === key && row.hidden);
  return restored ? [...rows.filter((row) => row !== restored), { ...restored, hidden: false }] : rows;
}
