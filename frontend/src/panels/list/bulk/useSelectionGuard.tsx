import {type ReactNode, useRef, useState} from "react";
import {ConfirmDialog} from "primereact/confirmdialog";
import {t, tn} from "../../../i18n";
import type {ListSelection} from "./useListSelection";

export interface SelectionGuard {
  // Runs `apply` (a change of search or filters) at once when nothing is selected; otherwise first
  // warns that the selection will be emptied, and runs it only once the user agrees.
  guard: (apply: () => void) => void;
  // The warning, to render once.
  dialog: ReactNode;
}

/**
 * Keeps a list's selection true to what it shows: a search or a filter changes the rows, so before
 * one is applied over a selection the user is warned that it will be emptied — and it is, on their
 * agreement — rather than a « N sélectionnées » left counting rows that may no longer be listed.
 */
export function useSelectionGuard(selection: ListSelection): SelectionGuard {
  const pending = useRef<(() => void) | null>(null);
  const [visible, setVisible] = useState(false);

  /**
   * Applies the change now, or once the user agrees to lose the selection.
   */
  function guard(apply: () => void) {
    if (selection.count === 0) {
      apply();
      return;
    }
    pending.current = apply;
    setVisible(true);
  }

  /**
   * The user agreed: the selection is emptied, then the change applied.
   */
  function accept() {
    const apply = pending.current;
    pending.current = null;
    selection.clear();
    apply?.();
  }

  /**
   * The user kept the selection: the change is dropped.
   */
  function reject() {
    pending.current = null;
  }

  const dialog = (
    <ConfirmDialog
      visible={visible}
      onHide={() => setVisible(false)}
      header={t("bulk.filterWarningTitle")}
      message={tn("bulk.filterWarning", selection.count)}
      icon="bi bi-exclamation-triangle"
      acceptLabel={t("bulk.filterWarningAccept")}
      rejectLabel={t("common.cancel")}
      accept={accept}
      reject={reject}
      className="bulk-filter-warning"
    />
  );

  return { guard, dialog };
}
