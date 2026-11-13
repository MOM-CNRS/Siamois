import {apiFetch} from "../../api/client";

/** One element a bulk action could not change: the status its unit call would have answered, and why. */
export interface BulkFailure {
  id: number;
  status: number;
  message: string;
}

/** What a bulk action did: each element is changed (or refused) on its own, in its own transaction. */
export interface BulkResult {
  succeeded: number[];
  failed: BulkFailure[];
  // Its owner stopped it before the end: the elements processed until then stay changed.
  cancelled?: boolean;
}

/** How far a bulk action running in the background has got. */
export interface BulkProgress {
  processed: number;
  total: number;
}

/** Hears the progress of a bulk action while the server processes it. */
export type OnBulkProgress = (progress: BulkProgress) => void;

/** A bulk action the server started: what to follow it by. */
export interface BulkJobRef {
  id: string;
  total: number;
}

/** Whether a bulk document action links the document to every element, or removes that link. */
export type BulkLinkMode = "LINK" | "UNLINK";

type Ids = readonly (string | number)[];

/**
 * The bulk actions an entity type offers on a list's selection (EntityTypeConfig.api.bulk). Each one
 * starts one job (several for a selection past {@link BULK_CHUNK_SIZE}) on the server's bulk endpoints
 * (BulkActionsControllerApi), which processes the elements in the background: follow it with
 * {@link followBulkJob}.
 */
export interface BulkApi {
  // Deletes each element as its unit DELETE would.
  delete(ids: Ids): Promise<BulkJobRef[]>;
  // Applies the same unit PATCH body to each element.
  patch(ids: Ids, patch: unknown): Promise<BulkJobRef[]>;
  // Links a document to each element, or unlinks it. Absent: documents can't be linked to this type.
  linkDocument?(ids: Ids, documentId: string | number, mode: BulkLinkMode): Promise<BulkJobRef[]>;
  // Adds the same stratigraphic relationship to each element, or removes it. Absent: no stratigraphy.
  linkStratigraphy?(ids: Ids, relationship: BulkStratigraphy, mode: BulkLinkMode): Promise<BulkJobRef[]>;
}

/** A stratigraphic relationship every element of a bulk action gets towards the same unit. */
export interface BulkStratigraphy {
  relatedRecordingUnitId: string | number;
  // Where the other unit stands relative to each element.
  position: "anterior" | "posterior" | "synchronous";
  typeConceptId?: string | number | null;
  uncertain?: boolean;
}

export interface BulkApiOptions {
  // Documents can be linked to this type (it has a Documents tab): « Documents » is among its relations.
  linksDocuments?: boolean;
  // Its elements have stratigraphic relationships (recording units): its stratigraphy fields are among its relations.
  linksStratigraphy?: boolean;
}

// A bulk action as the server reports it (BulkJobResource): the lists only once it has ended.
interface BulkJob extends BulkResult {
  id: string;
  status: "RUNNING" | "DONE" | "FAILED" | "CANCELLED";
  total: number;
  processed: number;
  error?: string | null;
}

interface BulkJobBody {
  data: BulkJob;
}

// The most elements one bulk request may carry (BulkActionOpenApiService.MAX_IDS): a larger selection
// goes in several jobs.
export const BULK_CHUNK_SIZE = 10_000;

// How long to wait before asking a running job for its progress: short at first (a small action is
// done at once), then longer and longer up to the maximum, so a long action costs few requests.
export const BULK_POLL_FIRST_MS = 500;
export const BULK_POLL_MAX_MS = 3000;
const BULK_POLL_GROWTH = 1.5;

/**
 * The numeric ids the bulk endpoints take.
 */
function numericIds(ids: Ids): number[] {
  return ids.map(Number);
}

/**
 * Waits `ms` milliseconds.
 */
function pause(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Follows a job until the server has processed every element, reporting its progress on the way
 * (asking less and less often), and resolves with what it did; rejects when the job stopped on an
 * error, or can't be read (forgotten, someone else's: 404).
 */
export async function followBulkJob(jobId: string, onProgress?: OnBulkProgress): Promise<BulkResult> {
  let delay = BULK_POLL_FIRST_MS;
  for (;;) {
    const job = (await apiFetch<BulkJobBody>(`/api/v1/bulk/jobs/${encodeURIComponent(jobId)}`)).data;
    onProgress?.({ processed: job.processed, total: job.total });
    if (job.status === "FAILED") throw new Error(job.error ?? "");
    if (job.status !== "RUNNING") {
      return { succeeded: job.succeeded, failed: job.failed, ...(job.status === "CANCELLED" ? { cancelled: true } : {}) };
    }
    await pause(delay);
    delay = Math.min(BULK_POLL_MAX_MS, Math.round(delay * BULK_POLL_GROWTH));
  }
}

/**
 * POST /api/v1/bulk/jobs/{id}/cancel — asks to stop a job the caller started: it stops before its next
 * element, wherever it runs.
 */
export async function cancelBulkJob(jobId: string): Promise<void> {
  await apiFetch<unknown>(`/api/v1/bulk/jobs/${encodeURIComponent(jobId)}/cancel`, { method: "POST" });
}

/**
 * Starts the jobs of a selection: one per {@link BULK_CHUNK_SIZE} ids, one after the other.
 */
async function startJobs(ids: Ids, start: (chunk: number[]) => Promise<BulkJob>): Promise<BulkJobRef[]> {
  const all = numericIds(ids);
  const refs: BulkJobRef[] = [];
  for (let offset = 0; offset < all.length; offset += BULK_CHUNK_SIZE) {
    const job = await start(all.slice(offset, offset + BULK_CHUNK_SIZE));
    refs.push({ id: job.id, total: job.total });
  }
  return refs;
}

/**
 * POSTs a bulk request: the server checks it, then answers 202 with the job it started.
 */
async function startJob(path: string, body: unknown): Promise<BulkJob> {
  return (await apiFetch<BulkJobBody>(`/api/v1/bulk/${path}`, { method: "POST", body })).data;
}

/**
 * The bulk actions of the entities served under `/api/v1/{collectionPath}`: what an entity type
 * declares as its `api.bulk` when the server has bulk operations for it.
 */
export function createBulkApi(collectionPath: string, options: BulkApiOptions = {}): BulkApi {
  const api: BulkApi = {
    delete: (ids) => startJobs(ids, (chunk) => startJob(`${collectionPath}/delete`, { ids: chunk })),
    patch: (ids, patch) => startJobs(ids, (chunk) => startJob(`${collectionPath}/patch`, { ids: chunk, patch })),
  };
  return {
    ...api,
    ...(options.linksDocuments
      ? {
          linkDocument: (ids: Ids, documentId: string | number, mode: BulkLinkMode) =>
            startJobs(ids, (chunk) => startJob(`${collectionPath}/documents`, { ids: chunk, documentId: Number(documentId), mode })),
        }
      : {}),
    ...(options.linksStratigraphy
      ? {
          linkStratigraphy: (ids: Ids, relationship: BulkStratigraphy, mode: BulkLinkMode) =>
            startJobs(ids, (chunk) =>
              startJob(`${collectionPath}/stratigraphy`, {
                ids: chunk,
                relatedRecordingUnitId: Number(relationship.relatedRecordingUnitId),
                position: relationship.position,
                typeConceptId: relationship.typeConceptId == null ? null : Number(relationship.typeConceptId),
                uncertain: relationship.uncertain === true,
                mode,
              }),
            ),
        }
      : {}),
  };
}
