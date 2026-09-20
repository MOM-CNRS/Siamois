import { apiFetch } from "../../api/client";
import type { ListParams, PagedResult } from "../types";
import type { ProjectDetail, ProjectSummary } from "./types";

// Response envelopes match ProjectListResponse/ProjectResponse exactly (plan §5's existing,
// unchanged GET /api/v1/projects / GET /api/v1/projects/{id}) — meta.total, not totalCount, is
// the wire field name; PagedResult normalizes that for the generic panels.
interface ListMetaResponse {
  total: number;
  limit: number;
  offset: number;
}

interface ProjectListResponseBody {
  data: ProjectSummary[];
  meta: ListMetaResponse;
}

interface ProjectResponseBody {
  data: ProjectDetail;
}

export async function listProjects(params: ListParams): Promise<PagedResult<ProjectSummary>> {
  const query = new URLSearchParams();
  query.set("offset", String(params.offset));
  query.set("limit", String(params.limit));
  if (params.search) query.set("search", params.search);
  if (params.sort) query.set("sort", params.sort);
  if (params.organizationId != null) query.set("organizationId", String(params.organizationId));

  const body = await apiFetch<ProjectListResponseBody>(`/api/v1/projects?${query.toString()}`);
  return {
    data: body.data,
    totalCount: body.meta.total,
    limit: body.meta.limit,
    offset: body.meta.offset,
  };
}

export async function getProject(id: string | number): Promise<ProjectDetail> {
  const body = await apiFetch<ProjectResponseBody>(`/api/v1/projects/${id}`);
  return body.data;
}

// Mirrors ProjectPatchRequest's fields — a strict subset (name/identifier/beginDate/endDate;
// typeId/mainLocationId/spatialContextSpatialUnitIds/geom exist server-side too but have no
// working editor yet, fields/registerDefaultRenderers.ts). Absent keys are left unchanged
// server-side (partial update), never sent as null.
export interface ProjectPatch {
  name?: string;
  identifier?: string;
  beginDate?: string | null;
  endDate?: string | null;
}

export async function patchProject(id: string | number, patch: ProjectPatch): Promise<ProjectDetail> {
  const body = await apiFetch<ProjectResponseBody>(`/api/v1/projects/${id}`, { method: "PATCH", body: patch });
  return body.data;
}
