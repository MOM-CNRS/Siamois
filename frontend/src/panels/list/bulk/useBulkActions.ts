import {useState} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {messageForError} from "../../../api/errors";
import {queryKeys} from "../../../api/queryKeys";
import type {BulkApi, BulkLinkMode, BulkResult} from "../../../entities/shared/bulkApi";
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
  // The entity type's bulk endpoints (config.api.bulk).
  bulk: BulkApi;
  organizationId?: number;
  // After a deletion: the rows that are gone, for the selection to let go of them.
  onDeleted: (rows: RowRecord[]) => void;
}

export interface BulkActions {
  // An action is under way: the bar's controls wait for it.
  running: boolean;
  // Writes one field's answer on every one of the rows the caller may edit.
  applyAnswer: (rows: RowRecord[], fieldId: string, answer: AnswerInputBody) => Promise<void>;
  // Links a document to (or unlinks it from) every one of the rows the caller may edit.
  applyDocument: (rows: RowRecord[], documentId: string | number, mode: BulkLinkMode) => Promise<void>;
  // Deletes every one of the rows the caller may delete.
  deleteRows: (rows: RowRecord[]) => Promise<void>;
}

const NOTHING_DONE: BulkResult = { succeeded: [], failed: [] };

/**
 * The bulk actions on a list's selection — the Notion-style bar's behaviour, apart from how it is
 * drawn and how the selection is gathered. Each action goes to the entity type's bulk endpoints
 * (`config.api.bulk`), which treat every element as its unit endpoint would and answer which ones
 * they changed; this tells the user, failures included, and refreshes the lists and the open fiches.
 */
export function useBulkActions({ entityType, config, bulk, organizationId, onDeleted }: UseBulkActionsOptions): BulkActions {
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
   * Sends the bulk request while the bar shows it is busy, then reports and refreshes. A request the
   * server refuses as a whole (a patch it can't read…) is reported as an error.
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
   * Writes the answer on every editable row, in the collection's own PATCH shape.
   */
  async function applyAnswer(rows: RowRecord[], fieldId: string, answer: AnswerInputBody): Promise<void> {
    const answers = { [fieldId]: answer };
    const patch = config.api.patchBody ? config.api.patchBody(answers) : { answers };
    await run(() => bulk.patch(idsOf(editableRows(rows)), patch), "bulk.done");
  }

  /**
   * Links or unlinks the document on every editable row.
   */
  async function applyDocument(rows: RowRecord[], documentId: string | number, mode: BulkLinkMode): Promise<void> {
    const linkDocument = bulk.linkDocument;
    if (!linkDocument) return;
    await run(() => linkDocument(idsOf(editableRows(rows)), documentId, mode), "bulk.done");
  }

  /**
   * Deletes every deletable row; every pane showing a deleted one lets go of it.
   */
  async function deleteRows(rows: RowRecord[]): Promise<void> {
    const result = await run(() => bulk.delete(idsOf(deletableRows(rows))), "bulk.deleted");
    if (result.succeeded.length === 0) return;
    const deleted = new Set(result.succeeded.map(String));
    const deletedRows = rows.filter((row) => deleted.has(String(row.id)));
    // The rows' own ids (a string or a number, as the list gave them): what the caches are keyed by.
    idsOf(deletedRows).forEach((id) => {
      queryClient.removeQueries({ queryKey: queryKeys.entityDetail(entityType, id) });
      announceEntityDeleted(entityType, id);
    });
    void queryClient.invalidateQueries({ queryKey: queryKeys.organizationCounts(organizationId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.projectHomeRecent(organizationId) });
    onDeleted(deletedRows);
  }

  return { running, applyAnswer, applyDocument, deleteRows };
}
