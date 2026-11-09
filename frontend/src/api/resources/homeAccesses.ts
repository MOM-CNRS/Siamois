import {apiFetch} from "../client";
import type {FilterValue} from "../../entities/shared/types";
import type {ViewTableState} from "./views";

// GET/POST/DELETE /api/v1/home-accesses — the cards of the home page's « Accès aux données » section: a table on a
// view (which holds the type to show), or a table on its own base view. Every member sees them; who may edit the
// organization's default views adds and removes them. Adding a view card creates its view.
// An organization that configured nothing gets one card per table, not stored yet (`defaults`, `id` null): the
// first change stores them (customizeHomeAccesses).
export interface HomeAccess {
  // Null on a default card, not stored yet.
  id: number | null;
  // Null on a card of a table's base view.
  viewId: number | null;
  // The table (key of the entity registry).
  resourceType: string;
  // The view's name: what the card says; null on a table card (it says the table's name).
  title: string | null;
  // Legacy card caption; the card shows `title`.
  label: string;
  // The view's filters, to count what it shows.
  filters?: Record<string, FilterValue> | null;
}

export interface HomeAccessList {
  data: HomeAccess[];
  canEdit: boolean;
  defaults: boolean;
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
  // A card on the base view of the table `resourceType`, with no view.
  table?: boolean;
}): Promise<HomeAccess> {
  return apiFetch<HomeAccess>("/api/v1/home-accesses", { method: "POST", body });
}

export async function deleteHomeAccess(organizationId: number, id: number): Promise<void> {
  await apiFetch<void>(`/api/v1/home-accesses/${id}?organizationId=${organizationId}`, { method: "DELETE" });
}

export async function reorderHomeAccesses(organizationId: number, ids: number[]): Promise<void> {
  await apiFetch<void>("/api/v1/home-accesses/order", { method: "PUT", body: { organizationId, ids } });
}

/** Stores the default cards (one per table) so they can be changed; no effect once cards are stored. */
export async function customizeHomeAccesses(organizationId: number): Promise<HomeAccessList> {
  return apiFetch<HomeAccessList>(`/api/v1/home-accesses/customize?organizationId=${organizationId}`, { method: "POST" });
}

/** Removes every card (the views stay): the organization is back to one card per table. */
export async function resetHomeAccesses(organizationId: number): Promise<void> {
  await apiFetch<void>(`/api/v1/home-accesses?organizationId=${organizationId}`, { method: "DELETE" });
}
