import {useState} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {queryKeys} from "../../../api/queryKeys";
import {linkDocument, unlinkDocument} from "../../../entities/document/api";
import type {EntityTypeConfig} from "../../../entities/shared/types";
import type {AnswerInputBody} from "../../../fields/types";
import {t, tn} from "../../../i18n";
import {useNotify} from "../../../notify/NotifyProvider";
import {announceEntityDeleted} from "../../detail/entityDeleted";
import type {RowRecord} from "../useEntityListData";
import {deletableRows, editableRows, idsOf} from "./bulkSelection";
import {type BulkOutcome, runBulk} from "./runBulk";

/** Whether a bulk document action links the document to every entity, or removes that link. */
export type DocumentLinkMode = "link" | "unlink";

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
  applyAnswer: (fieldId: string, answer: AnswerInputBody) => Promise<BulkOutcome>;
  // Links a document to (or unlinks it from) every selected row the caller may edit.
  applyDocument: (segment: string, documentId: string | number, mode: DocumentLinkMode) => Promise<BulkOutcome>;
  // Deletes every selected row the caller may delete; those deleted leave the selection.
  deleteSelection: () => Promise<BulkOutcome>;
}

/**
 * The bulk actions of a list's selection — the Notion-style bar's behaviour, apart from how it is
 * drawn. Each action goes through the same per-entity endpoint a single edit uses (so each entity
 * is checked on its own rights and rules), several at a time; it then tells the user what it did,
 * failures included, and refreshes the lists and the open fiches.
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
  function report(outcome: BulkOutcome, successKey: "bulk.done" | "bulk.deleted") {
    if (outcome.failed.length === 0) {
      notify.success(tn(successKey, outcome.succeeded.length));
      return;
    }
    const params = {
      done: outcome.succeeded.length,
      failed: outcome.failed.length,
      first: outcome.failed[0].message,
    };
    if (outcome.succeeded.length === 0) notify.error(params.first);
    else notify.warn(t("bulk.partial", params));
  }

  /**
   * Runs one task per id while the bar shows it is busy, then reports and refreshes.
   */
  async function run(
    ids: (string | number)[],
    task: (id: string | number) => Promise<unknown>,
    successKey: "bulk.done" | "bulk.deleted",
  ): Promise<BulkOutcome> {
    setRunning(true);
    try {
      const outcome = await runBulk(ids, task, t("bulk.failed"));
      report(outcome, successKey);
      refresh();
      return outcome;
    } finally {
      setRunning(false);
    }
  }

  /**
   * Writes the answer on every editable selected row.
   */
  function applyAnswer(fieldId: string, answer: AnswerInputBody): Promise<BulkOutcome> {
    const patch = config.api.patchAnswers;
    if (!patch) return Promise.resolve({ succeeded: [], failed: [] });
    return run(idsOf(editableRows(selectedRows)), (id) => patch(id, { [fieldId]: answer }), "bulk.done");
  }

  /**
   * Links or unlinks the document on every editable selected row.
   */
  function applyDocument(segment: string, documentId: string | number, mode: DocumentLinkMode): Promise<BulkOutcome> {
    const write = mode === "link" ? linkDocument : unlinkDocument;
    return run(idsOf(editableRows(selectedRows)), (id) => write(segment, id, documentId), "bulk.done");
  }

  /**
   * Deletes every deletable selected row; the deleted ones leave the selection and every pane
   * showing them lets go of them.
   */
  async function deleteSelection(): Promise<BulkOutcome> {
    const remove = config.api.delete;
    if (!remove) return { succeeded: [], failed: [] };
    const outcome = await run(idsOf(deletableRows(selectedRows)), (id) => remove(id), "bulk.deleted");
    const deleted = new Set(outcome.succeeded.map(String));
    outcome.succeeded.forEach((id) => {
      queryClient.removeQueries({ queryKey: queryKeys.entityDetail(entityType, id) });
      announceEntityDeleted(entityType, id);
    });
    if (deleted.size > 0) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.organizationCounts(organizationId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.projectHomeRecent(organizationId) });
      onSelectionChange(selectedRows.filter((row) => !deleted.has(String(row.id))));
    }
    return outcome;
  }

  return { running, applyAnswer, applyDocument, deleteSelection };
}
