import { getFieldRenderer } from "../form/fieldRegistry";
import type { useEntityForm } from "../form/useEntityForm";
import type { FormLayout } from "../form/schema";
import type { RecordingUnitType } from "../api/recordingUnitType";

export interface DetailsTabProps {
  layout: FormLayout;
  formType: RecordingUnitType;
  projectId: string;
  form: ReturnType<typeof useEntityForm>;
}

/**
 * Renders the "Détails" tab from the server-composed layout, joining each column to its FieldResource
 * via the type's `fields` map (not the RU's `answers` map — a column can reference a field with no
 * answer yet, so the type bundle's fields map is the authoritative source, matching how the JSF form
 * resolves field metadata independently of whether it currently has a value).
 *
 * NOTE: `className` grid classes (`ui-g-12 ui-md-6 ...`) are PrimeFlex-style and currently have no
 * effect — PrimeFlex isn't in this app's dependencies yet, so columns stack full-width instead of
 * following the JSF grid. Functionally complete, visually not yet at parity; add `primeflex` before
 * this ships to real users if the grid layout itself matters (not just field presence/order).
 */
export function DetailsTab({ layout, formType, projectId, form }: DetailsTabProps) {
  return (
    <div className="ru-details-tab">
      {layout.map((panel, panelIndex) => (
        <fieldset key={panelIndex} className="ru-form-panel">
          {panel.name && <legend>{panel.name}</legend>}
          {panel.rows.map((row, rowIndex) => (
            <div key={rowIndex} className="ru-form-row" style={{ display: "flex", flexWrap: "wrap", gap: "1rem" }}>
              {row.columns.map((col, colIndex) => {
                if (col.className?.includes("d-none")) return null;
                if (col.fieldId == null) return null;
                const field = formType.fields[String(col.fieldId)];
                if (!field) return null;

                const Renderer = getFieldRenderer(field.answerType);
                const enabled = form.isEnabled(col.fieldId) && !col.isReadOnly;
                const status = form.fieldStatus(col.fieldId);

                return (
                  <div key={colIndex} className="ru-form-col" style={{ flex: "1 1 240px", minWidth: 0 }}>
                    <label style={{ display: "block", fontSize: "0.85rem", marginBottom: "0.25rem" }}>
                      {field.label}
                      {col.isRequired && <span style={{ color: "var(--red-500, #e24c4c)" }}> *</span>}
                    </label>
                    <Renderer
                      field={field}
                      value={form.getValue(col.fieldId)}
                      onChange={(value) => form.setValue(col.fieldId!, value)}
                      disabled={!enabled}
                      projectId={projectId}
                    />
                    {status === "saving" && <small className="ru-field-status">Enregistrement…</small>}
                    {status === "error" && (
                      <small className="ru-field-status ru-field-status-error">Échec de l'enregistrement</small>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
        </fieldset>
      ))}
    </div>
  );
}
