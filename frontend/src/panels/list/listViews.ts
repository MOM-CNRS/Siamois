import type {EntityTypeConfig, ListScope} from "../../entities/shared/types";
import {type MessageKey, t} from "../../i18n";

/**
 * How a list is shown. The table's own views are "list" (flat, paged) and "tree" (the hierarchy);
 * other kinds of view (a map, a timeline…) will join this union, each with its own entry in
 * LIST_VIEWS and its own availability rule in `availableViews`. Chosen in the gear menu, remembered
 * per pane.
 */
export type ListViewMode = "list" | "tree" | "cards";

export const DEFAULT_LIST_VIEW: ListViewMode = "list";

interface ListViewDefinition {
  label: MessageKey;
  icon: string;
}

export const LIST_VIEWS: Record<ListViewMode, ListViewDefinition> = {
  list: { label: "list.viewModeList", icon: "bi bi-list-ul" },
  tree: { label: "list.viewModeTree", icon: "bi bi-list-nested" },
  cards: { label: "list.viewModeCards", icon: "bi bi-grid-3x3-gap" },
};

export interface ListViewOption {
  mode: ListViewMode;
  label: string;
  icon: string;
}

/**
 * How a context is named in `list.treeScopes`: its parent's type, plus the sub-collection when the list
 * is not the parent's default one ("recordingUnit:children" is the units below a unit, where
 * "recordingUnit:parents" would be its ancestors).
 */
export function treeScopeKey(scope: ListScope): string {
  return scope.path ? `${scope.entityType}:${scope.path}` : scope.entityType;
}

/** The views `config`'s list offers inside `scope` (none when there is only the flat list). */
export function availableViews(
  config: EntityTypeConfig<unknown, unknown> | undefined,
  scope: ListScope | undefined,
): ListViewMode[] {
  if (!config) return [DEFAULT_LIST_VIEW];
  const views: ListViewMode[] = [DEFAULT_LIST_VIEW];
  // A hierarchy view of the organization-wide list, or of a context the type declares.
  if (
    config.list.tree === true &&
    (scope == null ||
      config.list.treeScopes?.includes(treeScopeKey(scope)) === true)
  ) {
    views.push("tree");
  }
  // A grid of cards, for a type that says how its rows look as one.
  if (config.list.card != null) views.push("cards");
  return views;
}

/** The mode to show: the remembered one if this list still offers it, else the type's default, else the flat list. */
export function resolveViewMode(
  remembered: ListViewMode | undefined,
  available: ListViewMode[],
  preferred?: ListViewMode,
): ListViewMode {
  if (remembered != null && available.includes(remembered)) return remembered;
  // Nothing chosen: the type's own starting mode, when this list offers it.
  return remembered == null && preferred != null && available.includes(preferred) ? preferred : DEFAULT_LIST_VIEW;
}

export function viewOptions(available: ListViewMode[]): ListViewOption[] {
  return available.map((mode) => ({
    mode,
    label: t(LIST_VIEWS[mode].label),
    icon: LIST_VIEWS[mode].icon,
  }));
}
