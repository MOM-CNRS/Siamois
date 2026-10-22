import { apiFetch } from "../client";

// GET/PUT/DELETE /api/v1/view-states — what the current user changed on one view (filters, sort,
// columns, page size, list/tree mode, the active tab of a fiche…), kept server-side so they find the
// same state on any browser. `state` is opaque to the server: its shape is ours (panels/list/listState.ts).
export type ViewStateBody = Record<string, unknown>;

export interface ViewStateResponse {
  viewKey: string;
  // null when the user changed nothing on this view yet.
  state: ViewStateBody | null;
}

export async function getViewState(organizationId: number, viewKey: string): Promise<ViewStateResponse> {
  const query = new URLSearchParams({ organizationId: String(organizationId), viewKey });
  return apiFetch<ViewStateResponse>(`/api/v1/view-states?${query.toString()}`);
}

export async function putViewState(organizationId: number, viewKey: string, state: ViewStateBody): Promise<void> {
  // keepalive: the last change before the tab closes is still sent.
  await apiFetch<ViewStateResponse>("/api/v1/view-states", {
    method: "PUT",
    body: { organizationId, viewKey, state },
    keepalive: true,
  });
}

export async function deleteViewState(organizationId: number, viewKey: string): Promise<void> {
  const query = new URLSearchParams({ organizationId: String(organizationId), viewKey });
  await apiFetch<void>(`/api/v1/view-states?${query.toString()}`, { method: "DELETE" });
}

// GET/POST/PUT/DELETE /api/v1/views — the saved views of an organization: the state of a list, named. Every
// member of the organization sees them; only the owner changes or deletes one.

/** The state of a saved view: the React list's own state, as the server stores it (TableViewState v2). */
export interface ViewTableState {
  version?: number;
  visibleColumns?: string[];
  sort?: string;
  search?: string;
  limit?: number;
  viewMode?: "list" | "tree" | "cards";
  typeSegment?: string;
  // Filters by column id, in the shape of the list's own filters (panels/list/tableState.ts's FilterValue).
  listFilters?: Record<string, unknown>;
}

export interface ViewResource {
  id: number;
  resourceType: string;
  title: string;
  kind: "USER" | "DEFAULT";
  scopeEntityType?: string | null;
  projectId?: number | null;
  typeId?: number | null;
  state: ViewTableState;
  ownerId?: number | null;
  ownerName?: string | null;
  // The current user may change or delete it (they own it).
  canEdit: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ViewList {
  data: ViewResource[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface ViewWriteBody {
  organizationId: number;
  resourceType: string;
  title: string;
  scopeEntityType?: string;
  state: ViewTableState;
}

export interface ListViewsParams {
  resourceType?: string;
  q?: string;
  limit?: number;
  offset?: number;
}

export async function listViews(organizationId: number, params: ListViewsParams = {}): Promise<ViewList> {
  const query = new URLSearchParams({ organizationId: String(organizationId) });
  if (params.resourceType) query.set("resourceType", params.resourceType);
  if (params.q) query.set("q", params.q);
  if (params.limit != null) query.set("limit", String(params.limit));
  if (params.offset != null) query.set("offset", String(params.offset));
  return apiFetch<ViewList>(`/api/v1/views?${query.toString()}`);
}

export async function getView(organizationId: number, id: number): Promise<ViewResource> {
  return apiFetch<ViewResource>(`/api/v1/views/${id}?organizationId=${organizationId}`);
}

export async function createView(body: ViewWriteBody): Promise<ViewResource> {
  return apiFetch<ViewResource>("/api/v1/views", { method: "POST", body });
}

export async function updateView(id: number, body: ViewWriteBody): Promise<ViewResource> {
  return apiFetch<ViewResource>(`/api/v1/views/${id}`, { method: "PUT", body });
}

export async function deleteView(organizationId: number, id: number): Promise<void> {
  await apiFetch<void>(`/api/v1/views/${id}?organizationId=${organizationId}`, { method: "DELETE" });
}
