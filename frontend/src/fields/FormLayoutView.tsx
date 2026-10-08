import { useMemo } from "react";
import { Panel } from "primereact/panel";
import { evaluateForm, type FieldState } from "../rules";
import { panelLabel, toGridClass, type FormLayoutCol, type FormLayoutPanel } from "./layout";
import { FieldEditCell } from "./FieldEditCell";
import { FieldLabel } from "./FieldLabel";
import { isTypeField } from "./typeField";
import { resolveValueBinding, type AnswerInputBody, type FieldResource } from "./types";
import { valueOfField } from "./values";
import { t } from "../i18n";

/**
 * The fiche's form, for every entity type: panels → rows → columns of FieldLabel + FieldEditCell,
 * with the layout's conditional rules applied (rules/evaluate.ts). The rules are evaluated once per
 * render of the whole form against the entity as served — each field saves itself and the fiche
 * refetches, so the states follow the saved values without any form state of their own.
 *
 * <p>Each entity's FicheTab keeps what is really its own: how it loads its form (effective form per
 * type, or bundled in the detail), how it saves (its PATCH, Project's flat aliases), and which
 * fields it doesn't show (`isFieldShown`).</p>
 */
export interface FormLayoutViewProps<TEntity extends { id?: string | number }> {
  entity: TEntity;
  entityType: string;
  fields: Record<string, FieldResource>;
  panels: FormLayoutPanel[];
  canEdit: boolean;
  organizationId?: number;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>, value?: unknown) => Promise<unknown>;
  onSaved: () => void;
  /** Extra per-entity filter on top of `hidden` columns; `stored` is the answer as served. */
  isFieldShown?: (field: FieldResource, stored: unknown) => boolean;
}

export function FormLayoutView<TEntity extends { id?: string | number }>({
  entity,
  entityType,
  fields,
  panels,
  canEdit,
  organizationId,
  onSave,
  onSaved,
  isFieldShown,
}: FormLayoutViewProps<TEntity>) {
  const states = useMemo(() => {
    const columns = panels.flatMap((p) => p.rows.flatMap((r) => r.columns));
    return evaluateForm(columns, (id) => valueOfField(entity, fields, id));
  }, [panels, fields, entity]);

  return (
    <>
      {panels.map((panel, panelIndex) => (
        <Panel
          key={panelIndex}
          header={panelLabel(panel.name)}
          toggleable
          className={`sia-form-panel ${panel.className ?? ""}`.trim()}
        >
          {panel.rows.map((row, rowIndex) => (
            <div key={rowIndex} className="project-fiche-tab-row sia-grid">
              {row.columns.map((col, colIndex) => (
                <FormLayoutField
                  key={colIndex}
                  col={col}
                  fields={fields}
                  state={col.fieldId != null ? states.get(String(col.fieldId)) : undefined}
                  entity={entity}
                  entityType={entityType}
                  canEdit={canEdit}
                  organizationId={organizationId}
                  isFieldShown={isFieldShown}
                  onSave={onSave}
                  onSaved={onSaved}
                />
              ))}
            </div>
          ))}
          {panel.rows.length === 0 && <i>{t("field.noField")}</i>}
        </Panel>
      ))}
    </>
  );
}

function FormLayoutField<TEntity extends { id?: string | number }>({
  col,
  fields,
  state,
  entity,
  entityType,
  canEdit,
  organizationId,
  isFieldShown,
  onSave,
  onSaved,
}: {
  col: FormLayoutCol;
  fields: Record<string, FieldResource>;
  state?: FieldState;
  entity: TEntity;
  entityType: string;
  canEdit: boolean;
  organizationId?: number;
  isFieldShown?: (field: FieldResource, stored: unknown) => boolean;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>, value?: unknown) => Promise<unknown>;
  onSaved: () => void;
}) {
  // Hidden columns (the RU's project and identifier) are edited — or merely shown — outside this
  // grid, in the panel header.
  if (col.fieldId == null || col.hidden) return null;

  const fieldId = String(col.fieldId);
  const field = fields[fieldId];
  if (!field) return null;
  // The type is edited from the fiche's header, not as one of the form's fields.
  if (isTypeField(field)) return null;

  const stored = resolveValueBinding(field).readRaw(entity);
  if (isFieldShown && !isFieldShown(field, stored)) return null;

  const required = state?.required ?? col.isRequired;
  return (
    <div
      className={`project-fiche-tab-col ${toGridClass(col.width)}${state?.enabled === false ? " sia-field--disabled" : ""}`}
      data-field-id={fieldId}
    >
      <div className="field-value-group">
        <FieldLabel field={field} required={required} />
        <FieldEditCell
          entityType={entityType}
          key={JSON.stringify(stored ?? null)}
          field={field}
          row={entity}
          stored={stored}
          readOnly={!canEdit || col.isReadOnly}
          required={required}
          fieldState={state}
          fieldLabelOf={(id) => fields[id]?.label ?? id}
          organizationId={organizationId}
          onSave={onSave}
          onSaved={onSaved}
        />
      </div>
    </div>
  );
}
