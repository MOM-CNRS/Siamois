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
  return addRelationLink(fieldId, { collection: "recording-units", id: unitId }, { collection: "recording-units", id: relatedId }, body);
}

// A record at one end of a link: its API collection and its id.
export interface RecordEnd {
  collection: string;
  id: string | number;
}

// POST /relation-links — links the record being edited to another, of any kind, through `fieldId`.
export function addRelationLink(fieldId: string | number, record: RecordEnd, other: RecordEnd, body: StratigraphyBody): Promise<void> {
  return apiFetch<void>("/api/v1/relation-links", {
    method: "POST",
    body: {
      field: Number(fieldId),
      record: { resourceType: record.collection, resourceId: Number(record.id) },
      other: { resourceType: other.collection, resourceId: Number(other.id) },
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

// The API collection of each kind of record a relation can link, by registry key, and back.
export const RELATION_COLLECTIONS: Record<string, string> = {
  recordingUnit: "recording-units",
  find: "finds",
  phase: "phases",
  container: "containers",
  document: "documents",
  study: "studies",
  project: "projects",
};

export const entityKeyOfCollection = (collection: string): string | undefined =>
  Object.entries(RELATION_COLLECTIONS).find(([, c]) => c === collection)?.[0];

// What a relation field is in a project (GET /relation-fields/{fieldId}): the records it links, whether a
// record has only one link through it, and its qualifiers as it shows them.
export interface RelationFieldDescription {
  field: number;
  side: "source" | "target";
  mirrorField: number;
  singleChoice: boolean;
  accepts: { resourceType: string; subtype?: number | null }[];
  qualifiers: { shown: number; stored: number }[];
  noCycle: boolean;
}

export function getRelationField(fieldId: string | number, projectId: string | number): Promise<RelationFieldDescription> {
  return apiFetch<RelationFieldDescription>(`/api/v1/relation-fields/${fieldId}?projectId=${encodeURIComponent(String(projectId))}`);
}

interface RecordRowsBody {
  data: { id: string | number; fullIdentifier?: string | null; identifier?: string | null; title?: string | null; name?: string | null }[];
}

// The records of one kind of a project matching a search, for the picker of a record to link (one page).
export async function searchProjectRecords(collection: string, projectId: string | number, q: string): Promise<UnitOption[]> {
  const query = new URLSearchParams({ offset: "0", limit: "20" });
  if (q) query.set("search", q);
  const body = await apiFetch<RecordRowsBody>(`/api/v1/projects/${projectId}/${collection}?${query.toString()}`);
  return body.data.map((row) => ({ id: String(row.id), label: row.fullIdentifier ?? row.title ?? row.name ?? row.identifier ?? String(row.id) }));
}
