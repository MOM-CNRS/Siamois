import { apiFetch } from "../../api/client";
import { fetchList } from "../listApi";
import type { AnswerInputBody } from "../../fields/types";
import type { ListParams, PagedResult } from "../types";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./types";

interface RecordingUnitResponseBody {
  data: RecordingUnitDetail;
}

// Unscoped today: nothing calls this without a `scope` (there is no standalone "all recording
// units" list in this app yet — RecordingUnit is always viewed scoped to a project). Kept for
// EntityTypeConfig.api.list's own contract and so relationTab's generic scoping mechanism has
// something to call.
export async function listRecordingUnits(params: ListParams): Promise<PagedResult<RecordingUnitSummary>> {
  return fetchList<RecordingUnitSummary>("recording-units", params);
}

export async function getRecordingUnit(id: string | number): Promise<RecordingUnitDetail> {
  const body = await apiFetch<RecordingUnitResponseBody>(`/api/v1/recording-units/${id}`);
  return body.data;
}

// Mirrors RecordingUnitPatchRequest.answers — the same by-field-id write path the list's
// click-to-edit overlay (CellEditOverlay) and the fiche's autosaving fields use for Project.
export async function patchRecordingUnitAnswers(
  id: string | number,
  answers: Record<string, AnswerInputBody>,
): Promise<RecordingUnitDetail> {
  const body = await apiFetch<RecordingUnitResponseBody>(`/api/v1/recording-units/${id}`, {
    method: "PATCH",
    body: { answers },
  });
  return body.data;
}
