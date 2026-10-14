import { createEntityApi } from "../createEntityApi";
import type { StudyDetail, StudySummary } from "./types";

// Mirrors StudyCreateRequest's required pair.
export interface StudyCreateBody {
  projectId: string;
  typeId: string;
}

const api = createEntityApi<StudySummary, StudyDetail, StudyCreateBody>("studies");

export const listStudies = api.list;
export const getStudy = api.get;
export const patchStudyAnswers = api.patchAnswers;
export const patchStudy = api.patch;
export const createStudy = api.create;
