// Mirrors fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationGraphResource —
// GET /api/v1/recording-units/{id}/relation-graph. The zones are the contract: nothing here says how
// a relation is stored.

export interface GraphQualifier {
  concept?: { resourceId?: string; label?: string | null } | null;
  uncertain?: boolean | null;
}

export interface GraphUnitRef {
  resourceId: string;
  resourceType: string;
  label?: string | null;
  qualifier?: GraphQualifier | null;
}

// A bounded preview of a zone: `total` says how many units there are in all.
export interface GraphZone {
  values: GraphUnitRef[];
  total: number;
  complete: boolean;
}

// A relation between two units of the graph, neither the center: from the more recent unit (or the parent) to the older one.
export interface GraphLink {
  from: string;
  to: string;
  kind: "hierarchy" | "stratigraphy" | "synchronous";
  type?: { resourceId?: string; label?: string | null } | null;
  uncertain?: boolean | null;
}

export interface RelationGraph {
  center: GraphUnitRef;
  parents: GraphZone;
  children: GraphZone;
  // Units older than the center (the center is posterior to them).
  anterior: GraphZone;
  synchronous: GraphZone;
  // Units more recent than the center (the center is anterior to them).
  posterior: GraphZone;
  // Configurable relations — empty until they exist.
  others: GraphZone;
  // The field of the fiche that holds each stratigraphic zone: its list gives the relation types.
  fields: Record<"anterior" | "synchronous" | "posterior", string>;
  // Only in the full graph: the relations between the units shown.
  links?: GraphLink[];
}

// The kinds of relation a unit has with another, as the list names them: the graph's zones but "others".
export type RelationKind = "parents" | "children" | "anterior" | "posterior" | "synchronous";

// One relation of the list view: the unit it is with, and where that unit stands.
export interface RelationRow {
  kind: RelationKind;
  unit: GraphUnitRef;
}

export interface RelationList {
  data: RelationRow[];
  meta: { total: number; limit: number; offset: number };
}

export type ZoneKey = "parents" | "children" | "anterior" | "synchronous" | "posterior" | "others";
