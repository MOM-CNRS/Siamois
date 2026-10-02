import { createEntityApi } from "../createEntityApi";
import type { DocumentDetail, DocumentSummary } from "./types";

// Mirrors DocumentCreateRequest's required pair (the title is editable afterwards, on the fiche).
export interface DocumentCreateBody {
  projectId: string;
  categoryId: string;
}

const api = createEntityApi<DocumentSummary, DocumentDetail, DocumentCreateBody>("documents");

export const listDocuments = api.list;
export const getDocument = api.get;
export const patchDocumentAnswers = api.patchAnswers;
export const createDocument = api.create;
