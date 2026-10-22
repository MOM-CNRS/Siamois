import { useEffect, useState } from "react";
import { Button } from "primereact/button";
import { Dialog } from "primereact/dialog";
import { InputText } from "primereact/inputtext";
import { t } from "../../i18n";
import { useToastOnChange } from "../../notify/NotifyProvider";

export interface DeleteEntityDialogProps {
  visible: boolean;
  // The name the user must type to confirm (a project). Absent: a plain "are you sure?".
  confirmName?: string;
  // The server's refusal (the entity still has relationships...), shown in the dialog, which stays open.
  error?: string | null;
  busy?: boolean;
  onConfirm: () => void;
  onHide: () => void;
}

/**
 * The permanent-deletion confirmation. For a project (`confirmName`) the confirm button stays
 * disabled until the project's name has been typed exactly.
 */
export function DeleteEntityDialog({ visible, confirmName, error, busy, onConfirm, onHide }: DeleteEntityDialogProps) {
  const [typed, setTyped] = useState("");
  useEffect(() => {
    if (visible) setTyped("");
  }, [visible, confirmName]);

  const needsName = confirmName != null;
  const canConfirm = !busy && (!needsName || typed.trim() === confirmName.trim());

  useToastOnChange(error);
  return (
    <Dialog
      visible={visible}
      onHide={onHide}
      modal
      draggable={false}
      header={t("delete.entityTitle")}
      className="delete-entity-dialog"
      style={{ width: "min(32rem, 94vw)" }}
      footer={
        <>
          <Button label={t("common.cancel")} text onClick={onHide} disabled={busy} />
          <Button
            label={t("delete.submit")}
            icon="bi bi-trash"
            severity="danger"
            onClick={onConfirm}
            disabled={!canConfirm}
            loading={busy}
          />
        </>
      }
    >
      <p className="delete-entity-dialog-message">{needsName ? t("delete.projectWarning") : t("delete.entityConfirm")}</p>
      {needsName && (
        <div className="delete-entity-dialog-confirm">
          <label htmlFor="delete-entity-name">{t("delete.projectType", { name: confirmName })}</label>
          <InputText
            id="delete-entity-name"
            value={typed}
            autoFocus
            autoComplete="off"
            aria-label={t("delete.projectInputLabel")}
            onChange={(e) => setTyped(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && canConfirm) onConfirm();
            }}
            className="w-full"
          />
        </div>
      )}
    </Dialog>
  );
}
