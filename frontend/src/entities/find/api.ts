import { apiFetch } from "../../api/client";
import { fetchList } from "../listApi";
import type { AnswerInputBody } from "../../fields/types";
import type { ListParams, PagedResult } from "../types";
import type { FindDetail, FindSummary } from "./types";

interface FindResponseBody {
  data: FindDetail;
}

// Unscoped today, same as listRecordingUnits: nothing calls this without a `scope` — Find is
// always viewed scoped to a project (GET /api/v1/projects/{id}/mobiliers), there is no standalone
// "all mobiliers" list in this app. Kept for EntityTypeConfig.api.list's own contract and so
// relationTab's generic scoping mechanism has something to call.
export async function listFinds(params: ListParams): Promise<PagedResult<FindSummary>> {
  return fetchList<FindSummary>("finds", params);
}

export async function getFind(id: string | number): Promise<FindDetail> {
  const body = await apiFetch<FindResponseBody>(`/api/v1/finds/${id}`);
  return body.data;
}

// Mirrors FindCreateRequest's required pair — recordingUnitId (a mobilier is created ON a
// recording unit, never directly on a project; see CreateForm.tsx's own doc for why its overlay,
// scoped by PROJECT like the rest of this tab, still needs its own recording-unit picker) and
// typeId (answers unused here, same reduction as Project's/RecordingUnit's own create forms).
export interface FindCreateBody {
  recordingUnitId: string;
  typeId: string;
}

export async function createFind(body: FindCreateBody): Promise<FindDetail> {
  const response = await apiFetch<FindResponseBody>("/api/v1/finds", {
    method: "POST",
    body: { recordingUnitId: body.recordingUnitId, typeId: body.typeId },
  });
  return response.data;
}

// Mirrors FindPatchRequest.answers — same by-field-id write path Project/RecordingUnit's fiches
// use for their own autosaving fields.
export async function patchFindAnswers(
  id: string | number,
  answers: Record<string, AnswerInputBody>,
): Promise<FindDetail> {
  const body = await apiFetch<FindResponseBody>(`/api/v1/finds/${id}`, {
    method: "PATCH",
    body: { answers },
  });
  return body.data;
}
