import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../api/client";
import { queryKeys } from "../api/queryKeys";

// Mirrors OrganizationCountsResource — one request feeds every home "database access" card.
export interface OrganizationCounts {
  projects: number;
  places: number;
  recordingUnits: number;
  finds: number;
  phases: number;
  containers: number;
  documents: number;
}

export async function getOrganizationCounts(organizationId: number): Promise<OrganizationCounts> {
  const body = await apiFetch<{ data: OrganizationCounts }>(`/api/v1/organizations/${organizationId}/counts`);
  return body.data;
}

// Same query key for every card, so react-query dedupes the six cards down to one request.
export function useOrganizationCounts(organizationId?: number) {
  return useQuery({
    queryKey: queryKeys.organizationCounts(organizationId),
    queryFn: () => getOrganizationCounts(organizationId as number),
    enabled: organizationId != null,
  });
}
