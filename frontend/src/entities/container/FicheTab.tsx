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
import { containerPanelLabel } from "./form";
import { getContainerEffectiveForm } from "./containerTypes";
import { patchContainerAnswers } from "./api";
import type { ContainerDetail } from "./types";

// The fiche, schema-driven off GET /api/v1/projects/{id}/container-types — renders every
// panel/row/field Container.DETAILS_FORM's layout defines, same as JSF's container "Détails" tab.
// Resolved for the container's OWN type, falling back to `_default` for an untyped container —
// same pattern as PhaseFicheTab/FindFicheTab (see getContainerEffectiveForm).
//
// EDIT MODEL: identical to the other fiches' — click-to-edit via FieldEditCell/CellEditOverlay,
// gated on WriteModeProvider AND `_permissions.canEdit`. Everything routes through `answers`
// (patchContainerAnswers): ContainerPatchRequest carries only `answers`.
//
// Deliberately absent, same reasons as the other fiches: no conditional field visibility, no
// per-column active/inactive toggle, no history footer, no identifier editing (server-generated).
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
      {formQuery.isLoading && <div>Chargement du formulaire…</div>}
      {formQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields &&
        panels.map((panel, panelIndex) => (
          <Panel
            key={panelIndex}
            header={containerPanelLabel(panel.name)}
            toggleable
            className={`sia-form-panel ${panel.className ?? ""}`.trim()}
          >
            {panel.rows.map((row, rowIndex) => (
              <div key={rowIndex} className="project-fiche-tab-row sia-grid">
                {row.columns.map((col, colIndex) => (
                  <ContainerFormField
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

function ContainerFormField({
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
  entity: ContainerDetail;
  canEdit: boolean;
  organizationId?: number;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
}) {
  // identifierField/actionUnitField are both hidden + readOnly in ContainerDetailsForm.build() —
  // shown (or, for the identifier, merely displayed) outside this grid instead, via the panel
  // header.
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
          entityType="container"
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
