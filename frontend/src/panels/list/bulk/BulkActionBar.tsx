import {useRef, useState} from "react";
import {Button} from "primereact/button";
import {Dialog} from "primereact/dialog";
import {OverlayPanel} from "primereact/overlaypanel";
import {messageForError} from "../../../api/errors";
import {DeleteEntityDialog} from "../../../components/detail/DeleteEntityDialog";
import type {BulkApi} from "../../../entities/shared/bulkApi";
import type {EntityTypeConfig, FieldCatalog} from "../../../entities/shared/types";
import type {FieldResource} from "../../../fields/types";
import type {FieldState} from "../../../rules";
import {t, tn} from "../../../i18n";
import {useNotify} from "../../../notify/NotifyProvider";
import type {RowRecord} from "../useEntityListData";
import {BulkDocumentEditor} from "./BulkDocumentEditor";
import {BulkFieldEditor} from "./BulkFieldEditor";
import {bulkFieldState} from "./bulkFieldState";
import {BulkFieldPicker, type BulkRelationEntry} from "./BulkFieldPicker";
import {
  bulkEditableFields,
  bulkEditContext,
  canBulkEdit,
  deletableRows,
  editableRows,
  spansSeveralProjects,
} from "./bulkSelection";
import {useBulkActions} from "./useBulkActions";
import type {ListSelection} from "./useListSelection";

export interface BulkActionBarProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  organizationId?: number;
  // The list's field catalog: the properties a bulk edit can write.
  catalog?: FieldCatalog;
  selection: ListSelection;
  // The list's rules (useRowRules): a field's state on a row, and the fields' labels — what gives a
  // bulk editor the same sources and options as the fiche's.
  rowRules?: {
    stateOf: (row: RowRecord, fieldId: string) => FieldState | undefined;
    labelOf: (fieldId: string) => string;
  };
}

// The dialog the bar has open, if any.
type OpenDialog = { kind: "field"; field: FieldResource } | { kind: "documents" } | { kind: "delete" } | null;

// The key of the documents among the picker's relations.
const DOCUMENTS_RELATION = "documents";

/**
 * The Notion-style bar laid over the top of a list while rows are ticked: how many are, a way to
 * untick them, and what can be done to all of them at once — edit one property or relation, the
 * linked documents included (« Modifier », then a click on it), or delete them. Each action only
 * touches the rows the caller may change, and says how many it skips. Only for an entity type that
 * declares bulk endpoints (`config.api.bulk`).
 */
export function BulkActionBar(props: BulkActionBarProps) {
  const bulk = props.config.api.bulk;
  if (props.selection.count === 0 || !bulk) return null;
  return <BulkActionBarContent {...props} bulk={bulk} />;
}

/**
 * The bar itself, once there is a selection and endpoints to act on it.
 */
