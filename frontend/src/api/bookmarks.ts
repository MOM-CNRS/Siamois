import { apiFetch } from "./client";

// POST/DELETE /api/v1/bookmarks (plan §5/§8 phase 8) — generic, not Project-specific:
// resourceUri is the plain app route (e.g. "/action-unit/123"), not a REST path. The caller
// always knows the current bookmarked state (read off the entity's own `bookmarked` field, or
// carried in via PanelChrome), so there's no toggle endpoint — create or delete explicitly.
export interface BookmarkRequest {
  resourceUri: string;
  titleCode: string;
  organizationId: number;
}

export async function createBookmark(request: BookmarkRequest): Promise<void> {
  await apiFetch<void>("/api/v1/bookmarks", { method: "POST", body: request });
}

export async function deleteBookmark(resourceUri: string, organizationId: number): Promise<void> {
  const query = new URLSearchParams({ resourceUri, organizationId: String(organizationId) });
  await apiFetch<void>(`/api/v1/bookmarks?${query.toString()}`, { method: "DELETE" });
}
