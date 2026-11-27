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

// The stratigraphy is a pair of configurable relations (order, synchrony): a link is written through
// /relation-links, by the field it is entered from (-328 the anterior units, -329 the posterior ones,
// -330 the synchronous ones), which tells the relation and the side. The type is the one this unit
// reads; the server stores the posterior unit's reading.

// POST /relation-links — links `unitId` to `relatedId` through `fieldId`.
export function addStratigraphyLink(fieldId: string | number, unitId: string | number, relatedId: string | number, body: StratigraphyBody): Promise<void> {
  return apiFetch<void>("/api/v1/relation-links", {
    method: "POST",
    body: {
      field: Number(fieldId),
      record: { resourceType: "recording-units", resourceId: Number(unitId) },
      other: { resourceType: "recording-units", resourceId: Number(relatedId) },
      qualifier: body.typeConceptId ?? null,
      uncertain: !!body.uncertain,
    },
  });
}

// PATCH /relation-links/{linkId} — replaces the type and the uncertainty.
export function updateStratigraphyLink(fieldId: string | number, linkId: string | number, body: StratigraphyBody): Promise<void> {
  return apiFetch<void>(`/api/v1/relation-links/${linkId}`, {
    method: "PATCH",
    body: { field: Number(fieldId), qualifier: body.typeConceptId ?? null, uncertain: !!body.uncertain },
  });
}

// DELETE /relation-links/{linkId}?field= — the link, seen from both units.
export function removeStratigraphyLink(fieldId: string | number, linkId: string | number): Promise<void> {
  return apiFetch<void>(`/api/v1/relation-links/${linkId}?field=${encodeURIComponent(String(fieldId))}`, { method: "DELETE" });
}

// The link a stratigraphic value stands for: what update and remove act on.
export function linkIdOf(ref: { qualifier?: { linkId?: number | string | null } | null }): string {
  const linkId = ref.qualifier?.linkId;
  if (linkId == null) throw new Error("Ce lien n'a pas d'identifiant : rechargez la fiche");
  return String(linkId);
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
