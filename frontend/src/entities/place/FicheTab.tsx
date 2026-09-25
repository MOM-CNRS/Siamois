import { useMemo } from "react";
import { Message } from "primereact/message";
import { Panel } from "primereact/panel";
import { useCanEdit } from "../../panels/writeMode";
import { FieldEditCell } from "../../fields/FieldEditCell";
import { FieldLabel } from "../../fields/FieldLabel";
import { resolveValueBinding } from "../../fields/types";
import type { AnswerInputBody, FieldResource } from "../../fields/types";
import { parseLayout, toGridClass, type FormLayoutCol } from "../project/form";
import { placePanelLabel } from "./form";
import { patchPlaceAnswers } from "./api";
import type { PlaceDetail } from "./types";

// The fiche for SpatialUnit.DETAILS_FORM — unlike Phase/Container/RecordingUnit/Find, Place has
// no per-type catalog to resolve (SpatialUnit is absent from ConfigurableTable): the layout and
// field catalog already ride along on the detail response itself (entity.formBundle/entity.fields
// — see PlaceOpenApiService#getPlaceById), so there is no separate "effective form" query here,
// unlike PhaseFicheTab/ContainerFicheTab.
//
// EDIT MODEL: identical to the other fiches' — no Modifier/Enregistrer/Annuler, fields edit on
// click via FieldEditCell/CellEditOverlay, gated on WriteModeProvider AND `_permissions.canEdit`.
// `patchPlaceAnswers` is an adapter (see api.ts's own doc): PlacePatchRequest has no generic
// `answers` map, so it translates the field-id-keyed answers this fiche produces into that flat
// shape.
//
// The address field (CustomFieldSelectOneAddress, valueBinding "address") is deliberately
// skipped, not rendered: FullAddress is a composite object with no simple editor in this
// architecture yet, and buildPlaceAnswers never puts an entry for it in `answers`, so
// resolveValueBinding would otherwise read a raw FullAddress object into a text/concept renderer
// that can't represent it. A known, documented gap (see PlaceOpenApiService#getPlaceById's own
// javadoc), not an oversight.
interface PlaceFicheTabProps {
  entity: PlaceDetail;
  onSaved: () => void;
}

export function PlaceFicheTab({ entity, onSaved }: PlaceFicheTabProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const fields = entity.fields;
  const panels = useMemo(
    () => (entity.formBundle ? parseLayout(entity.formBundle.layoutJson) : []),
    [entity.formBundle],
  );

  const save = (id: string | number, answers: Record<string, AnswerInputBody>) =>
    patchPlaceAnswers(id, answers);

  return (
    <div className="place-fiche-tab sia-fiche-tab">
      {!entity.formBundle && (
        <Message severity="error" text="Impossible de charger la configuration du formulaire" />
      )}
      {fields &&
        panels.map((panel, panelIndex) => (
          <Panel
            key={panelIndex}
            header={placePanelLabel(panel.name)}
            toggleable
            className={`sia-form-panel ${panel.className ?? ""}`.trim()}
          >
            {panel.rows.map((row, rowIndex) => (
              <div key={rowIndex} className="project-fiche-tab-row sia-grid">
                {row.columns.map((col, colIndex) => (
                  <PlaceFormField
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

function PlaceFormField({
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
  entity: PlaceDetail;
  canEdit: boolean;
  organizationId?: number;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
}) {
  if (col.fieldId == null || col.hidden) return null;

  const fieldId = String(col.fieldId);
  const field = fields[fieldId];
  if (!field) return null;
  // Address field: present in the field catalog/layout (SpatialUnit.DETAILS_FORM), never in
  // `answers` — see this file's own top-of-file doc.
  if (field.valueBinding === "address") return null;

  const stored = resolveValueBinding(field).read(entity);

  return (
    <div className={`project-fiche-tab-col ${toGridClass(col.width)}`} data-field-id={fieldId}>
      <div className="field-value-group">
        <FieldLabel field={field} required={col.isRequired} />
        <FieldEditCell
          entityType="place"
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
