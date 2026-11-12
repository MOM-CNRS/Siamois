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
}

/** How far a bulk action running in the background has got. */
export interface BulkProgress {
  processed: number;
  total: number;
}

/** Hears the progress of a bulk action while the server processes it. */
export type OnBulkProgress = (progress: BulkProgress) => void;

/** Whether a bulk document action links the document to every element, or removes that link. */
export type BulkLinkMode = "LINK" | "UNLINK";

type Ids = readonly (string | number)[];

/**
 * The bulk actions an entity type offers on a list's selection (EntityTypeConfig.api.bulk). Each one
 * starts a job on the server's bulk endpoints (BulkActionsControllerApi), which processes the elements
 * in the background; the promise follows the job and resolves with its result once it is done.
 */
export interface BulkApi {
  // Deletes each element as its unit DELETE would.
  delete(ids: Ids, onProgress?: OnBulkProgress): Promise<BulkResult>;
  // Applies the same unit PATCH body to each element.
  patch(ids: Ids, patch: unknown, onProgress?: OnBulkProgress): Promise<BulkResult>;
  // Links a document to each element, or unlinks it. Absent: documents can't be linked to this type.
  linkDocument?(ids: Ids, documentId: string | number, mode: BulkLinkMode, onProgress?: OnBulkProgress): Promise<BulkResult>;
}

export interface BulkApiOptions {
  // Documents can be linked to this type (it has a Documents tab): the selection bar offers « Documents ».
  linksDocuments?: boolean;
}

// A bulk action as the server reports it (BulkJobResource).
interface BulkJob extends BulkResult {
  id: string;
  status: "RUNNING" | "DONE" | "FAILED";
  total: number;
  processed: number;
  error?: string | null;
}

interface BulkJobBody {
  data: BulkJob;
}

// The most elements one bulk request may carry (BulkActionOpenApiService.MAX_IDS): a larger selection
// goes in several jobs, one after the other.
export const BULK_CHUNK_SIZE = 10_000;

// How often a running job is asked for its progress.
export const BULK_POLL_INTERVAL_MS = 500;

/**
 * The numeric ids the bulk endpoints take.
 */
function numericIds(ids: Ids): number[] {
  return ids.map(Number);
}

/**
 * Waits before the next look at a running job.
 */
function pause(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, BULK_POLL_INTERVAL_MS));
}

/**
 * Follows a job until the server has processed every element, reporting its progress on the way;
 * rejects when the job stopped on an error.
 */
async function followJob(started: BulkJob, onProgress: (job: BulkJob) => void): Promise<BulkResult> {
  let job = started;
  onProgress(job);
  while (job.status === "RUNNING") {
    await pause();
    job = (await apiFetch<BulkJobBody>(`/api/v1/bulk/jobs/${encodeURIComponent(job.id)}`)).data;
    onProgress(job);
  }
  if (job.status === "FAILED") throw new Error(job.error ?? "");
  return { succeeded: job.succeeded, failed: job.failed };
}

/**
 * Sends the ids in jobs of at most {@link BULK_CHUNK_SIZE}, one after the other, follows each until
 * done, and adds up their results; the progress counts over the whole selection.
 */
async function inJobs(ids: Ids, start: (chunk: number[]) => Promise<BulkJob>, onProgress?: OnBulkProgress): Promise<BulkResult> {
  const all = numericIds(ids);
  const total: BulkResult = { succeeded: [], failed: [] };
  for (let offset = 0; offset < all.length; offset += BULK_CHUNK_SIZE) {
    const job = await start(all.slice(offset, offset + BULK_CHUNK_SIZE));
    const result = await followJob(job, (current) => onProgress?.({ processed: offset + current.processed, total: all.length }));
    total.succeeded.push(...result.succeeded);
    total.failed.push(...result.failed);
  }
  return total;
}

/**
 * POSTs a bulk request: the server answers 202 with the job it started.
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
    delete: (ids, onProgress) => inJobs(ids, (chunk) => startJob(`${collectionPath}/delete`, { ids: chunk }), onProgress),
    patch: (ids, patch, onProgress) =>
      inJobs(ids, (chunk) => startJob(`${collectionPath}/patch`, { ids: chunk, patch }), onProgress),
  };
  if (!options.linksDocuments) return api;
  return {
    ...api,
    linkDocument: (ids, documentId, mode, onProgress) =>
      inJobs(
        ids,
        (chunk) => startJob(`${collectionPath}/documents`, { ids: chunk, documentId: Number(documentId), mode }),
        onProgress,
      ),
  };
}
