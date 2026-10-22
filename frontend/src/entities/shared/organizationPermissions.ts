import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import { queryKeys } from "../../api/queryKeys";

// Mirrors OrganizationResourcePermissions: what the user may do in the organization on their own.
export interface OrganizationPermissions {
  canCreateProjects: boolean;
}

export async function getOrganizationPermissions(organizationId: number): Promise<OrganizationPermissions> {
  const body = await apiFetch<{ data: OrganizationPermissions }>(`/api/v1/organizations/${organizationId}/permissions`);
  return body.data;
}

export function useOrganizationPermissions(organizationId?: number) {
  return useQuery({
    queryKey: queryKeys.organizationPermissions(organizationId),
    queryFn: () => getOrganizationPermissions(organizationId as number),
    enabled: organizationId != null,
  });
}
