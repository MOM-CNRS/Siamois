import { createEntityApi } from "../createEntityApi";
import type { DocumentDetail, DocumentSummary } from "./types";
import { apiBlob, apiFetch } from "../../api/client";

// Mirrors DocumentCreateRequest's required pair (the title is editable afterwards, on the fiche).
export interface DocumentCreateBody {
  projectId: string;
  typeId: string;
  // What a Documents tab creates it linked to (the one entity's id, under its own list).
  recordingUnitIds?: Array<string | number>;
  findIds?: Array<string | number>;
  placeIds?: Array<string | number>;
  phaseIds?: Array<string | number>;
  containerIds?: Array<string | number>;
}

const api = createEntityApi<DocumentSummary, DocumentDetail, DocumentCreateBody>("documents");

export const listDocuments = api.list;
export const getDocument = api.get;
export const patchDocumentAnswers = api.patchAnswers;
export const patchDocument = api.patch;
export const createDocument = api.create;

// The stored file: sent and removed apart from the form's answers (its bytes cannot travel in a PATCH).
export function uploadDocumentFile(id: string | number, file: File): Promise<unknown> {
  const body = new FormData();
  body.append("file", file);
  return apiFetch(`/api/v1/documents/${id}/file`, { method: "PUT", body });
}

export function removeDocumentFile(id: string | number): Promise<unknown> {
  return apiFetch(`/api/v1/documents/${id}/file`, { method: "DELETE" });
}

export function fetchDocumentFile(id: string | number, download = false): Promise<Blob> {
  return apiBlob(`/api/v1/documents/${id}/file${download ? "?download=true" : ""}`);
}

// A document's link to an entity — a recording unit, a find, a place, a phase or a container —
// under the entity's own REST segment. Both are idempotent.
export function linkDocument(segment: string, entityId: string | number, documentId: string | number): Promise<unknown> {
  return apiFetch(`/api/v1/${segment}/${entityId}/documents/${documentId}`, { method: "PUT" });
}

export function unlinkDocument(segment: string, entityId: string | number, documentId: string | number): Promise<unknown> {
  return apiFetch(`/api/v1/${segment}/${entityId}/documents/${documentId}`, { method: "DELETE" });
}
