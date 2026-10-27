import {apiFetch} from "../../api/client";
import {getEntityType} from "../../entities/registry";
import {entityRowLabel} from "../../fields/optionSources";

export type HierarchyZone = "parents" | "children";
export type StratigraphyZone = "anterior" | "synchronous" | "posterior";

export interface StratigraphyBody {
  typeConceptId?: number | null;
  uncertain?: boolean;
}

// POST /recording-units/{id}/parents|children — links an existing unit (204).
export function addHierarchyLink(id: string | number, zone: HierarchyZone, relatedId: string | number): Promise<void> {
  return apiFetch<void>(`/api/v1/recording-units/${id}/${zone}`, { method: "POST", body: { relatedRecordingUnitId: Number(relatedId) } });
}

// DELETE /recording-units/{id}/parents|children/{relatedId}.
export function removeHierarchyLink(id: string | number, zone: HierarchyZone, relatedId: string | number): Promise<void> {
  return apiFetch<void>(`/api/v1/recording-units/${id}/${zone}/${relatedId}`, { method: "DELETE" });
}

// POST /recording-units/{id}/stratigraphic-relationships — `position` is where the other unit stands
// relative to this one.
export function addStratigraphyLink(id: string | number, position: StratigraphyZone, relatedId: string | number, body: StratigraphyBody): Promise<void> {
  return apiFetch<void>(`/api/v1/recording-units/${id}/stratigraphic-relationships`, {
    method: "POST",
    body: { relatedRecordingUnitId: Number(relatedId), position, typeConceptId: body.typeConceptId ?? null, uncertain: !!body.uncertain },
  });
}

// PUT …/stratigraphic-relationships/{relatedId} — replaces the type and the uncertainty.
export function updateStratigraphyLink(id: string | number, relatedId: string | number, body: StratigraphyBody): Promise<void> {
  return apiFetch<void>(`/api/v1/recording-units/${id}/stratigraphic-relationships/${relatedId}`, {
    method: "PUT",
    body: { typeConceptId: body.typeConceptId ?? null, uncertain: !!body.uncertain },
  });
}

export function removeStratigraphyLink(id: string | number, relatedId: string | number): Promise<void> {
  return apiFetch<void>(`/api/v1/recording-units/${id}/stratigraphic-relationships/${relatedId}`, { method: "DELETE" });
}

export interface UnitOption {
  id: string;
  label: string;
}

interface UnitRowsBody {
  data: { id: string | number; fullIdentifier?: string | null; identifier?: string | null }[];
}

// The units of a project matching a search, for the picker of a unit to link (one page: past that the
// user narrows by typing).
export async function searchProjectUnits(projectId: string | number, q: string): Promise<UnitOption[]> {
  const query = new URLSearchParams({ offset: "0", limit: "20" });
  if (q) query.set("search", q);
  const body = await apiFetch<UnitRowsBody>(`/api/v1/projects/${projectId}/recording-units?${query.toString()}`);
  return body.data.map((row) => ({ id: String(row.id), label: row.fullIdentifier ?? row.identifier ?? String(row.id) }));
}

// A unit just created, as the picker shows it: nothing is linked yet, the user still confirms the link.
// The id stands in for the label when the unit can't be read back.
export async function createdUnitOption(id: string | number): Promise<UnitOption> {
  try {
    const row = await getEntityType("recordingUnit")!.api.get(id);
    return { id: String(id), label: entityRowLabel(row) };
  } catch {
    return { id: String(id), label: String(id) };
  }
}
