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
import { findPanelLabel } from "./form";
import { getFindEffectiveForm } from "./findTypes";
import { patchFindAnswers } from "./api";
import type { FindDetail } from "./types";

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
// - conditional field visibility (no rules engine ported in this architecture yet).
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
      {formQuery.isLoading && <div>Chargement du formulaire…</div>}
      {formQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields &&
        panels.map((panel, panelIndex) => (
          <Panel
            key={panelIndex}
            header={findPanelLabel(panel.name)}
            toggleable
            className={`sia-form-panel ${panel.className ?? ""}`.trim()}
          >
            {panel.rows.map((row, rowIndex) => (
              <div key={rowIndex} className="project-fiche-tab-row grid">
                {row.columns.map((col, colIndex) => (
                  <FindFormField
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

function FindFormField({
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
  entity: FindDetail;
  canEdit: boolean;
  organizationId?: number;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
}) {
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
