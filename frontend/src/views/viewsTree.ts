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

/** Whether `text` matches the search, ignoring case; an empty search matches everything. */
export function matches(text: string, search: string): boolean {
  const needle = search.trim().toLowerCase();
  return needle === "" || text.toLowerCase().includes(needle);
}
