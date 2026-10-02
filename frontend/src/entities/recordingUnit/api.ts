import { apiFetch } from "../../api/client";
import { createEntityApi } from "../createEntityApi";
import type { DuplicationNode, DuplicationResult, DuplicationStructure } from "../types";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./types";

export interface RecordingUnitCreateBody {
  projectId: string;
  typeId: string;
  // The new UE becomes a direct child of parentRecordingUnitId / the direct parent of
  // childRecordingUnitId — linked server-side in the same transaction as the creation.
  parentRecordingUnitId?: string | number;
  childRecordingUnitId?: string | number;
}

const api = createEntityApi<RecordingUnitSummary, RecordingUnitDetail, RecordingUnitCreateBody>("recording-units", {
  // The fiche's tab badges (contained RUs, finds) — not computed unless asked.
  getQuery: "counts=specimen,children,documents",
  toCreateBody: (body) => ({
    projectId: body.projectId,
    typeId: body.typeId,
    parentRecordingUnitId: body.parentRecordingUnitId != null ? Number(body.parentRecordingUnitId) : undefined,
    childRecordingUnitId: body.childRecordingUnitId != null ? Number(body.childRecordingUnitId) : undefined,
  }),
});

export const listRecordingUnits = api.list;
export const getRecordingUnit = api.get;
export const patchRecordingUnitAnswers = api.patchAnswers;
export const createRecordingUnit = api.create;
export const duplicateRecordingUnit = api.duplicate;

interface StructureBody {
  data: { root: DuplicationNode; descendants: DuplicationNode[]; truncated: boolean };
}

// GET /api/v1/recording-units/{id}/structure — the unit and its descendants, what "Dupliquer la
// structure" offers to include.
export async function getRecordingUnitStructure(id: string | number): Promise<DuplicationStructure> {
  const body = await apiFetch<StructureBody>(`/api/v1/recording-units/${id}/structure`);
  return body.data;
}

// POST /api/v1/recording-units/{id}/duplicate-structure — N exemplars of the unit and the chosen
// descendants, in one transaction.
export async function duplicateRecordingUnitStructure(
  id: string | number,
  options: { copies: number; descendantIds: (string | number)[] },
): Promise<DuplicationResult> {
  const body = await apiFetch<{ data: DuplicationResult }>(`/api/v1/recording-units/${id}/duplicate-structure`, {
    method: "POST",
    body: { copies: options.copies, descendantIds: options.descendantIds.map(Number) },
  });
  return body.data;
}

// Mirrors RecordingUnitCreateRequest's required pair (projectId/typeId — answers/geom both
// optional and unused here, see CreateForm.tsx's own doc for why the overlay stays this small).
