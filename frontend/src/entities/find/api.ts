import { createEntityApi } from "../createEntityApi";
import type { FindDetail, FindSummary } from "./types";

// Mirrors FindCreateRequest's required pair: a mobilier is created ON a recording unit, never
// directly on a project.
export interface FindCreateBody {
  recordingUnitId: string;
  typeId: string;
}

const api = createEntityApi<FindSummary, FindDetail, FindCreateBody>("finds");

export const listFinds = api.list;
export const getFind = api.get;
export const patchFindAnswers = api.patchAnswers;
export const createFind = api.create;
// POST /api/v1/finds/{id}/duplicate — a copy on the same recording unit, with a new identifier.
export const duplicateFind = api.duplicate;
