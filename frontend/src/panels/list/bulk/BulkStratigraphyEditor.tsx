import {useState} from "react";
import {searchProjectUnits, type StratigraphyZone} from "../../../components/relationLinks/api";
import {InlinePicker, type PickerOption} from "../../../components/relationLinks/InlinePicker";
import {type Qualifier, QualifierFields} from "../../../components/relationLinks/QualifierFields";
import type {BulkLinkMode, BulkStratigraphy} from "../../../entities/shared/bulkApi";
import type {FieldResource} from "../../../fields/types";
import {t} from "../../../i18n";
import {type BulkEditorCommonProps, BulkEditorFooter, BulkLinkModeButton} from "./BulkEditorParts";

export interface BulkStratigraphyEditorProps extends BulkEditorCommonProps {
  // The stratigraphy field picked (anterior, posterior or synchronous units) and where its units stand.
  field: FieldResource;
  position: StratigraphyZone;
  // The selection's one project, whose units are offered, and its one type (narrows the link types).
  projectId?: string;
  organizationId?: number;
  typeConceptId?: string;
  onApply: (relationship: BulkStratigraphy, mode: BulkLinkMode) => void;
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
      <BulkLinkModeButton mode={mode} onChange={setMode} disabled={running} ariaLabel={field.label} />
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
      <BulkEditorFooter
        editableCount={editableCount}
        skippedCount={skippedCount}
        running={running}
        canApply={unit != null}
        onApply={apply}
        onCancel={onCancel}
      />
    </div>
  );
}
