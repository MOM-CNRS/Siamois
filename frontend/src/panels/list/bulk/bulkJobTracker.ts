import {type BulkProgress, type BulkResult, followBulkJob} from "../../../entities/shared/bulkApi";
import type {MessageKey} from "../../../i18n";

// Where the bulk jobs being followed are remembered, for the tab: a page change (JSF re-renders the
// page, the React tree is mounted afresh) picks them up again.
const STORAGE_KEY = "siamois.bulkJobs";

/** What a finished bulk job is reported as, with how many entities it changed. */
export type BulkSuccessKey = "bulk.editDone" | "bulk.linksDone" | "bulk.deleteDone";

/** A bulk job to follow: what it is called while it runs, and how its success is told. */
export interface BulkJobEntry {
  id: string;
  label: MessageKey;
  successKey: BulkSuccessKey;
}

/** A bulk job being followed: its progress and, once it has ended, its outcome. */
export interface TrackedBulkJob extends BulkJobEntry {
  progress: BulkProgress;
  outcome?: { result: BulkResult } | { error: unknown };
}

const jobs = new Map<string, TrackedBulkJob>();
const following = new Map<string, Promise<BulkResult>>();
const listeners = new Set<() => void>();
let snapshot: readonly TrackedBulkJob[] = [];

/**
 * Tells the subscribers the jobs changed.
 */
function emit() {
  snapshot = [...jobs.values()];
  listeners.forEach((listener) => listener());
}

/**
 * The jobs remembered for this tab (none when the storage can't be read).
 */
function readStored(): BulkJobEntry[] {
  try {
    const stored: unknown = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) ?? "[]");
    return Array.isArray(stored) ? (stored as BulkJobEntry[]) : [];
  } catch {
    return [];
  }
}

/**
 * Remembers the jobs not yet reported, for the next page of this tab.
 */
function persist() {
  try {
    const entries = [...jobs.values()].map(({ id, label, successKey }) => ({ id, label, successKey }));
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  } catch {
    // No storage (private window, blocked site data): the job is followed on this page only.
  }
}

/**
 * Updates a followed job.
 */
function update(id: string, change: Partial<TrackedBulkJob>) {
  const job = jobs.get(id);
  if (!job) return;
  jobs.set(id, { ...job, ...change });
  emit();
}

/**
 * Follows a bulk job — once per page, whoever asks — and resolves with what it did. Remembered for
 * the tab until its outcome has been told ({@link acknowledgeBulkJob}), so a page change goes on
 * following it.
 */
export function trackBulkJob(entry: BulkJobEntry, total = 0): Promise<BulkResult> {
  const known = following.get(entry.id);
  if (known) return known;
  jobs.set(entry.id, { ...entry, progress: { processed: 0, total } });
  persist();
  emit();
  const promise = followBulkJob(entry.id, (progress) => update(entry.id, { progress }));
  promise.then(
    (result) => update(entry.id, { outcome: { result } }),
    (error: unknown) => update(entry.id, { outcome: { error } }),
  );
  following.set(entry.id, promise);
  return promise;
}

/**
 * Goes on following the jobs a previous page of this tab started and did not report.
 */
export function resumeBulkJobs(): void {
  readStored().forEach((entry) => {
    trackBulkJob(entry).catch(() => {
      // Its outcome is told by whoever reports the jobs (BulkJobNotifier).
    });
  });
}

/**
 * The outcome of the job has been told: it is no longer followed nor remembered.
 */
export function acknowledgeBulkJob(id: string): void {
  jobs.delete(id);
  following.delete(id);
  persist();
  emit();
}

/**
 * The jobs being followed, as they are now (the same array until they change).
 */
export function bulkJobsSnapshot(): readonly TrackedBulkJob[] {
  return snapshot;
}

/**
 * Hears every change of the jobs being followed.
 */
export function subscribeBulkJobs(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
