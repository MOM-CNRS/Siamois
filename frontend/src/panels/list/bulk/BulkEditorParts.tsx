import {Button} from "primereact/button";
import {SelectButton} from "primereact/selectbutton";
import type {BulkLinkMode} from "../../../entities/shared/bulkApi";
import {t, tn} from "../../../i18n";

/** What every bulk editor of the « Modifier » picker gets, whatever it edits. */
export interface BulkEditorCommonProps {
  // The selected rows the caller may edit (written), and those they may not (skipped).
  editableCount: number;
  skippedCount: number;
  running: boolean;
  onCancel: () => void;
}

export interface BulkEditorFooterProps extends BulkEditorCommonProps {
  // Whether what the editor holds can be applied (a value picked, something to change…).
  canApply: boolean;
  onApply: () => void;
}

/**
 * The end of a bulk editor: the rows that will be skipped, then « Annuler » and « Appliquer à N éléments ».
 */
export function BulkEditorFooter({ editableCount, skippedCount, running, canApply, onApply, onCancel }: BulkEditorFooterProps) {
  return (
    <>
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
          disabled={running || !canApply || editableCount === 0}
          onClick={onApply}
        />
      </div>
    </>
  );
}

export interface BulkLinkModeButtonProps {
  mode: BulkLinkMode;
  onChange: (mode: BulkLinkMode) => void;
  disabled: boolean;
  ariaLabel: string;
}

/**
 * « Ajouter » / « Retirer » for a relation of the selection: whether the link is added to each entity
 * or removed from each.
 */
export function BulkLinkModeButton({ mode, onChange, disabled, ariaLabel }: BulkLinkModeButtonProps) {
  return (
    <SelectButton
      value={mode}
      options={[
        { label: t("bulk.modeAdd"), value: "LINK" },
        { label: t("bulk.modeRemove"), value: "UNLINK" },
      ]}
      allowEmpty={false}
      disabled={disabled}
      onChange={(e) => onChange(e.value as BulkLinkMode)}
      aria-label={ariaLabel}
    />
  );
}
