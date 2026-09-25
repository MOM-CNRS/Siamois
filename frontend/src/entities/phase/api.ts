import { apiFetch } from "../../api/client";
import { fetchList } from "../listApi";
import type { AnswerInputBody } from "../../fields/types";
import type { ListParams, PagedResult } from "../types";
import type { PhaseDetail, PhaseSummary } from "./types";

interface PhaseResponseBody {
  data: PhaseDetail;
}

// Unscoped today, same as listFinds/listRecordingUnits: nothing calls this without a `scope` —
// Phase is always viewed scoped to a project (GET /api/v1/projects/{id}/phases), there is no
// standalone "all phases" list in this app.
export async function listPhases(params: ListParams): Promise<PagedResult<PhaseSummary>> {
  return fetchList<PhaseSummary>("phases", params);
}

export async function getPhase(id: string | number): Promise<PhaseDetail> {
  const body = await apiFetch<PhaseResponseBody>(`/api/v1/phases/${id}`);
  return body.data;
}

// Mirrors PhasePatchRequest.answers — same by-field-id write path Project/RecordingUnit/Find's
// fiches use for their own autosaving fields.
export async function patchPhaseAnswers(
  id: string | number,
  answers: Record<string, AnswerInputBody>,
): Promise<PhaseDetail> {
  const body = await apiFetch<PhaseResponseBody>(`/api/v1/phases/${id}`, {
    method: "PATCH",
    body: { answers },
  });
  return body.data;
}

// Mirrors PhaseCreateRequest's required pair (projectId/typeId) — see CreateForm.tsx's own doc,
// same reduction as Project's/RecordingUnit's own create forms (title is accepted server-side
// too but left off this overlay, editable afterward on the fiche).
export interface PhaseCreateBody {
  projectId: string;
  typeId: string;
}

export async function createPhase(body: PhaseCreateBody): Promise<PhaseDetail> {
  const response = await apiFetch<PhaseResponseBody>("/api/v1/phases", {
    method: "POST",
    body: { projectId: body.projectId, typeId: body.typeId },
  });
  return response.data;
}
