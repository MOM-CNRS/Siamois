import { useCallback, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { useCanEdit } from "../../panels/writeMode";
import { FormLayoutView } from "../../fields/FormLayoutView";
import type { AnswerInputBody } from "../../fields/types";
import { parseLayout } from "../project/form";
import { phasePanelLabel } from "./form";
import { getPhaseEffectiveForm } from "./phaseTypes";
import { patchPhaseAnswers } from "./api";
import type { PhaseDetail } from "./types";
import { FormSkeleton } from "../../components/DetailSkeleton";

// The fiche, schema-driven off GET /api/v1/projects/{id}/phase-types — renders every
// panel/row/field Phase.DETAILS_FORM's layout defines, same as JSF's phase "Détails" tab. The
// layout/field-catalog it renders is resolved for the phase's OWN type (mirrors
// FindFicheTab/RecordingUnitFicheTab's own per-type resolution — see getPhaseEffectiveForm),
// falling back to `_default` for an untyped phase.
//
// EDIT MODEL: identical to the other fiches' — no Modifier/Enregistrer/Annuler, fields edit on
// click via FieldEditCell/CellEditOverlay, gated on WriteModeProvider AND `_permissions.canEdit`.
// Everything routes through `answers` (patchPhaseAnswers): PhasePatchRequest carries only
// `answers`, so the save callback below just forwards CellEditOverlay's own answers map straight
// through.
//
// Deliberately absent, same reasons as the other fiches (no REST equivalent, not by oversight):
// - per-column active/inactive toggle: Phase's own type resource carries no `fieldConfigs`.
// - a history footer: no GET .../phases/{id}/history endpoint exists yet.
// - identifier editing: PhasePatchRequest has no flat alias for identifier (server-generated).

interface PhaseFicheTabProps {
  entity: PhaseDetail;
  onSaved: () => void;
}

export function PhaseFicheTab({ entity, onSaved }: PhaseFicheTabProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const formQuery = useQuery({
    queryKey: ["phase-effective-form", entity.projectId, entity.type?.id],
    queryFn: () => getPhaseEffectiveForm(entity.projectId as string, entity.type?.id ?? null),
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
    (id: string | number, answers: Record<string, AnswerInputBody>) => patchPhaseAnswers(id, answers),
    [],
  );

  return (
    <div className="phase-fiche-tab sia-fiche-tab">
      {entity.projectId == null && (
        <Message severity="warn" text="Projet inconnu : impossible de charger le formulaire" />
      )}
      {formQuery.isLoading && <FormSkeleton />}
      {formQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields && (
        <FormLayoutView
          entity={entity}
          entityType="phase"
          fields={fields}
          panels={panels}
          panelLabel={phasePanelLabel}
          canEdit={canEdit}
          organizationId={organizationId}
          onSave={save}
          onSaved={onSaved}
        />
      )}
    </div>
  );
}
