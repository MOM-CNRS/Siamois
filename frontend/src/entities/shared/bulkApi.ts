import {apiFetch} from "../../api/client";

// The collections the server's bulk endpoints serve (BulkActionsControllerApi): their collection paths.
const BULK_COLLECTIONS = new Set([
  "recording-units",
  "finds",
  "phases",
  "containers",
  "studies",
  "documents",
  "places",
  "projects",
]);

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

interface BulkResultBody {
  data: BulkResult;
}

/**
 * Whether the server has bulk endpoints for the entities served under this collection path.
 */
export function supportsBulk(collectionPath: string): boolean {
  return BULK_COLLECTIONS.has(collectionPath);
}

/**
 * The numeric ids the bulk endpoints take.
 */
function numericIds(ids: readonly (string | number)[]): number[] {
  return ids.map(Number);
}

/**
 * POST /api/v1/{collectionPath}/bulk/delete — deletes each element as its unit DELETE would.
 */
export async function bulkDelete(collectionPath: string, ids: readonly (string | number)[]): Promise<BulkResult> {
  const body = await apiFetch<BulkResultBody>(`/api/v1/${collectionPath}/bulk/delete`, {
    method: "POST",
    body: { ids: numericIds(ids) },
  });
  return body.data;
}

/**
 * POST /api/v1/{collectionPath}/bulk/patch — applies the same unit PATCH body to each element.
 */
export async function bulkPatch(
  collectionPath: string,
  ids: readonly (string | number)[],
  patch: unknown,
): Promise<BulkResult> {
  const body = await apiFetch<BulkResultBody>(`/api/v1/${collectionPath}/bulk/patch`, {
    method: "POST",
    body: { ids: numericIds(ids), patch },
  });
  return body.data;
}

/**
 * POST /api/v1/{segment}/bulk/documents — links the document to each element, or unlinks it.
 */
export async function bulkLinkDocument(
  segment: string,
  ids: readonly (string | number)[],
  documentId: string | number,
  mode: BulkLinkMode,
): Promise<BulkResult> {
  const body = await apiFetch<BulkResultBody>(`/api/v1/${segment}/bulk/documents`, {
    method: "POST",
    body: { ids: numericIds(ids), documentId: Number(documentId), mode },
  });
  return body.data;
}
