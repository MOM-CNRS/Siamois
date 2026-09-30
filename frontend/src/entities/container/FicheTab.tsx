import { useCallback, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { useCanEdit } from "../../panels/writeMode";
import { FormLayoutView } from "../../fields/FormLayoutView";
import type { AnswerInputBody } from "../../fields/types";
import { parseLayout } from "../project/form";
import { containerPanelLabel } from "./form";
import { getContainerEffectiveForm } from "./containerTypes";
import { patchContainerAnswers } from "./api";
import type { ContainerDetail } from "./types";
import { FormSkeleton } from "../../components/DetailSkeleton";

// The fiche, schema-driven off GET /api/v1/projects/{id}/container-types — renders every
// panel/row/field Container.DETAILS_FORM's layout defines, same as JSF's container "Détails" tab.
// Resolved for the container's OWN type, falling back to `_default` for an untyped container —
// same pattern as PhaseFicheTab/FindFicheTab (see getContainerEffectiveForm).
//
// EDIT MODEL: identical to the other fiches' — click-to-edit via FieldEditCell/CellEditOverlay,
// gated on WriteModeProvider AND `_permissions.canEdit`. Everything routes through `answers`
// (patchContainerAnswers): ContainerPatchRequest carries only `answers`.
//
// Deliberately absent, same reasons as the other fiches: no per-column active/inactive toggle, no
// history footer, no identifier editing (server-generated).
interface ContainerFicheTabProps {
  entity: ContainerDetail;
  onSaved: () => void;
}

export function ContainerFicheTab({ entity, onSaved }: ContainerFicheTabProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const formQuery = useQuery({
    queryKey: ["container-effective-form", entity.projectId, entity.type?.id],
    queryFn: () => getContainerEffectiveForm(entity.projectId as string, entity.type?.id ?? null),
    enabled: entity.projectId != null,
  });

  const fields = formQuery.data?.fields;
  const panels = useMemo(
    () => (formQuery.data ? parseLayout(formQuery.data.layoutJson) : []),
    [formQuery.data],
  );

  const save = useCallback(
    (id: string | number, answers: Record<string, AnswerInputBody>) => patchContainerAnswers(id, answers),
    [],
  );

  return (
    <div className="container-fiche-tab sia-fiche-tab">
      {entity.projectId == null && (
        <Message severity="warn" text="Projet inconnu : impossible de charger le formulaire" />
      )}
      {formQuery.isLoading && <FormSkeleton />}
      {formQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields && (
        <FormLayoutView
          entity={entity}
          entityType="container"
          fields={fields}
          panels={panels}
          panelLabel={containerPanelLabel}
          canEdit={canEdit}
          organizationId={organizationId}
          onSave={save}
          onSaved={onSaved}
        />
      )}
    </div>
  );
}
