import { apiFetch } from "./client";
import type { FieldResource } from "../form/schema";
import type { ResolvedConceptResource } from "./recordingUnit";

export interface RecordingUnitType {
  concept: ResolvedConceptResource | null;
  id: string;
  formBundle: { resourceType: "forms"; layoutJson: string };
  fields: Record<string, FieldResource>;
}

/** Same fields as RecordingUnitType minus `concept`/`id` — the default has no concept of its own. */
type RecordingUnitDefaultType = Omit<RecordingUnitType, "concept" | "id">;

interface ProjectRecordingUnitTypeListResponse {
  data: RecordingUnitType[];
  /** Config/form used when a type has none configured of its own — see TableFieldConfigService
   *  .DEFAULT_TYPE server-side ("_default"), the same fallback EffectiveFormResolver applies for JSF. */
  _default: RecordingUnitDefaultType;
}

const DEFAULT_TYPE_ID = "_default";

let cache: { projectId: string; types: RecordingUnitType[]; defaultType: RecordingUnitType } | null = null;

async function fetchRecordingUnitTypesBundle(
  projectId: string,
): Promise<{ types: RecordingUnitType[]; defaultType: RecordingUnitType }> {
  if (cache && cache.projectId === projectId) {
    return cache;
  }
  const response = await apiFetch<ProjectRecordingUnitTypeListResponse>(
    `/projects/${projectId}/recording-unit-types`,
  );
  const defaultType: RecordingUnitType = { concept: null, id: DEFAULT_TYPE_ID, ...response._default };
  cache = { projectId, types: response.data, defaultType };
  return cache;
}

/**
 * All configured Recording Unit types for a project, in one call (the same bundle the JSF panel
 * resolves per-type via EffectiveFormResolver). Cached per projectId for the lifetime of the tab —
 * types/forms don't change while a single overview panel is open.
 */
export async function fetchRecordingUnitTypes(projectId: string): Promise<RecordingUnitType[]> {
  return (await fetchRecordingUnitTypesBundle(projectId)).types;
}

/**
 * The form to use for `typeConceptId` — falls back to the project's "_default" form when the type has
 * none configured of its own (or the unit has no type at all: pass null), mirroring
 * TableFieldConfigService.DEFAULT_TYPE / EffectiveFormResolver on the JSF side. Never returns null.
 */
export async function fetchFormForType(projectId: string, typeConceptId: string | null): Promise<RecordingUnitType> {
  const { types, defaultType } = await fetchRecordingUnitTypesBundle(projectId);
  return (typeConceptId && types.find((t) => t.id === typeConceptId)) || defaultType;
}
