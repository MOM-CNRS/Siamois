import { apiFetch } from "../api/client";
import type { CreatableKind, PagedResult } from "./types";

// The fields of a project a create form's project picker shows (a ProjectResource row).
export interface CreatableProject {
  id: string;
  fullIdentifier: string;
  name?: string | null;
}

/**
 * What a create form can be pointed at when its list has no project of its own: the organization's
 * projects in which the caller may create `kind` — GET /api/v1/projects?canCreate=…, the same
 * permission rule each create endpoint enforces.
 */
export async function searchCreatableProjects(
  organizationId: number,
  kind: CreatableKind,
  search: string | undefined,
  limit = 20,
): Promise<PagedResult<CreatableProject>> {
  const query = new URLSearchParams({
    organizationId: String(organizationId),
    canCreate: kind,
    offset: "0",
    limit: String(limit),
    sort: "name:asc",
  });
  if (search) query.set("search", search);
  const body = await apiFetch<{ data: CreatableProject[]; meta?: { total?: number } }>(`/api/v1/projects?${query.toString()}`);
  return { data: body.data, totalCount: body.meta?.total ?? body.data.length, limit, offset: 0 };
}
