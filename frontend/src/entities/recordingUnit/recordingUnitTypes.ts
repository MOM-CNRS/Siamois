import { apiFetch } from "../../api/client";
import type { FieldResource } from "../../fields/types";
import type { ProjectTableColumnDefault } from "../project/projectTypes";

// GET /api/v1/projects/{id}/recording-unit-types — unlike getProjectTypes (organization-scoped),
// this catalog is PROJECT-scoped: a project's own effective RU form(s), plus (per plan) a root
// `fields` map that's the union of `_default.fields` and every configured type's own fields, and
// `_default.tableColumns` from RecordingUnitTableColumnDefaults (the same source
// RecordingUnitTableDefinitionFactory reads for the JSF table).
interface RecordingUnitTypesResponseBody {
  data: unknown[];
  _default: {
    formBundle: { resourceType: string; layoutJson: string } | null;
    tableColumns: ProjectTableColumnDefault[];
  };
  fields: Record<string, FieldResource>;
}

export interface RecordingUnitTypesResult {
  tableColumns: ProjectTableColumnDefault[];
  fields: Record<string, FieldResource>;
}

export async function getRecordingUnitTypes(projectId: string | number): Promise<RecordingUnitTypesResult> {
  const body = await apiFetch<RecordingUnitTypesResponseBody>(`/api/v1/projects/${projectId}/recording-unit-types`);
  return {
    tableColumns: body._default.tableColumns ?? [],
    fields: body.fields,
  };
}
