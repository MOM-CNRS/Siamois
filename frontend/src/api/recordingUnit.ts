import { apiFetch } from "./client";
import type { FieldAnswer } from "../form/schema";

export interface ResolvedConceptResource {
  resourceType: "concepts";
  id: string;
  externalUrl?: string | null;
  resolvedLabel?: string | null;
}

export interface PersonResource {
  id: string;
  username: string;
  name: string;
  lastname: string;
}

export type ValidationStatus = "INCOMPLETE" | "COMPLETE" | "VALIDATED";

export interface RecordingUnitResource {
  resourceType: "recording-units";
  id: string;
  syncRevision: number;
  identifier: string;
  fullIdentifier: string;
  projectId: string;
  type: ResolvedConceptResource | null;
  answers: Record<string, FieldAnswer>;
  validated: ValidationStatus;
  validatedAt: string | null;
  validatedBy: PersonResource | null;
  _counts?: { children: number | null; finds: number | null; parents: number | null; documents: number | null };
}

interface RecordingUnitResponse {
  data: RecordingUnitResource;
}

export function fetchRecordingUnit(id: string | number): Promise<RecordingUnitResource> {
  return apiFetch<RecordingUnitResponse>(`/recording-units/${id}`).then((r) => r.data);
}

export interface RecordingUnitPatchRequest {
  expectedRevision?: number;
  answers?: Record<string, { value?: unknown; values?: unknown[] }>;
  validated?: ValidationStatus;
}

export function patchRecordingUnit(
  id: string | number,
  patch: RecordingUnitPatchRequest,
): Promise<RecordingUnitResource> {
  return apiFetch<RecordingUnitResponse>(`/recording-units/${id}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  }).then((r) => r.data);
}

export interface RecordingUnitAdjacent {
  previousId: number | null;
  nextId: number | null;
}

export function fetchAdjacent(id: string | number): Promise<RecordingUnitAdjacent> {
  return apiFetch<RecordingUnitAdjacent>(`/recording-units/${id}/adjacent`);
}

export interface RecordingUnitHistory {
  lastRevisionAt: string | null;
  lastRevisionBy: PersonResource | null;
  contributors: PersonResource[];
}

export function fetchHistory(id: string | number): Promise<RecordingUnitHistory> {
  return apiFetch<RecordingUnitHistory>(`/recording-units/${id}/history`);
}

export function duplicateRecordingUnit(id: string | number): Promise<RecordingUnitResource> {
  return apiFetch<RecordingUnitResponse>(`/recording-units/${id}/duplicate`, { method: "POST" }).then(
    (r) => r.data,
  );
}

/** Next status in the JSF header's toggle cycle: INCOMPLETE -> COMPLETE -> VALIDATED -> INCOMPLETE. */
export function nextValidationStatus(current: ValidationStatus): ValidationStatus {
  switch (current) {
    case "INCOMPLETE":
      return "COMPLETE";
    case "COMPLETE":
      return "VALIDATED";
    case "VALIDATED":
      return "INCOMPLETE";
  }
}
