import { apiFetch } from "../client";
import type { FilterValue } from "../../entities/shared/types";
import type { ViewTableState } from "./views";

// GET/POST/DELETE /api/v1/home-accesses — the cards of the home page's « Accès aux données » section: a table on a
// view (which holds the type to show). Every member sees them; who may edit the organization's default views
// adds and removes them. Adding one creates its view.
export interface HomeAccess {
  id: number;
  viewId: number;
  // The table (key of the entity registry).
  resourceType: string;
  // The view's name: what the card says.
  title: string;
  // Legacy card caption; the card shows `title`.
  label: string;
  // The view's filters, to count what it shows.
  filters?: Record<string, FilterValue> | null;
}

export interface HomeAccessList {
  data: HomeAccess[];
  canEdit: boolean;
}

export async function listHomeAccesses(organizationId: number): Promise<HomeAccessList> {
  return apiFetch<HomeAccessList>(`/api/v1/home-accesses?organizationId=${organizationId}`);
}

// Either an existing view of the organization (`viewId`), or the table, title and state of a view to create.
export async function createHomeAccess(body: {
  organizationId: number;
  viewId?: number;
  resourceType?: string;
  title?: string;
  state?: ViewTableState;
}): Promise<HomeAccess> {
  return apiFetch<HomeAccess>("/api/v1/home-accesses", { method: "POST", body });
}

export async function deleteHomeAccess(organizationId: number, id: number): Promise<void> {
  await apiFetch<void>(`/api/v1/home-accesses/${id}?organizationId=${organizationId}`, { method: "DELETE" });
}

export async function reorderHomeAccesses(organizationId: number, ids: number[]): Promise<void> {
  await apiFetch<void>("/api/v1/home-accesses/order", { method: "PUT", body: { organizationId, ids } });
}
