import {useState} from "react";
import {Button} from "primereact/button";
import {SelectButton} from "primereact/selectbutton";
import {searchProjectUnits, type StratigraphyZone} from "../../../components/relationLinks/api";
import {InlinePicker, type PickerOption} from "../../../components/relationLinks/InlinePicker";
import {type Qualifier, QualifierFields} from "../../../components/relationLinks/QualifierFields";
import type {BulkLinkMode, BulkStratigraphy} from "../../../entities/shared/bulkApi";
import type {FieldResource} from "../../../fields/types";
import {t, tn} from "../../../i18n";

export interface BulkStratigraphyEditorProps {
  // The stratigraphy field picked (anterior, posterior or synchronous units) and where its units stand.
  field: FieldResource;
  position: StratigraphyZone;
  // The selection's one project, whose units are offered, and its one type (narrows the link types).
  projectId?: string;
  organizationId?: number;
  typeConceptId?: string;
  editableCount: number;
  skippedCount: number;
  running: boolean;
  onApply: (relationship: BulkStratigraphy, mode: BulkLinkMode) => void;
  onCancel: () => void;
}

const NO_QUALIFIER: Qualifier = { type: null, uncertain: false };

/**
 * A stratigraphy field of the selection: adds to every selected unit the same relationship towards one
 * unit (its type and uncertainty, as on the fiche), or removes the relationship with that unit from
 * each. The bulk counterpart of the fiche's stratigraphy editor.
 */
export function BulkStratigraphyEditor({
  field,
  position,
  projectId,
  organizationId,
  typeConceptId,
  editableCount,
  skippedCount,
  running,
  onApply,
  onCancel,
}: BulkStratigraphyEditorProps) {
  const [mode, setMode] = useState<BulkLinkMode>("LINK");
  const [unit, setUnit] = useState<PickerOption | null>(null);
  const [qualifier, setQualifier] = useState<Qualifier>(NO_QUALIFIER);

  /**
   * The project's units matching what is typed.
   */
  async function loadUnits(query: string): Promise<PickerOption[]> {
    return projectId == null ? [] : searchProjectUnits(projectId, query);
  }

  /**
   * Hands the relationship over: the picked unit, where it stands, and — when adding — its qualifier.
   */
  function apply() {
    if (!unit) return;
    onApply(
      {
        relatedRecordingUnitId: unit.id,
        position,
        typeConceptId: qualifier.type?.id ?? null,
        uncertain: qualifier.uncertain,
      },
      mode,
    );
  }

  return (
    <div className="bulk-field-editor">
      <SelectButton
        value={mode}
        options={[
          { label: t("bulk.modeAdd"), value: "LINK" },
          { label: t("bulk.modeRemove"), value: "UNLINK" },
        ]}
        allowEmpty={false}
        disabled={running}
        onChange={(e) => setMode(e.value as BulkLinkMode)}
        aria-label={field.label}
      />
      <small className="bulk-field-editor-hint">
        {t(mode === "LINK" ? "bulk.stratigraphyAddHint" : "bulk.stratigraphyRemoveHint", { label: field.label })}
      </small>
      <div className="p-fluid bulk-field-editor-input">
        <InlinePicker value={unit} onChange={setUnit} load={loadUnits} placeholder={t("bulk.stratigraphyUnit")} autoFocus />
      </div>
      {mode === "LINK" && (
        <QualifierFields
          vocabulary={{ fieldId: field.id, valueConceptId: typeConceptId }}
          projectId={projectId}
          organizationId={organizationId}
          value={qualifier}
          onChange={setQualifier}
        />
      )}
      {skippedCount > 0 && (
        <small className="bulk-field-editor-skipped" role="note">
          <i className="bi bi-info-circle" aria-hidden="true" /> {tn("bulk.skipped", skippedCount)}
        </small>
      )}
      <div className="bulk-field-editor-actions">
        <Button type="button" label={t("common.cancel")} text disabled={running} onClick={onCancel} />
        <Button
          type="button"
          label={tn("bulk.apply", editableCount)}
          icon="bi bi-check2-all"
          loading={running}
          disabled={running || unit == null || editableCount === 0}
          onClick={apply}
        />
      </div>
    </div>
  );
}
