import {useState} from "react";
import {Button} from "primereact/button";
import {SelectButton} from "primereact/selectbutton";
import type {FieldEditContext} from "../../../fields/editContext";
import {renderAnswerCell} from "../../../fields/display";
import {isEmptyValue} from "../../../fields/FieldLabel";
import {getFieldRenderer} from "../../../fields/registry";
import {picksAsChips} from "../../../fields/renderers";
import type {AnswerInputBody, FieldResource} from "../../../fields/types";
import {t, tn} from "../../../i18n";
import {useOpenEntity} from "../../pane/entityNavigation";
import type {BulkFieldState} from "./bulkFieldState";
import {bulkAnswerInput, type BulkEditMode, isMultiValued} from "./bulkSelection";

export interface BulkFieldEditorProps {
  field: FieldResource;
  context: FieldEditContext;
  // What the fiche's editor of the field gets from the form's rules: the place sources (INSEE…) and
  // the options the field is narrowed to.
  state?: BulkFieldState;
  // Names another field in the picker's hints ("fill X to narrow the search").
  fieldLabelOf?: (fieldId: string) => string;
  // The selected rows the caller may edit (written), and those they may not (skipped).
  editableCount: number;
  skippedCount: number;
  running: boolean;
  onApply: (answer: AnswerInputBody) => void;
  onCancel: () => void;
}

const MODE_HINTS: Record<BulkEditMode, "bulk.modeSetHint" | "bulk.modeAddHint" | "bulk.modeRemoveHint"> = {
  set: "bulk.modeSetHint",
  add: "bulk.modeAddHint",
  remove: "bulk.modeRemoveHint",
};

/**
 * The modes a multi-valued field offers: adding and removing first (the safe ones, which keep each
 * entity's other values), replacing last.
 */
function modeOptions(): { label: string; value: BulkEditMode }[] {
  return [
    { label: t("bulk.modeAdd"), value: "add" },
    { label: t("bulk.modeRemove"), value: "remove" },
    { label: t("bulk.modeSet"), value: "set" },
  ];
}

/**
 * Edits one field for the whole selection: the field's own editor (the same one a cell opens), and
 * for a multi-valued field whether the picked values are added to each entity's, removed from them
 * or replace them. Nothing is written until « Appliquer ».
 */
export function BulkFieldEditor({
  field,
  context,
  state,
  fieldLabelOf,
  editableCount,
  skippedCount,
  running,
  onApply,
  onCancel,
}: BulkFieldEditorProps) {
  const multiple = isMultiValued(field);
  const [mode, setMode] = useState<BulkEditMode>(multiple ? "add" : "set");
  const [value, setValue] = useState<unknown>(multiple ? [] : null);
  const Renderer = getFieldRenderer(field.answerType);
  const openEntity = useOpenEntity();
  // A single reference whose picker holds the picked value as plain text (a concept, a person): shown
  // as the chip a fiche field shows it as. The other pickers already show their own chips.
  const pickedChip =
    field.answerType.startsWith("SELECT_ONE") && !picksAsChips(field, openEntity) && !isEmptyValue(value)
      ? renderAnswerCell(field, value, { all: true })
      : null;
  // Adding or removing nothing does nothing: only replacing may be done with an empty value (it clears).
  const nothingToChange = mode !== "set" && isEmptyValue(value);

  return (
    <div className="bulk-field-editor">
      {multiple && (
        <>
          <SelectButton
            value={mode}
            options={modeOptions()}
            allowEmpty={false}
            disabled={running}
            onChange={(e) => setMode(e.value as BulkEditMode)}
            aria-label={field.label}
          />
          <small className="bulk-field-editor-hint">{t(MODE_HINTS[mode])}</small>
        </>
      )}
      <div className="p-fluid bulk-field-editor-input">
        <Renderer
          field={field}
          value={value}
          readOnly={running}
          required={false}
          organizationId={context.organizationId}
          context={context}
          optionsContext={state?.optionsContext}
          placeContext={state?.placeContext}
          fieldLabelOf={fieldLabelOf}
          onChange={setValue}
        />
      </div>
      {pickedChip && <div className="bulk-field-editor-picked">{pickedChip}</div>}
      {mode === "set" && isEmptyValue(value) && <small className="bulk-field-editor-hint">{t("bulk.emptyClears")}</small>}
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
          disabled={running || nothingToChange || editableCount === 0}
          onClick={() => onApply(bulkAnswerInput(field, mode, value))}
        />
      </div>
    </div>
  );
}
