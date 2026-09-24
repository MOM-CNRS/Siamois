import { useCallback, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { Panel } from "primereact/panel";
import { useCanEdit } from "../../panels/writeMode";
import { FieldEditCell } from "../../fields/FieldEditCell";
import { FieldLabel } from "../../fields/FieldLabel";
import { resolveValueBinding } from "../../fields/types";
import type { AnswerInputBody, FieldResource } from "../../fields/types";
import { parseLayout, toPrimeFlexClass, type FormLayoutCol } from "../project/form";
import { phasePanelLabel } from "./form";
import { getPhaseEffectiveForm } from "./phaseTypes";
import { patchPhaseAnswers } from "./api";
import type { PhaseDetail } from "./types";

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
// - conditional field visibility (no rules engine ported in this architecture yet).
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
      {formQuery.isLoading && <div>Chargement du formulaire…</div>}
      {formQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields &&
        panels.map((panel, panelIndex) => (
          <Panel
            key={panelIndex}
            header={phasePanelLabel(panel.name)}
            toggleable
            className={`sia-form-panel ${panel.className ?? ""}`.trim()}
          >
            {panel.rows.map((row, rowIndex) => (
              <div key={rowIndex} className="project-fiche-tab-row grid">
                {row.columns.map((col, colIndex) => (
                  <PhaseFormField
                    key={colIndex}
                    col={col}
                    fields={fields}
                    entity={entity}
                    canEdit={canEdit}
                    organizationId={organizationId}
                    onSave={save}
                    onSaved={onSaved}
                  />
                ))}
              </div>
            ))}
            {panel.rows.length === 0 && <i>Aucun champ</i>}
          </Panel>
        ))}
    </div>
  );
}

function PhaseFormField({
  col,
  fields,
  entity,
  canEdit,
  organizationId,
  onSave,
  onSaved,
}: {
  col: FormLayoutCol;
  fields: Record<string, FieldResource>;
  entity: PhaseDetail;
  canEdit: boolean;
  organizationId?: number;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
}) {
  // identifierField/actionUnitField are both hidden + readOnly in PhaseDetailsForm.build() — same
  // reason as Project's/RecordingUnit's own hidden identifier/parent columns: shown (or, for the
  // identifier, merely displayed) outside this grid instead, via the panel header.
  if (col.fieldId == null || col.hidden) return null;

  const fieldId = String(col.fieldId);
  const field = fields[fieldId];
  if (!field) return null;

  const stored = resolveValueBinding(field).read(entity);

  return (
    <div className={`project-fiche-tab-col ${toPrimeFlexClass(col.width)}`} data-field-id={fieldId}>
      <div className="field-value-group">
        <FieldLabel field={field} required={col.isRequired} />
        <FieldEditCell
          entityType="phase"
          key={JSON.stringify(stored ?? null)}
          field={field}
          row={entity}
          stored={stored}
          readOnly={!canEdit || col.isReadOnly}
          required={col.isRequired}
          organizationId={organizationId}
          onSave={onSave}
          onSaved={onSaved}
        />
      </div>
    </div>
  );
}
