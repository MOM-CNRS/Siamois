import {useState} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {messageForError} from "../../../api/errors";
import {queryKeys} from "../../../api/queryKeys";
import {bulkDelete, bulkLinkDocument, type BulkLinkMode, bulkPatch, type BulkResult} from "../../../entities/shared/bulkApi";
import type {EntityTypeConfig} from "../../../entities/shared/types";
import type {AnswerInputBody} from "../../../fields/types";
import {t, tn} from "../../../i18n";
import {useNotify} from "../../../notify/NotifyProvider";
import {announceEntityDeleted} from "../../detail/entityDeleted";
import type {RowRecord} from "../useEntityListData";
import {deletableRows, editableRows, idsOf} from "./bulkSelection";

export interface UseBulkActionsOptions {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  organizationId?: number;
  selectedRows: RowRecord[];
  onSelectionChange: (rows: RowRecord[]) => void;
}

export interface BulkActions {
  // An action is under way: the bar's controls wait for it.
  running: boolean;
  // Writes one field's answer on every selected row the caller may edit.
  applyAnswer: (fieldId: string, answer: AnswerInputBody) => Promise<void>;
  // Links a document to (or unlinks it from) every selected row the caller may edit.
  applyDocument: (documentId: string | number, mode: BulkLinkMode) => Promise<void>;
  // Deletes every selected row the caller may delete; those deleted leave the selection.
  deleteSelection: () => Promise<void>;
}

const NOTHING_DONE: BulkResult = { succeeded: [], failed: [] };

/**
 * The bulk actions of a list's selection — the Notion-style bar's behaviour, apart from how it is
 * drawn. Each action is ONE request to the server's bulk endpoint of the collection
 * (BulkActionsControllerApi), which treats every element as its unit endpoint would and answers
 * which ones it changed; this tells the user, failures included, and refreshes the lists and the
 * open fiches.
 */
export function useBulkActions({
  entityType,
  config,
  organizationId,
  selectedRows,
  onSelectionChange,
}: UseBulkActionsOptions): BulkActions {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [running, setRunning] = useState(false);

  /**
   * Re-fetches what a bulk action may have changed: the lists (of any type: a relation shows on
   * both sides) and the open fiches.
   */
  function refresh() {
    void queryClient.invalidateQueries({ queryKey: queryKeys.entityLists() });
    void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetails() });
  }

  /**
   * Tells the user what an action did: a success toast, or a warning naming the first failure.
   */
  function report(result: BulkResult, successKey: "bulk.done" | "bulk.deleted") {
    if (result.failed.length === 0) {
      notify.success(tn(successKey, result.succeeded.length));
      return;
    }
    const params = { done: result.succeeded.length, failed: result.failed.length, first: result.failed[0].message };
    if (result.succeeded.length === 0) notify.error(params.first);
    else notify.warn(t("bulk.partial", params));
  }

  /**
   * Sends one bulk request while the bar shows it is busy, then reports and refreshes. A request the
   * server refuses as a whole (an empty selection, a patch it can't read) is reported as an error.
   */
  async function run(request: () => Promise<BulkResult>, successKey: "bulk.done" | "bulk.deleted"): Promise<BulkResult> {
    setRunning(true);
    try {
      const result = await request();
      report(result, successKey);
      refresh();
      return result;
    } catch (error) {
      notify.error(messageForError(error, t("bulk.failed")));
      return NOTHING_DONE;
    } finally {
      setRunning(false);
    }
  }

  /**
   * Writes the answer on every editable selected row, in the collection's own PATCH shape.
   */
  async function applyAnswer(fieldId: string, answer: AnswerInputBody): Promise<void> {
    const answers = { [fieldId]: answer };
    const patch = config.api.patchBody ? config.api.patchBody(answers) : { answers };
    await run(() => bulkPatch(config.collectionPath, idsOf(editableRows(selectedRows)), patch), "bulk.done");
  }

  /**
   * Links or unlinks the document on every editable selected row.
   */
  async function applyDocument(documentId: string | number, mode: BulkLinkMode): Promise<void> {
    await run(
      () => bulkLinkDocument(config.collectionPath, idsOf(editableRows(selectedRows)), documentId, mode),
      "bulk.done",
    );
  }

  /**
   * Deletes every deletable selected row; the deleted ones leave the selection and every pane
   * showing them lets go of them.
   */
  async function deleteSelection(): Promise<void> {
    const result = await run(() => bulkDelete(config.collectionPath, idsOf(deletableRows(selectedRows))), "bulk.deleted");
    if (result.succeeded.length === 0) return;
    const deleted = new Set(result.succeeded.map(String));
    // The rows' own ids (a string or a number, as the list gave them): what the caches are keyed by.
    idsOf(selectedRows.filter((row) => deleted.has(String(row.id)))).forEach((id) => {
      queryClient.removeQueries({ queryKey: queryKeys.entityDetail(entityType, id) });
      announceEntityDeleted(entityType, id);
    });
    void queryClient.invalidateQueries({ queryKey: queryKeys.organizationCounts(organizationId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.projectHomeRecent(organizationId) });
    onSelectionChange(selectedRows.filter((row) => !deleted.has(String(row.id))));
  }

  return { running, applyAnswer, applyDocument, deleteSelection };
}
