import { apiFetch } from "../../api/client";
import { fetchList } from "../listApi";
import type { AnswerInputBody } from "../../fields/types";
import type { ListParams, PagedResult } from "../types";
import type { ContainerDetail, ContainerSummary } from "./types";

interface ContainerResponseBody {
  data: ContainerDetail;
}

// Unscoped today, same as listPhases/listFinds: nothing calls this without a `scope` — Container
// is always viewed scoped to a project (GET /api/v1/projects/{id}/containers), there is no
// standalone "all containers" list in this app.
export async function listContainers(params: ListParams): Promise<PagedResult<ContainerSummary>> {
  return fetchList<ContainerSummary>("containers", params);
}

export async function getContainer(id: string | number): Promise<ContainerDetail> {
  const body = await apiFetch<ContainerResponseBody>(`/api/v1/containers/${id}`);
  return body.data;
}

// Mirrors ContainerPatchRequest.answers — same by-field-id write path Project/RecordingUnit/
// Find/Phase's fiches use for their own autosaving fields.
export async function patchContainerAnswers(
  id: string | number,
  answers: Record<string, AnswerInputBody>,
): Promise<ContainerDetail> {
  const body = await apiFetch<ContainerResponseBody>(`/api/v1/containers/${id}`, {
    method: "PATCH",
    body: { answers },
  });
  return body.data;
}

// Mirrors ContainerCreateRequest's required pair (projectId/typeId) — see CreateForm.tsx's own
// doc, same reduction as Project's/RecordingUnit's/Phase's own create forms.
export interface ContainerCreateBody {
  projectId: string;
  typeId: string;
}

export async function createContainer(body: ContainerCreateBody): Promise<ContainerDetail> {
  const response = await apiFetch<ContainerResponseBody>("/api/v1/containers", {
    method: "POST",
    body: { projectId: body.projectId, typeId: body.typeId },
  });
  return response.data;
}
