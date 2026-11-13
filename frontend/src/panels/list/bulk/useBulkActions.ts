import {useState} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {messageForError} from "../../../api/errors";
import {queryKeys} from "../../../api/queryKeys";
import type {BulkApi, BulkJobRef, BulkLinkMode, BulkResult, BulkStratigraphy} from "../../../entities/shared/bulkApi";
import type {EntityTypeConfig} from "../../../entities/shared/types";
import type {AnswerInputBody} from "../../../fields/types";
import {type MessageKey, t} from "../../../i18n";
import {useNotify} from "../../../notify/NotifyProvider";
import {announceEntityDeleted} from "../../detail/entityDeleted";
import type {RowRecord} from "../useEntityListData";
import {type BulkSuccessKey, trackBulkJob} from "./bulkJobTracker";
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
  // Adds a stratigraphic relationship to (or removes it from) every one of the rows the caller may edit.
  applyStratigraphy: (rows: RowRecord[], relationship: BulkStratigraphy, mode: BulkLinkMode) => Promise<void>;
  // Deletes every one of the rows the caller may delete.
  deleteRows: (rows: RowRecord[]) => Promise<void>;
}

const NOTHING_DONE: BulkResult = { succeeded: [], failed: [] };

/**
 * The ids of the rows the caller may edit: those an edit action is sent for.
 */
function editableIds(rows: RowRecord[]): (string | number)[] {
  return idsOf(editableRows(rows));
}

/**
 * Adds up the results of the jobs of one action.
 */
function merge(results: BulkResult[]): BulkResult {
  return {
    succeeded: results.flatMap((result) => result.succeeded),
    failed: results.flatMap((result) => result.failed),
  };
}

/**
 * The bulk actions on a list's selection — the Notion-style bar's behaviour, apart from how it is
 * drawn and how the selection is gathered. Each action starts a job on the entity type's bulk
 * endpoints (`config.api.bulk`), processed in the background by the server. The job is followed by
 * the app's bulk job tracker — its toast, its final message and the refresh of the lists are the
 * BulkJobNotifier's, whatever page the user is on by then — and this waits for it only to let go of
 * the rows a deletion removed.
 */
export function useBulkActions({ entityType, config, bulk, organizationId, onDeleted }: UseBulkActionsOptions): BulkActions {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [running, setRunning] = useState(false);

  /**
   * Starts the action's jobs, hands them to the tracker and waits for their result (the bar stays busy
   * meanwhile). A request the server refuses as a whole (a patch it can't read…) is told here; a job's
   * own outcome is told by the notifier.
   */
  async function run(label: MessageKey, successKey: BulkSuccessKey, start: () => Promise<BulkJobRef[]>): Promise<BulkResult> {
    setRunning(true);
    try {
      const refs = await startOrTell(start);
      return merge(await Promise.all(refs.map((ref) => trackBulkJob({ id: ref.id, label, successKey }, ref.total))));
    } catch {
      // A job that stopped on an error: the notifier tells it.
      return NOTHING_DONE;
    } finally {
      setRunning(false);
    }
  }

  /**
   * Starts the action's jobs; a request the server refuses as a whole is told here (no job to follow).
   */
  async function startOrTell(start: () => Promise<BulkJobRef[]>): Promise<BulkJobRef[]> {
    try {
      return await start();
    } catch (error) {
      notify.error(messageForError(error, t("bulk.failed")));
      return [];
    }
  }

  /**
   * Writes the answer on every editable row, in the collection's own PATCH shape.
   */
  async function applyAnswer(rows: RowRecord[], fieldId: string, answer: AnswerInputBody): Promise<void> {
    const answers = { [fieldId]: answer };
    const patch = config.api.patchBody ? config.api.patchBody(answers) : { answers };
    await run("bulk.taskEdit", "bulk.editDone", () => bulk.patch(editableIds(rows), patch));
  }

  /**
   * Links or unlinks the document on every editable row.
   */
  async function applyDocument(rows: RowRecord[], documentId: string | number, mode: BulkLinkMode): Promise<void> {
    const linkDocument = bulk.linkDocument;
    if (!linkDocument) return;
    await run("bulk.taskLinks", "bulk.linksDone", () => linkDocument(editableIds(rows), documentId, mode));
  }

  /**
   * Adds or removes the stratigraphic relationship on every editable row.
   */
  async function applyStratigraphy(rows: RowRecord[], relationship: BulkStratigraphy, mode: BulkLinkMode): Promise<void> {
    const linkStratigraphy = bulk.linkStratigraphy;
    if (!linkStratigraphy) return;
    await run("bulk.taskLinks", "bulk.linksDone", () => linkStratigraphy(editableIds(rows), relationship, mode));
  }

  /**
   * Deletes every deletable row; every pane showing a deleted one lets go of it.
   */
  async function deleteRows(rows: RowRecord[]): Promise<void> {
    const result = await run("bulk.taskDelete", "bulk.deleteDone", () => bulk.delete(idsOf(deletableRows(rows))));
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

  return { running, applyAnswer, applyDocument, applyStratigraphy, deleteRows };
}
