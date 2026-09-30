import { useCallback, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { useCanEdit } from "../../panels/writeMode";
import { FormLayoutView } from "../../fields/FormLayoutView";
import type { AnswerInputBody } from "../../fields/types";
import { parseLayout } from "../project/form";
import { findPanelLabel } from "./form";
import { getFindEffectiveForm } from "./findTypes";
import { patchFindAnswers } from "./api";
import type { FindDetail } from "./types";
import { FormSkeleton } from "../../components/DetailSkeleton";

// The fiche, schema-driven off GET /api/v1/projects/{id}/find-types — renders every
// panel/row/field Specimen.DETAILS_FORM's layout defines, same as JSF's mobilier "Détails" tab.
// The layout/field-catalog it renders is resolved for the mobilier's OWN type
// (RecordingUnitOpenApiService#buildFindMobilierForm's own Specimen.DETAILS_FORM base, matched
// against the project's find-types catalog here client-side — see getFindEffectiveForm),
// falling back to `_default` for an untyped mobilier.
//
// EDIT MODEL: identical to RecordingUnitFicheTab's — no Modifier/Enregistrer/Annuler, fields edit
// on click via FieldEditCell/CellEditOverlay, gated on WriteModeProvider AND
// `_permissions.canEdit`. Everything routes through `answers` (patchFindAnswers): FindPatchRequest
// carries only `answers` (plus a legacy `fieldAnswers` alias this client never uses), so the save
// callback below just forwards CellEditOverlay's own answers map straight through.
//
// Deliberately absent, same reasons as the other fiches (no REST equivalent, not by oversight):
// - per-column active/inactive toggle: Find's own type resource carries no `fieldConfigs`.
// - a history footer: no GET .../finds/{id}/history endpoint exists yet.
// - identifier editing: FindPatchRequest has no flat alias for fullIdentifier.

interface FindFicheTabProps {
  entity: FindDetail;
  onSaved: () => void;
}

export function FindFicheTab({ entity, onSaved }: FindFicheTabProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const formQuery = useQuery({
    queryKey: ["find-effective-form", entity.projectId, entity.type?.id],
    queryFn: () => getFindEffectiveForm(entity.projectId as string, entity.type?.id ?? null),
    enabled: entity.projectId != null,
  });

  const fields = formQuery.data?.fields;
  const panels = useMemo(
    () => (formQuery.data ? parseLayout(formQuery.data.layoutJson) : []),
    [formQuery.data],
  );

  // No onSaved() call here: CellEditOverlay calls its own onSaved prop itself once onSave
  // resolves.
  const save = useCallback(
    (id: string | number, answers: Record<string, AnswerInputBody>) => patchFindAnswers(id, answers),
    [],
  );

  return (
    <div className="find-fiche-tab sia-fiche-tab">
      {entity.projectId == null && (
        <Message severity="warn" text="Projet inconnu : impossible de charger le formulaire" />
      )}
      {formQuery.isLoading && <FormSkeleton />}
      {formQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields && (
        <FormLayoutView
          entity={entity}
          entityType="find"
          fields={fields}
          panels={panels}
          panelLabel={findPanelLabel}
          canEdit={canEdit}
          organizationId={organizationId}
          onSave={save}
          onSaved={onSaved}
        />
      )}
    </div>
  );
}
