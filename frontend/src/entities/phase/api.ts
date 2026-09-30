import { createEntityApi } from "../createEntityApi";
import type { PhaseDetail, PhaseSummary } from "./types";

// Mirrors PhaseCreateRequest's required pair (the title is editable afterwards, on the fiche).
export interface PhaseCreateBody {
  projectId: string;
  typeId: string;
}

const api = createEntityApi<PhaseSummary, PhaseDetail, PhaseCreateBody>("phases");

export const listPhases = api.list;
export const getPhase = api.get;
export const patchPhaseAnswers = api.patchAnswers;
export const createPhase = api.create;
