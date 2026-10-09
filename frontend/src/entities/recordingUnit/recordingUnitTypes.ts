import { fetchTypesCatalog } from "../typeCatalog";
import type { FieldResource } from "../../fields/types";
import type { ProjectTableColumnDefault } from "../common";

// The recording unit list's column catalog: the union of every type's fields (root `fields`) and
// the default columns (RecordingUnitTableColumnDefaults, the JSF table's own source).
export interface RecordingUnitTypesResult {
  tableColumns: ProjectTableColumnDefault[];
  fields: Record<string, FieldResource>;
}

// GET /api/v1/projects/{id}/recording-unit-types.
export async function getRecordingUnitTypes(projectId: string | number): Promise<RecordingUnitTypesResult> {
  const body = await fetchTypesCatalog(`/api/v1/projects/${projectId}/recording-unit-types`);
  return { tableColumns: body.tableColumns ?? [], fields: body.fields ?? {} };
}

// GET /api/v1/organizations/{id}/recording-unit-types — the organization-wide list's catalog: the
// system fields plus every additional field active in any of the organization's projects, and the
// same default columns as a project's catalog. No per-type entries (`data` is always empty).
export async function getOrganizationRecordingUnitTypes(organizationId: number): Promise<RecordingUnitTypesResult> {
  const body = await fetchTypesCatalog(`/api/v1/organizations/${organizationId}/recording-unit-types`);
  return { tableColumns: body.tableColumns ?? [], fields: body.fields ?? {} };
}
