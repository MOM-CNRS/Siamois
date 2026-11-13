import {Button} from "primereact/button";
import {Dialog} from "primereact/dialog";
import {t, tn} from "../../../i18n";

export interface BulkDuplicateDialogProps {
  visible: boolean;
  // The selected rows that will be duplicated (those the caller may edit), and those skipped.
  duplicableCount: number;
  skippedCount: number;
  busy: boolean;
  onConfirm: () => void;
  onHide: () => void;
}

/**
 * The confirmation of a bulk duplication: how many elements will be copied, and the warning that each
 * one is copied alone — its hierarchy (parents, children) and its relations are not duplicated.
 */
export function BulkDuplicateDialog({ visible, duplicableCount, skippedCount, busy, onConfirm, onHide }: BulkDuplicateDialogProps) {
  return (
    <Dialog
      visible={visible}
      onHide={onHide}
      modal
      draggable={false}
      header={t("bulk.duplicateTitle")}
      className="bulk-dialog"
      footer={
        <>
          <Button label={t("common.cancel")} text onClick={onHide} disabled={busy} />
          <Button
            label={t("bulk.duplicateSubmit")}
            icon="bi bi-copy"
            onClick={onConfirm}
            disabled={busy || duplicableCount === 0}
            loading={busy}
          />
        </>
      }
    >
      <div className="bulk-field-editor">
        <p>{tn("bulk.duplicateConfirm", duplicableCount)}</p>
        <p className="bulk-duplicate-warning" role="note">
          <i className="bi bi-exclamation-triangle" aria-hidden="true" /> {t("bulk.duplicateNoHierarchy")}
        </p>
        {skippedCount > 0 && <small className="bulk-field-editor-skipped">{tn("bulk.skipped", skippedCount)}</small>}
      </div>
    </Dialog>
  );
}
