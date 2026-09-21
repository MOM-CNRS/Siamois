import { apiFetch } from "../../api/client";
import type { FieldResource } from "../../fields/types";

// GET /api/v1/organizations/{id}/project-types (plan §5/§6/§8 phase 3+6) — replaces the removed
// GET /api/v1/projects/form. `data` is always `[]` this phase (Project has no configurable types
// yet); the fiche only ever consumes `_default`.
interface ProjectFieldConfigBody {
  field: string;
  active: boolean;
  institutionLocked: boolean;
}

// Mirrors fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource — the single
// source of truth for list-column visibility/order, shared with the JSF table
// (ActionUnitTableDefinitionFactory reads the same ActionUnitTableColumnDefaults). Does not cover
// the structural columns (identifier chip, name, recording-unit count): those aren't toggleable
// and aren't part of the field catalog.
export interface ProjectTableColumnDefault {
  columnId: string;
  fieldId: string;
  visible: boolean;
  order: number;
}

interface ProjectTypesResponseBody {
  data: unknown[];
  _default: {
    form: { resourceType: string; layoutJson: string };
    fieldConfigs: ProjectFieldConfigBody[];
    tableColumns: ProjectTableColumnDefault[];
  };
  fields: Record<string, FieldResource>;
}

export interface ProjectTypesResult {
  layoutJson: string;
  fieldConfigs: ProjectFieldConfigBody[];
  tableColumns: ProjectTableColumnDefault[];
  fields: Record<string, FieldResource>;
}

export async function getProjectTypes(organizationId: string | number): Promise<ProjectTypesResult> {
  const body = await apiFetch<ProjectTypesResponseBody>(`/api/v1/organizations/${organizationId}/project-types`);
  return {
    layoutJson: body._default.form.layoutJson,
    fieldConfigs: body._default.fieldConfigs,
    tableColumns: body._default.tableColumns ?? [],
    fields: body.fields,
  };
}
