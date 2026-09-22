import { apiFetch } from "../../api/client";
import { fetchList } from "../listApi";
import type { ListParams, PagedResult } from "../types";
import type { AnswerInputBody } from "../../fields/types";
import type { ProjectDetail, ProjectSummary } from "./types";

interface ProjectResponseBody {
  data: ProjectDetail;
}

export async function listProjects(params: ListParams): Promise<PagedResult<ProjectSummary>> {
  return fetchList<ProjectSummary>("projects", params);
}

/**
 * @param fields projection des champs de formulaire dans `answers` — "all", "default", ou une liste
 *   d'ids séparés par des virgules. La fiche demande "all" : elle rend tout le layout, pas une
 *   sélection de colonnes. Omis, la réponse n'a pas de clé `answers` et coûte ce qu'elle coûtait.
 */
export async function getProject(id: string | number, fields?: string): Promise<ProjectDetail> {
  const query = fields ? `?fields=${encodeURIComponent(fields)}` : "";
  const body = await apiFetch<ProjectResponseBody>(`/api/v1/projects/${id}${query}`);
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
  // The flat alias for the project type (ProjectPatchRequest.typeId, @JsonAlias typeConceptId) —
  // the header's category chip writes here rather than through `answers`, matching the server's
  // own documented path for that field.
  typeId?: string | null;
  beginDate?: string | null;
  endDate?: string | null;
  // The one field the fiche cannot route through `answers`: ProjectApiService.coerceScalarAnswer
  // throws 400 for CustomFieldSelectMultipleSpatialUnitTree, so SPATIAL_CONTEXT_FIELD writes here.
  spatialContextSpatialUnitIds?: string[];
  answers?: Record<string, AnswerInputBody>;
}

export async function patchProject(id: string | number, patch: ProjectPatch): Promise<ProjectDetail> {
  const body = await apiFetch<ProjectResponseBody>(`/api/v1/projects/${id}`, { method: "PATCH", body: patch });
  return body.data;
}
