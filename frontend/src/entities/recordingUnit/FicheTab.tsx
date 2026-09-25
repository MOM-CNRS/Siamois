import { useCallback, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { Panel } from "primereact/panel";
import { useCanEdit } from "../../panels/writeMode";
import { FieldEditCell } from "../../fields/FieldEditCell";
import { FieldLabel } from "../../fields/FieldLabel";
import { resolveValueBinding } from "../../fields/types";
import type { AnswerInputBody, FieldResource } from "../../fields/types";
import { parseLayout, toGridClass, type FormLayoutCol } from "../project/form";
import { recordingUnitPanelLabel } from "./form";
import { getRecordingUnitEffectiveForm } from "./recordingUnitTypes";
import { patchRecordingUnitAnswers } from "./api";
import type { RecordingUnitDetail } from "./types";

// The fiche, schema-driven off GET /api/v1/projects/{id}/recording-unit-types — renders every
// panel/row/field RecordingUnit.DETAILS_FORM's layout defines (4 panels: general, chronology,
// measurements, dates), same as JSF's "Détails" tab. The layout/field-catalog it renders is
// resolved for the RU's OWN type (RecordingUnitOpenApiService#resolveMobileDetail's own
// `dto.getType() != null ? dto.getType().getId() : null` branch — see
// getRecordingUnitEffectiveForm), falling back to `_default` for an untyped RU.
//
// EDIT MODEL: identical to ProjectFicheTab's — no Modifier/Enregistrer/Annuler, fields edit on
// click exactly like the list's own table cells (FieldEditCell opens CellEditOverlay, the same
// component the list uses), gated on WriteModeProvider AND `_permissions.canEdit`. Everything
// routes through `answers` (patchRecordingUnitAnswers): unlike Project, RU has no flat-alias
// fields at all (its PatchRequest carries only `answers`/`geom`/`expectedRevision` — no
// `identifier`/`typeId` aliases the way ProjectPatchRequest has), so the save callback below just
// forwards CellEditOverlay's own answers map straight through, no patch-shaping needed.
//
// Deliberately absent, same reasons as ProjectFicheTab (no REST equivalent, not by oversight):
// - conditional field visibility (erosion fields' `enabledWhen`, interpretation's `dependsOn` —
//   RecordingUnitDetailsForm.java declares both, but neither is modeled client-side yet; the
//   fields render unconditionally rather than silently disappearing, which is the safer gap to
//   have — a visible-but-currently-irrelevant field beats a hidden one with no way to reach it).
// - the per-column active/inactive toggle ProjectFicheTab's `inactiveFieldIds` applies: RU's own
//   type/default resource (RecordingUnitType/RecordingUnitDefaultType) carries no `fieldConfigs`
//   at all, unlike Project's — every field the layout places is always shown.
// - a history footer (ProjectFicheTab's FicheFooter): no `GET .../recording-units/{id}/history`
//   endpoint exists yet.
// - identifier editing: the header's identifier chip is read-only (see DetailHeader.tsx) — no
//   endpoint accepts a `fullIdentifier` write for a recording unit (RecordingUnitPatchRequest has
//   no flat alias for it, unlike ProjectPatchRequest.identifier).

interface RecordingUnitFicheTabProps {
  entity: RecordingUnitDetail;
  onSaved: () => void;
}

export function RecordingUnitFicheTab({ entity, onSaved }: RecordingUnitFicheTabProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const formQuery = useQuery({
    queryKey: ["recording-unit-effective-form", entity.projectId, entity.type?.id],
    queryFn: () => getRecordingUnitEffectiveForm(entity.projectId as string, entity.type?.id ?? null),
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
    (id: string | number, answers: Record<string, AnswerInputBody>) => patchRecordingUnitAnswers(id, answers),
    [],
  );

  return (
    <div className="recording-unit-fiche-tab sia-fiche-tab">
      {entity.projectId == null && (
        <Message severity="warn" text="Projet inconnu : impossible de charger le formulaire" />
      )}
      {formQuery.isLoading && <div>Chargement du formulaire…</div>}
      {formQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields &&
        panels.map((panel, panelIndex) => (
          <Panel
            key={panelIndex}
            header={recordingUnitPanelLabel(panel.name)}
            toggleable
            className={`sia-form-panel ${panel.className ?? ""}`.trim()}
          >
            {panel.rows.map((row, rowIndex) => (
              <div key={rowIndex} className="project-fiche-tab-row sia-grid">
                {row.columns.map((col, colIndex) => (
                  <RecordingUnitFormField
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

function RecordingUnitFormField({
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
  entity: RecordingUnitDetail;
  canEdit: boolean;
  organizationId?: number;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
}) {
  // ACTION_UNIT_FIELD and FULL_IDENTIFIER_FIELD are both hidden + readOnly in
  // RecordingUnitDetailsForm.generalPanel() — same reason as Project's own identifier column:
  // edited (or, for the identifier, merely displayed) outside this grid, via the panel header.
  if (col.fieldId == null || col.hidden) return null;

  const fieldId = String(col.fieldId);
  const field = fields[fieldId];
  if (!field) return null;

  const stored = resolveValueBinding(field).read(entity);

  return (
    <div className={`project-fiche-tab-col ${toGridClass(col.width)}`} data-field-id={fieldId}>
      <div className="field-value-group">
        <FieldLabel field={field} required={col.isRequired} />
        <FieldEditCell
          entityType="recordingUnit"
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
