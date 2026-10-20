import { apiFetch } from "./client";

// The pages the user opened lately (GET /api/v1/history), for the home page. Paths of the app
// (`/action-unit/12`, `/recording-unit?viewId=3`), opened like a sidebar entry would be.
export interface HistoryShortcut {
  id: number;
  // The overview's title when the page had one open next to the main pane, else the main pane's.
  title: string;
  icon?: string | null;
  mainUri: string;
  mainTitle: string;
  secondaryUri?: string | null;
  secondaryTitle?: string | null;
}

export async function listHistory(organizationId: number, limit: number): Promise<HistoryShortcut[]> {
  const query = new URLSearchParams({ organizationId: String(organizationId), limit: String(limit) });
  const body = await apiFetch<{ data: HistoryShortcut[] }>(`/api/v1/history?${query.toString()}`);
  return body.data;
}
