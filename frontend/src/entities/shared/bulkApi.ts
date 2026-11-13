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

/** Whether a bulk document action links the document to every element, or removes that link. */
export type BulkLinkMode = "LINK" | "UNLINK";

type Ids = readonly (string | number)[];

/**
 * The bulk actions an entity type offers on a list's selection (EntityTypeConfig.api.bulk) — each
 * one request to the server's bulk endpoints (BulkActionsControllerApi).
 */
export interface BulkApi {
  // Deletes each element as its unit DELETE would.
  delete(ids: Ids): Promise<BulkResult>;
  // Applies the same unit PATCH body to each element.
  patch(ids: Ids, patch: unknown): Promise<BulkResult>;
  // Links a document to each element, or unlinks it. Absent: documents can't be linked to this type.
  linkDocument?(ids: Ids, documentId: string | number, mode: BulkLinkMode): Promise<BulkResult>;
}

export interface BulkApiOptions {
  // Documents can be linked to this type (it has a Documents tab): the selection bar offers « Documents ».
  linksDocuments?: boolean;
}

interface BulkResultBody {
  data: BulkResult;
}

// The most elements one bulk request may carry (BulkActionOpenApiService.MAX_IDS): a larger selection
// goes in several requests.
export const BULK_CHUNK_SIZE = 1000;

/**
 * The numeric ids the bulk endpoints take.
 */
function numericIds(ids: Ids): number[] {
  return ids.map(Number);
}

/**
 * POSTs a bulk request and unwraps its result.
 */
async function postBulk(path: string, body: unknown): Promise<BulkResult> {
  return (await apiFetch<BulkResultBody>(`/api/v1/bulk/${path}`, { method: "POST", body })).data;
}

/**
 * Sends the ids in requests of at most {@link BULK_CHUNK_SIZE}, one after the other, and adds up
 * their results.
 */
async function inChunks(ids: Ids, send: (chunk: number[]) => Promise<BulkResult>): Promise<BulkResult> {
  const all = numericIds(ids);
  const total: BulkResult = { succeeded: [], failed: [] };
  for (let start = 0; start < all.length; start += BULK_CHUNK_SIZE) {
    const result = await send(all.slice(start, start + BULK_CHUNK_SIZE));
    total.succeeded.push(...result.succeeded);
    total.failed.push(...result.failed);
  }
  return total;
}

/**
 * The bulk actions of the entities served under `/api/v1/{collectionPath}`: what an entity type
 * declares as its `api.bulk` when the server has bulk operations for it.
 */
export function createBulkApi(collectionPath: string, options: BulkApiOptions = {}): BulkApi {
  const api: BulkApi = {
    delete: (ids) => inChunks(ids, (chunk) => postBulk(`${collectionPath}/delete`, { ids: chunk })),
    patch: (ids, patch) => inChunks(ids, (chunk) => postBulk(`${collectionPath}/patch`, { ids: chunk, patch })),
  };
  if (!options.linksDocuments) return api;
  return {
    ...api,
    linkDocument: (ids, documentId, mode) =>
      inChunks(ids, (chunk) =>
        postBulk(`${collectionPath}/documents`, { ids: chunk, documentId: Number(documentId), mode }),
      ),
  };
}
