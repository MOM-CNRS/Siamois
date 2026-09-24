import { apiFetch } from "../../api/client";
import type { FieldResource } from "../../fields/types";

// GET /api/v1/projects/{id}/container-types — project-scoped, mirrors
// entities/phase/phaseTypes.ts's getPhaseEffectiveForm: no root `fields` union and no
// `_default.tableColumns` (Container has no dynamic column catalog yet, see columns.tsx) — only
// what the fiche needs, the layout+fields for THIS container's own type, falling back to
// `_default` for an untyped container.
interface ContainerTypeBody {
  id: string;
  formBundle: { resourceType: string; layoutJson: string } | null;
  fields: Record<string, FieldResource>;
}

interface ContainerTypesResponseBody {
  data: ContainerTypeBody[];
  _default: {
    formBundle: { resourceType: string; layoutJson: string } | null;
    fields: Record<string, FieldResource>;
  };
}

export interface ContainerEffectiveForm {
  layoutJson: string;
  fields: Record<string, FieldResource>;
}

export async function getContainerEffectiveForm(
  projectId: string | number,
  typeId: string | null | undefined,
): Promise<ContainerEffectiveForm> {
  const body = await apiFetch<ContainerTypesResponseBody>(`/api/v1/projects/${projectId}/container-types`);
  const match = typeId != null ? body.data.find((t) => t.id === typeId) : undefined;
  const effective = match ?? body._default;
  return {
    layoutJson: effective.formBundle?.layoutJson ?? "",
    fields: effective.fields ?? {},
  };
}
