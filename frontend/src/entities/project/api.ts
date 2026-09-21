import { apiFetch } from "../../api/client";
import type { ListParams, PagedResult } from "../types";
import { filtersToQueryParams } from "../../panels/tableState";
import type { AnswerInputBody } from "../../fields/types";
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
  if (params.fields) query.set("fields", params.fields);
  if (params.filters) {
    // f.<key>[.from|.to] — ProjectListFilter's own contract; filtersToQueryParams is the one place
    // that owns the encoding, shared with the base64url ?s= state (plan §f).
    for (const [key, value] of filtersToQueryParams(params.filters).entries()) {
      query.append(key, value);
    }
  }

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

// Mirrors ProjectPatchRequest's fields — the flat subset (name/identifier/beginDate/endDate;
// typeId/mainLocationId/spatialContextSpatialUnitIds/geom exist server-side too but have no flat
// editor here) plus `answers`, the generic by-field-id path the list's click-to-edit overlay uses
// for every other default-visible column (status, oaCode, openingRate, periods, subjects,
// scientificManager, mainLocation, type, ...). Absent keys are left unchanged server-side
// (partial update), never sent as null. `answers` is applied AFTER the flat fields
// (ProjectPatchRequest.getAnswers() javadoc) — sending both for the same field, `answers` wins.
export interface ProjectPatch {
  name?: string;
  identifier?: string;
  beginDate?: string | null;
  endDate?: string | null;
  answers?: Record<string, AnswerInputBody>;
}

export async function patchProject(id: string | number, patch: ProjectPatch): Promise<ProjectDetail> {
  const body = await apiFetch<ProjectResponseBody>(`/api/v1/projects/${id}`, { method: "PATCH", body: patch });
  return body.data;
}
