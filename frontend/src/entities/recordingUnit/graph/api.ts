import { apiFetch } from "../../../api/client";
import type { RelationGraph } from "./types";

// GET /api/v1/recording-units/{id}/relation-graph — the unit's relations grouped by graph zone.
export async function getRelationGraph(id: string | number): Promise<RelationGraph> {
  const body = await apiFetch<RelationGraph | { data: RelationGraph }>(`/api/v1/recording-units/${id}/relation-graph`);
  return "data" in body ? body.data : body;
}
