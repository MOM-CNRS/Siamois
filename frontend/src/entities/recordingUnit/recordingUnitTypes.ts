import { apiFetch } from "../../api/client";
import type { FieldResource } from "../../fields/types";
import type { ProjectTableColumnDefault } from "../project/projectTypes";

// GET /api/v1/projects/{id}/recording-unit-types — unlike getProjectTypes (organization-scoped),
// this catalog is PROJECT-scoped: a project's own effective RU form(s), plus (per plan) a root
// `fields` map that's the union of `_default.fields` and every configured type's own fields, and
// `_default.tableColumns` from RecordingUnitTableColumnDefaults (the same source
// RecordingUnitTableDefinitionFactory reads for the JSF table).
//
// One entry per configured RecordingUnitType (`data`), each carrying its OWN `formBundle`/`fields`
// — RU's form varies by type (RecordingUnitDetailsForm is the same layout for every type today,
// but the server already resolves it per-type via EffectiveFormResolver, and a future per-type
// override should just work once one is configured). `_default` is the fallback used when the RU
// has no type at all, mirroring RecordingUnitOpenApiService#resolveMobileDetail's own
// `dto.getType() != null ? dto.getType().getId() : null` branch.
interface RecordingUnitTypeBody {
  id: string;
  formBundle: { resourceType: string; layoutJson: string } | null;
  fields: Record<string, FieldResource>;
}

interface RecordingUnitTypesResponseBody {
  data: RecordingUnitTypeBody[];
  _default: {
    formBundle: { resourceType: string; layoutJson: string } | null;
    tableColumns: ProjectTableColumnDefault[];
    fields: Record<string, FieldResource>;
  };
  fields: Record<string, FieldResource>;
}

export interface RecordingUnitTypesResult {
  tableColumns: ProjectTableColumnDefault[];
  fields: Record<string, FieldResource>;
}

export async function getRecordingUnitTypes(projectId: string | number): Promise<RecordingUnitTypesResult> {
  const body = await getRecordingUnitTypesRaw(projectId);
  return {
    tableColumns: body._default.tableColumns ?? [],
    fields: body.fields,
  };
}

// GET /api/v1/organizations/{id}/recording-unit-types — the organization-wide list's catalog: the
// system fields plus every additional field active in any of the organization's projects, and the
// same default columns as a project's catalog. No per-type entries (`data` is always empty).
export async function getOrganizationRecordingUnitTypes(organizationId: number): Promise<RecordingUnitTypesResult> {
  const body = await apiFetch<{ fields: Record<string, FieldResource>; _default: { tableColumns?: ProjectTableColumnDefault[] } }>(
    `/api/v1/organizations/${organizationId}/recording-unit-types`,
  );
  return { tableColumns: body._default.tableColumns ?? [], fields: body.fields };
}

// The fiche's own need: the layout AND field catalog for THIS recording unit's type specifically
// (not the union across every type, which the list's column toggler uses instead) — mirrors
// FicheTab's own `join columns to the TYPE's fields map, not the RU's answers map` rule
// (a column can reference a field the RU has no answer for yet).
export interface RecordingUnitEffectiveForm {
  layoutJson: string;
  fields: Record<string, FieldResource>;
}

export async function getRecordingUnitEffectiveForm(
  projectId: string | number,
  typeId: string | null | undefined,
): Promise<RecordingUnitEffectiveForm> {
  const body = await getRecordingUnitTypesRaw(projectId);
  const match = typeId != null ? body.data.find((t) => t.id === typeId) : undefined;
  const effective = match ?? body._default;
  return {
    layoutJson: effective.formBundle?.layoutJson ?? "",
    fields: effective.fields ?? {},
  };
}

async function getRecordingUnitTypesRaw(projectId: string | number): Promise<RecordingUnitTypesResponseBody> {
  return apiFetch<RecordingUnitTypesResponseBody>(`/api/v1/projects/${projectId}/recording-unit-types`);
}
