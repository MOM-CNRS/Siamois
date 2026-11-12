import {useEffect, useRef, useState, useSyncExternalStore} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {ApiError} from "../../../api/client";
import {messageForError} from "../../../api/errors";
import {queryKeys} from "../../../api/queryKeys";
import type {BulkResult} from "../../../entities/shared/bulkApi";
import {t, tn} from "../../../i18n";
import {type Notify, type TaskToast, useNotify} from "../../../notify/NotifyProvider";
import {
  acknowledgeBulkJob,
  type BulkSuccessKey,
  bulkJobsSnapshot,
  resumeBulkJobs,
  subscribeBulkJobs,
  type TrackedBulkJob,
} from "./bulkJobTracker";

// The notifiers mounted on the page (one per React root): the first one tells, the others wait their turn.
const claims: symbol[] = [];
const claimListeners = new Set<() => void>();

/**
 * Tells the notifiers who tells now.
 */
function emitClaims() {
  claimListeners.forEach((listener) => listener());
}

/**
 * Tells the user what a finished bulk job did: a success, or a warning naming the first failure.
 */
export function reportBulkResult(notify: Notify, result: BulkResult, successKey: BulkSuccessKey): void {
  if (result.failed.length === 0) {
    notify.success(tn(successKey, result.succeeded.length));
    return;
  }
  const params = { done: result.succeeded.length, failed: result.failed.length, first: result.failed[0].message };
  if (result.succeeded.length === 0) notify.error(params.first);
  else notify.warn(t("bulk.partial", params));
}

/**
 * Tells the user how a job ended. A job that can no longer be read (forgotten after its hour, or
 * someone else's) is let go of silently.
 */
function reportOutcome(notify: Notify, job: TrackedBulkJob): void {
  const outcome = job.outcome;
  if (!outcome) return;
  if ("result" in outcome) {
    reportBulkResult(notify, outcome.result, job.successKey);
    return;
  }
  if (outcome.error instanceof ApiError && outcome.error.status === 404) return;
  notify.error(messageForError(outcome.error, t("bulk.failed")));
}

/**
 * Whether this notifier is the one that tells, among those of the page.
 */
function useIsTeller(): boolean {
  const [claim] = useState(() => Symbol("bulk-job-notifier"));
  const [teller, setTeller] = useState(false);
  useEffect(() => {
    claims.push(claim);
    const listener = () => setTeller(claims[0] === claim);
    claimListeners.add(listener);
    emitClaims();
    return () => {
      claims.splice(claims.indexOf(claim), 1);
      claimListeners.delete(listener);
      emitClaims();
    };
  }, [claim]);
  return teller;
}

/**
 * The bulk actions running in the background, shown whatever the page: a toast per job with its
 * progress while the server works, then what it did. Mounted once by the app, it also goes on
 * following the jobs a previous page of the tab started (they are remembered for the tab), so the
 * message survives a page change.
 */
export function BulkJobNotifier() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const teller = useIsTeller();
  const jobs = useSyncExternalStore(subscribeBulkJobs, bulkJobsSnapshot);
  const toasts = useRef(new Map<string, TaskToast>());

  useEffect(() => {
    resumeBulkJobs();
  }, []);

  useEffect(() => {
    if (!teller) return;
    jobs.forEach((job) => {
      const toast = toasts.current.get(job.id);
      if (job.outcome) {
        toast?.close();
        toasts.current.delete(job.id);
        reportOutcome(notify, job);
        // The changed entities show in lists of any type (a relation shows on both sides) and in open fiches.
        void queryClient.invalidateQueries({ queryKey: queryKeys.entityLists() });
        void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetails() });
        acknowledgeBulkJob(job.id);
        return;
      }
      const shown = toast ?? notify.task(t(job.label));
      toasts.current.set(job.id, shown);
      shown.progress(job.progress.processed, job.progress.total);
    });
  }, [jobs, teller, notify, queryClient]);

  useEffect(() => {
    const shown = toasts.current;
    return () => {
      shown.forEach((toast) => toast.close());
      shown.clear();
    };
  }, [teller]);

  return null;
}