function BulkActionBarContent({
  entityType,
  config,
  bulk,
  organizationId,
  catalog,
  selection,
  rowRules,
}: BulkActionBarProps & { bulk: BulkApi }) {
  const notify = useNotify();
  const pickerRef = useRef<OverlayPanel>(null);
  const [dialog, setDialog] = useState<OpenDialog>(null);
  // The rows the open picker or dialog acts on: the selection as it was gathered when it opened
  // (under « select all », read back from the server, the rows not shown included).
  const [target, setTarget] = useState<RowRecord[]>([]);
  const [gathering, setGathering] = useState(false);
  const actions = useBulkActions({
    entityType,
    config,
    bulk,
    organizationId,
    onDeleted: (deletedRows) => {
      // Under « select all » the deleted rows simply leave the result set.
      if (selection.all) return;
      const deleted = new Set(deletedRows.map((row) => String(row.id)));
      selection.setRows(selection.rows.filter((row) => !deleted.has(String(row.id))));
    },
  });

  const editable = editableRows(target);
  const deletable = deletableRows(target);
  const skippedCount = target.length - editable.length;
  const fields = config.api.patchAnswers ? bulkEditableFields(catalog) : [];
  // Not one selected row the caller may edit: every property says so, rather than a misleading reason.
  const noneEditable = target.length > 0 && editable.length === 0 ? t("bulk.noneEditable") : undefined;
  const context = bulkEditContext(editable, entityType, organizationId);
  const relations: BulkRelationEntry[] = bulk.linkDocument
    ? [
        {
          key: DOCUMENTS_RELATION,
          label: t("entity.document.plural"),
          icon: "bi bi-file-earmark",
          // A document is linked only inside its project: the selection must not span several.
          disabledReason: noneEditable ?? (spansSeveralProjects(editable, entityType) ? t("bulk.documentsNeedOneProject") : undefined),
        },
      ]
    : [];
  const busy = actions.running || gathering;
  const close = () => setDialog(null);

  /**
   * The selected rows, every one of them: under « select all », read back from the server first.
   * Null when they could not be read (the user is told).
   */
  async function gather(): Promise<RowRecord[] | null> {
    if (!selection.all) return selection.rows;
    setGathering(true);
    try {
      return await selection.resolve();
    } catch (error) {
      notify.error(messageForError(error, t("bulk.failed")));
      return null;
    } finally {
      setGathering(false);
    }
  }

  /**
   * Gathers the selection, then opens the property picker under the « Modifier » button.
   */
  async function openPicker(anchor: HTMLElement) {
    const rows = await gather();
    if (!rows) return;
    setTarget(rows);
    pickerRef.current?.show(null, anchor);
  }

  /**
   * Gathers the selection, then opens the deletion confirmation.
   */
  async function openDelete() {
    const rows = await gather();
    if (!rows) return;
    setTarget(rows);
    setDialog({ kind: "delete" });
  }

  /**
   * Opens the bulk editor of the property clicked in the picker.
   */
  function pickField(field: FieldResource) {
    pickerRef.current?.hide();
    setDialog({ kind: "field", field });
  }

  /**
   * Opens the bulk editor of the relation clicked in the picker that is no catalog field.
   */
  function pickRelation(key: string) {
    pickerRef.current?.hide();
    if (key === DOCUMENTS_RELATION) setDialog({ kind: "documents" });
  }

  return (
    <div className="bulk-action-bar-anchor">
      <div className="bulk-action-bar" role="toolbar" aria-label={t("bulk.toolbar")}>
        <span className="bulk-action-bar-count">{tn("bulk.selected", selection.count)}</span>
        <Button
          type="button"
          icon="bi bi-x-lg"
          text
          rounded
          size="small"
          aria-label={t("bulk.clear")}
          tooltip={t("bulk.clear")}
          tooltipOptions={{ position: "bottom" }}
          disabled={busy}
          onClick={selection.clear}
        />
        <span className="bulk-action-bar-separator" aria-hidden="true" />
        {(fields.length > 0 || bulk.linkDocument) && (
          <Button
            type="button"
            label={t("bulk.edit")}
            icon="bi bi-pencil-square"
            text
            size="small"
            disabled={busy}
            onClick={(e) => void openPicker(e.currentTarget)}
          />
        )}
        {config.api.delete && (
          <Button
            type="button"
            label={t("bulk.delete")}
            icon="bi bi-trash"
            text
            severity="danger"
            size="small"
            disabled={busy}
            onClick={() => void openDelete()}
          />
        )}
        {busy && (
          <span className="bulk-action-bar-running" role="status">
            <i className="pi pi-spin pi-spinner" aria-hidden="true" />{" "}
            {gathering ? t("bulk.loadingSelection") : t("bulk.running")}
          </span>
        )}
      </div>

      <OverlayPanel ref={pickerRef} className="bulk-field-picker-overlay">
        <BulkFieldPicker
          fields={fields}
          disabledReason={(field) =>
            noneEditable ?? (canBulkEdit(field, editable, entityType) ? undefined : t("bulk.needsOneProject"))
          }
          onPick={pickField}
          extraRelations={relations}
          onPickRelation={pickRelation}
        />
      </OverlayPanel>

      <Dialog
        visible={dialog?.kind === "field" || dialog?.kind === "documents"}
        onHide={() => !actions.running && close()}
        modal
        draggable={false}
        header={t("bulk.editTitle", {
          label: dialog?.kind === "field" ? dialog.field.label : t("entity.document.plural"),
        })}
        className="bulk-dialog"
      >
        {dialog?.kind === "field" && (
          <BulkFieldEditor
            key={dialog.field.id}
            field={dialog.field}
            context={context}
            state={bulkFieldState(
              dialog.field,
              editable.map((row) => rowRules?.stateOf(row, dialog.field.id)),
              context.typeConceptId,
            )}
            fieldLabelOf={rowRules?.labelOf}
            editableCount={editable.length}
            skippedCount={skippedCount}
            running={actions.running}
            onCancel={close}
            onApply={(answer) => {
              // The job runs in the background, its toast shows the progress: the dialog closes at once.
              void actions.applyAnswer(target, dialog.field.id, answer);
              close();
            }}
          />
        )}
        {dialog?.kind === "documents" && (
          <BulkDocumentEditor
            organizationId={organizationId}
            projectId={context.projectId}
            editableCount={editable.length}
            skippedCount={skippedCount}
            running={actions.running}
            onCancel={close}
            onApply={(documentId, mode) => {
              void actions.applyDocument(target, documentId, mode);
              close();
            }}
          />
        )}
      </Dialog>

      <DeleteEntityDialog
        visible={dialog?.kind === "delete"}
        message={tn(config.deleteRequiresName ? "bulk.deleteProjects" : "bulk.deleteConfirm", deletable.length)}
        confirmName={config.deleteRequiresName ? t("bulk.deleteWord") : undefined}
        confirmPrompt={t("bulk.deleteType", { name: t("bulk.deleteWord") })}
        busy={actions.running}
        onConfirm={() => {
          void actions.deleteRows(target);
          close();
        }}
        onHide={() => !actions.running && close()}
      />
    </div>
  );
}
