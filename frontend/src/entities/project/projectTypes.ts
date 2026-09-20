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

interface ProjectTypesResponseBody {
  data: unknown[];
  _default: {
    form: { resourceType: string; layoutJson: string };
    fieldConfigs: ProjectFieldConfigBody[];
  };
  fields: Record<string, FieldResource>;
}

export interface ProjectTypesResult {
  layoutJson: string;
  fieldConfigs: ProjectFieldConfigBody[];
  fields: Record<string, FieldResource>;
}

export async function getProjectTypes(organizationId: string | number): Promise<ProjectTypesResult> {
  const body = await apiFetch<ProjectTypesResponseBody>(`/api/v1/organizations/${organizationId}/project-types`);
  return {
    layoutJson: body._default.form.layoutJson,
    fieldConfigs: body._default.fieldConfigs,
    fields: body.fields,
  };
}
