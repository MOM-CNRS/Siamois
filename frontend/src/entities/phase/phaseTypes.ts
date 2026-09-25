import { apiFetch } from "../../api/client";
import type { FieldResource } from "../../fields/types";

// GET /api/v1/projects/{id}/phase-types — project-scoped, mirrors
// entities/find/findTypes.ts's getFindEffectiveForm: no root `fields` union and no
// `_default.tableColumns` (Phase has no dynamic column catalog yet, see columns.tsx) — only what
// the fiche needs, the layout+fields for THIS phase's own type, falling back to `_default` for an
// untyped phase.
interface PhaseTypeBody {
  id: string;
  formBundle: { resourceType: string; layoutJson: string } | null;
  fields: Record<string, FieldResource>;
}

interface PhaseTypesResponseBody {
  data: PhaseTypeBody[];
  _default: {
    formBundle: { resourceType: string; layoutJson: string } | null;
    fields: Record<string, FieldResource>;
  };
}

export interface PhaseEffectiveForm {
  layoutJson: string;
  fields: Record<string, FieldResource>;
}

export async function getPhaseEffectiveForm(
  projectId: string | number,
  typeId: string | null | undefined,
): Promise<PhaseEffectiveForm> {
  const body = await apiFetch<PhaseTypesResponseBody>(`/api/v1/projects/${projectId}/phase-types`);
  const match = typeId != null ? body.data.find((t) => t.id === typeId) : undefined;
  const effective = match ?? body._default;
  return {
    layoutJson: effective.formBundle?.layoutJson ?? "",
    fields: effective.fields ?? {},
  };
}
