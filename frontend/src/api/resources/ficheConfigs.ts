import {apiFetch} from "../client";

// GET/PUT/DELETE /api/v1/fiche-configs — how a project arranges the tabs of a record type's fiche (which ones,
// in which order, under which name, and the list tabs added to the built-in ones). Everybody reads it; saving
// needs the right to edit default views. `tabs` is opaque to the server: its shape is ours (panels/detail/ficheConfig.ts).
export interface FicheConfigResponse {
  // Absent while nobody configured this fiche.
  tabs?: Record<string, unknown> | null;
  // The sub-type the configuration was saved for; absent for every sub-type.
  typeId?: number | null;
  updatedAt?: string | null;
  canEdit: boolean;
}

export interface FicheConfigScope {
  organizationId: number;
  resourceType: string;
  projectId?: number;
  typeId?: number;
}

function queryOf(scope: FicheConfigScope): string {
  const query = new URLSearchParams({ organizationId: String(scope.organizationId), resourceType: scope.resourceType });
  if (scope.projectId != null) query.set("projectId", String(scope.projectId));
  if (scope.typeId != null) query.set("typeId", String(scope.typeId));
  return query.toString();
}

export async function getFicheConfig(scope: FicheConfigScope): Promise<FicheConfigResponse> {
  return apiFetch<FicheConfigResponse>(`/api/v1/fiche-configs?${queryOf(scope)}`);
}

export async function putFicheConfig(scope: FicheConfigScope, tabs: Record<string, unknown>): Promise<FicheConfigResponse> {
  return apiFetch<FicheConfigResponse>("/api/v1/fiche-configs", {
    method: "PUT",
    body: { ...scope, tabs },
  });
}

export async function deleteFicheConfig(scope: FicheConfigScope): Promise<void> {
  await apiFetch<void>(`/api/v1/fiche-configs?${queryOf(scope)}`, { method: "DELETE" });
}
