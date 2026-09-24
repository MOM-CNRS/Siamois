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
  // counts: the fiche's tab badges (contained RUs, finds) — not computed unless asked.
  const body = await apiFetch<RecordingUnitResponseBody>(`/api/v1/recording-units/${id}?counts=specimen,children`);
  return body.data;
}

// POST /api/v1/recording-units/{id}/duplicate — same copy as JSF's RecordingUnitPanel.duplicate().
export async function duplicateRecordingUnit(id: string | number): Promise<RecordingUnitDetail> {
  const response = await apiFetch<RecordingUnitResponseBody>(`/api/v1/recording-units/${id}/duplicate`, {
    method: "POST",
  });
  return response.data;
}

// Mirrors RecordingUnitCreateRequest's required pair (projectId/typeId — answers/geom both
// optional and unused here, see CreateForm.tsx's own doc for why the overlay stays this small).
export interface RecordingUnitCreateBody {
  projectId: string;
  typeId: string;
  // The new UE becomes a direct child of parentRecordingUnitId / the direct parent of
  // childRecordingUnitId — linked server-side in the same transaction as the creation.
  parentRecordingUnitId?: string | number;
  childRecordingUnitId?: string | number;
}

export async function createRecordingUnit(body: RecordingUnitCreateBody): Promise<RecordingUnitDetail> {
  const response = await apiFetch<RecordingUnitResponseBody>("/api/v1/recording-units", {
    method: "POST",
    body: {
      projectId: body.projectId,
      typeId: body.typeId,
      parentRecordingUnitId: body.parentRecordingUnitId != null ? Number(body.parentRecordingUnitId) : undefined,
      childRecordingUnitId: body.childRecordingUnitId != null ? Number(body.childRecordingUnitId) : undefined,
    },
  });
  return response.data;
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
