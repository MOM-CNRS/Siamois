import { Button } from "primereact/button";
import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "../../api/queryKeys";
import { useWriteMode } from "../../panels/pane/writeMode";
import { t } from "../../i18n";

export interface BulkImportDocumentProps {
  segment?: string;
  entityId?: string | number;
}

type OpenBulkFn = (params?: Record<string, unknown>) => unknown;

/**
 * Opens the PrimeFaces bulk document import dialog (DocumentBulkImportBean).
 * With segment + entityId, that fiche is pre-selected as target; without them, the dialog opens empty.
 * On success the dialog dispatches `sia-bulk-documents-done` so lists refresh.
 */
export function BulkImportDocument({ segment, entityId }: BulkImportDocumentProps) {
  const writeMode = useWriteMode();
  const queryClient = useQueryClient();

  useEffect(() => {
    const onDone = () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.entityList("document") });
    };
    window.addEventListener("sia-bulk-documents-done", onDone);
    return () => window.removeEventListener("sia-bulk-documents-done", onDone);
  }, [queryClient]);

  if (!writeMode) return null;

  const open = () => {
    const fn = (window as unknown as { siaOpenBulkDocumentImport?: OpenBulkFn }).siaOpenBulkDocumentImport;
    if (typeof fn !== "function") {
      console.warn("siaOpenBulkDocumentImport remoteCommand is not available on this page");
      return;
    }
    if (segment != null && entityId != null) {
      fn({ targetType: segment, targetId: String(entityId) });
    } else {
      fn({});
    }
  };

  return (
    <Button
      label={t("docs.bulkImport")}
      icon="bi bi-cloud-upload"
      outlined
      onClick={open}
    />
  );
}
