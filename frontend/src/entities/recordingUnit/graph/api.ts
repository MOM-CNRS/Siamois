import { apiFetch } from "../../../api/client";
import type { RelationKind, RelationList } from "./types";
import type { RelationGraph } from "./types";

// GET /api/v1/recording-units/{id}/relation-graph — the unit's relations grouped by graph zone.
export async function getRelationGraph(id: string | number): Promise<RelationGraph> {
  const body = await apiFetch<RelationGraph | { data: RelationGraph }>(`/api/v1/recording-units/${id}/relation-graph`);
  return "data" in body ? body.data : body;
}

export interface RelationListParams {
  // The kinds to keep; none keeps them all.
  kinds: RelationKind[];
  search?: string;
  // "kind:asc", "kind:desc", "label:asc" or "label:desc".
  sort?: string;
  offset: number;
  limit: number;
}

// GET /api/v1/recording-units/{id}/relations — every relation of the unit, paged, each saying its kind.
export function getRelationList(id: string | number, params: RelationListParams): Promise<RelationList> {
  const query = new URLSearchParams({ offset: String(params.offset), limit: String(params.limit) });
  for (const kind of params.kinds) query.append("kind", kind);
  if (params.search) query.set("search", params.search);
  if (params.sort) query.set("sort", params.sort);
  return apiFetch<RelationList>(`/api/v1/recording-units/${id}/relations?${query.toString()}`);
}
