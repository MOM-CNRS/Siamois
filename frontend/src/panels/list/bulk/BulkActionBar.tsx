import {useRef, useState} from "react";
import {Button} from "primereact/button";
import {Dialog} from "primereact/dialog";
import {OverlayPanel} from "primereact/overlaypanel";
import {DeleteEntityDialog} from "../../../components/detail/DeleteEntityDialog";
import {isDocumentLinkSegment} from "../../../entities/document/api";
import type {EntityTypeConfig, FieldCatalog} from "../../../entities/shared/types";
import type {FieldResource} from "../../../fields/types";
import {t, tn} from "../../../i18n";
import type {RowRecord} from "../useEntityListData";
import {BulkDocumentEditor} from "./BulkDocumentEditor";
import {BulkFieldEditor} from "./BulkFieldEditor";
import {BulkFieldPicker} from "./BulkFieldPicker";
import {
  bulkEditableFields,
  bulkEditContext,
  canBulkEdit,
  commonProjectId,
  deletableRows,
  editableRows,
} from "./bulkSelection";
import {useBulkActions} from "./useBulkActions";

export interface BulkActionBarProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  organizationId?: number;
  // The list's field catalog: the properties a bulk edit can write.
  catalog?: FieldCatalog;
  selectedRows: RowRecord[];
  onSelectionChange: (rows: RowRecord[]) => void;
}

// The dialog the bar has open, if any.
type OpenDialog = { kind: "field"; field: FieldResource } | { kind: "documents" } | { kind: "delete" } | null;

/**
 * The Notion-style bar laid over the top of a list while rows are ticked: how many are, a way to
 * untick them, and what can be done to all of them at once — edit one property or relation
 * (« Modifier », then a click on the property), link or unlink a document, delete them. Each action
 * only touches the rows the caller may change, and says how many it skips.
 */
export function BulkActionBar({
  entityType,
  config,
  organizationId,
  catalog,
  selectedRows,
  onSelectionChange,
}: BulkActionBarProps) {
  const pickerRef = useRef<OverlayPanel>(null);
  const [dialog, setDialog] = useState<OpenDialog>(null);
  const actions = useBulkActions({ entityType, config, organizationId, selectedRows, onSelectionChange });

  if (selectedRows.length === 0) return null;

  const editable = editableRows(selectedRows);
  const deletable = deletableRows(selectedRows);
  const skippedCount = selectedRows.length - editable.length;
  const fields = config.api.patchAnswers ? bulkEditableFields(catalog) : [];
  const context = bulkEditContext(editable, entityType, organizationId);
  const canLinkDocuments = isDocumentLinkSegment(config.collectionPath);
  // A place has no project: any document of the organization can be linked to it.
  const documentsBlocked = canLinkDocuments && entityType !== "place" && commonProjectId(editable, entityType) == null;
  const close = () => setDialog(null);

  /**
   * Opens the bulk editor of the property clicked in the picker.
   */
  function pickField(field: FieldResource) {
    pickerRef.current?.hide();
    setDialog({ kind: "field", field });
  }

  return (
    <div className="bulk-action-bar-anchor">
      <div className="bulk-action-bar" role="toolbar" aria-label={t("bulk.toolbar")}>
        <span className="bulk-action-bar-count">{tn("bulk.selected", selectedRows.length)}</span>
        <Button
          type="button"
          icon="bi bi-x-lg"
          text
          rounded
          size="small"
          aria-label={t("bulk.clear")}
          tooltip={t("bulk.clear")}
          tooltipOptions={{ position: "bottom" }}
          disabled={actions.running}
          onClick={() => onSelectionChange([])}
        />
        <span className="bulk-action-bar-separator" aria-hidden="true" />
        {fields.length > 0 && (
          <Button
            type="button"
            label={t("bulk.edit")}
            icon="bi bi-pencil-square"
            text
            size="small"
            disabled={actions.running || editable.length === 0}
            onClick={(e) => pickerRef.current?.toggle(e)}
          />
        )}
        {canLinkDocuments && (
          <span title={documentsBlocked ? t("bulk.documentsNeedOneProject") : undefined}>
            <Button
              type="button"
              label={t("bulk.documents")}
              icon="bi bi-link-45deg"
              text
              size="small"
              disabled={actions.running || editable.length === 0 || documentsBlocked}
              onClick={() => setDialog({ kind: "documents" })}
            />
          </span>
        )}
        {config.api.delete && (
          <Button
            type="button"
            label={t("bulk.delete")}
            icon="bi bi-trash"
            text
            severity="danger"
            size="small"
            disabled={actions.running || deletable.length === 0}
            onClick={() => setDialog({ kind: "delete" })}
          />
        )}
        {actions.running && (
          <span className="bulk-action-bar-running" role="status">
            <i className="pi pi-spin pi-spinner" aria-hidden="true" /> {t("bulk.running")}
          </span>
        )}
      </div>

      <OverlayPanel ref={pickerRef} className="bulk-field-picker-overlay">
        <BulkFieldPicker
          fields={fields}
          disabledReason={(field) => (canBulkEdit(field, editable, entityType) ? undefined : t("bulk.needsOneProject"))}
          onPick={pickField}
        />
      </OverlayPanel>

      <Dialog
        visible={dialog?.kind === "field"}
        onHide={() => !actions.running && close()}
        modal
        draggable={false}
        header={dialog?.kind === "field" ? t("bulk.editTitle", { label: dialog.field.label }) : ""}
        className="bulk-dialog"
      >
        {dialog?.kind === "field" && (
          <BulkFieldEditor
            key={dialog.field.id}
            field={dialog.field}
            context={context}
            editableCount={editable.length}
            skippedCount={skippedCount}
            running={actions.running}
            onCancel={close}
            onApply={(answer) => void actions.applyAnswer(dialog.field.id, answer).then(close)}
          />
        )}
      </Dialog>

      <Dialog
        visible={dialog?.kind === "documents"}
        onHide={() => !actions.running && close()}
        modal
        draggable={false}
        header={t("bulk.documentsTitle")}
        className="bulk-dialog"
      >
        {dialog?.kind === "documents" && (
          <BulkDocumentEditor
            organizationId={organizationId}
            projectId={context.projectId}
            editableCount={editable.length}
            skippedCount={skippedCount}
            running={actions.running}
            onCancel={close}
            onApply={(documentId, mode) =>
              void actions.applyDocument(config.collectionPath, documentId, mode).then(close)
            }
          />
        )}
      </Dialog>

      <DeleteEntityDialog
        visible={dialog?.kind === "delete"}
        message={tn(config.deleteRequiresName ? "bulk.deleteProjects" : "bulk.deleteConfirm", deletable.length)}
        confirmName={config.deleteRequiresName ? t("bulk.deleteWord") : undefined}
        confirmPrompt={t("bulk.deleteType", { name: t("bulk.deleteWord") })}
        busy={actions.running}
        onConfirm={() => void actions.deleteSelection().then(close)}
        onHide={() => !actions.running && close()}
      />
    </div>
  );
}
