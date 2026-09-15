import { apiFetch } from "./client";
import type { FieldResource } from "../form/schema";
import type { ResolvedConceptResource } from "./recordingUnit";

export interface RecordingUnitType {
  concept: ResolvedConceptResource | null;
  id: string;
  formBundle: { resourceType: "forms"; layoutJson: string };
  fields: Record<string, FieldResource>;
}

interface ProjectRecordingUnitTypeListResponse {
  data: RecordingUnitType[];
}

let cache: { projectId: string; types: RecordingUnitType[] } | null = null;

/**
 * All configured Recording Unit types for a project, in one call (the same bundle the JSF panel
 * resolves per-type via EffectiveFormResolver). Cached per projectId for the lifetime of the tab —
 * types/forms don't change while a single overview panel is open.
 */
export async function fetchRecordingUnitTypes(projectId: string): Promise<RecordingUnitType[]> {
  if (cache && cache.projectId === projectId) {
    return cache.types;
  }
  const response = await apiFetch<ProjectRecordingUnitTypeListResponse>(
    `/projects/${projectId}/recording-unit-types`,
  );
  cache = { projectId, types: response.data };
  return response.data;
}

export async function fetchFormForType(projectId: string, typeConceptId: string): Promise<RecordingUnitType | null> {
  const types = await fetchRecordingUnitTypes(projectId);
  return types.find((t) => t.id === typeConceptId) ?? null;
}
