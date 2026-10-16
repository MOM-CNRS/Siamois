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
}

export type ZoneKey = "parents" | "children" | "anterior" | "synchronous" | "posterior" | "others";
