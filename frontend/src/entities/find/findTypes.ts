import { apiFetch } from "../../api/client";
import type { FieldResource } from "../../fields/types";

// GET /api/v1/projects/{id}/find-types — project-scoped, mirrors
// entities/recordingUnit/recordingUnitTypes.ts's getRecordingUnitEffectiveForm, but against
// ProjectFindTypeListResponse's own shape: no root `fields` union and no `_default.tableColumns`
// (Find has no dynamic column catalog yet, see columns.tsx) — only what the fiche needs, the
// layout+fields for THIS mobilier's own type, falling back to `_default` for an untyped mobilier.
interface FindTypeBody {
  id: string;
  formBundle: { resourceType: string; layoutJson: string } | null;
  fields: Record<string, FieldResource>;
}

interface FindTypesResponseBody {
  data: FindTypeBody[];
  _default: {
    formBundle: { resourceType: string; layoutJson: string } | null;
    fields: Record<string, FieldResource>;
  };
}

export interface FindEffectiveForm {
  layoutJson: string;
  fields: Record<string, FieldResource>;
}

export async function getFindEffectiveForm(
  projectId: string | number,
  typeId: string | null | undefined,
): Promise<FindEffectiveForm> {
  const body = await apiFetch<FindTypesResponseBody>(`/api/v1/projects/${projectId}/find-types`);
  const match = typeId != null ? body.data.find((t) => t.id === typeId) : undefined;
  const effective = match ?? body._default;
  return {
    layoutJson: effective.formBundle?.layoutJson ?? "",
    fields: effective.fields ?? {},
  };
}
