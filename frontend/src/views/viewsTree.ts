import type {ViewResource} from "../api/resources/views";

/**
 * The project a saved view is about, when it can be told: a view whose own filters pick exactly one project
 * (a "project" filter on the list). The server keeps no project on a user view, so any other view has none
 * and is shown with its table.
 */
export function projectOfView(view: ViewResource): string | undefined {
  const filter = view.state?.listFilters?.project as { op?: string; v?: unknown } | undefined;
  if (filter?.op === "in" && Array.isArray(filter.v) && filter.v.length === 1) return String(filter.v[0]);
  return undefined;
}

export interface ViewGroups {
  // Views tied to a project, by project id.
  byProject: Map<string, ViewResource[]>;
  // The others, by table (the entity type key).
  byTable: Map<string, ViewResource[]>;
}

/** Splits the user's views into those of a project and those of a table, each group most recent first. */
export function groupViews(views: ViewResource[]): ViewGroups {
  const byProject = new Map<string, ViewResource[]>();
  const byTable = new Map<string, ViewResource[]>();
  for (const view of views) {
    const projectId = projectOfView(view);
    const [map, key] = projectId == null ? [byTable, view.resourceType] : [byProject, projectId];
    map.set(key, [...(map.get(key) ?? []), view]);
  }
  return { byProject, byTable };
}

/** Whether `text` matches the search, ignoring case; an empty search matches everything. */
export function matches(text: string, search: string): boolean {
  const needle = search.trim().toLowerCase();
  return needle === "" || text.toLowerCase().includes(needle);
}
