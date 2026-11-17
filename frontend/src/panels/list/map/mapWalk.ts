import {type MapFeature, type MapParams, mapRequestOf} from "../../../entities/shared/listApi";
import type {ListContext, ListRequest} from "../listContext";

/**
 * Where the overview's previous/next arrows start from when a fiche is opened on the map: its position
 * among the map's features, in the list request that yields exactly those rows in that order — the map's
 * result set and sort, narrowed like the map to the rows with a value in the geometry column drawn
 * (« not empty », unless the user already filters that column). Undefined when the map does not show it.
 */
export function mapWalkContext(
  params: MapParams,
  geometry: string | undefined,
  features: readonly MapFeature[],
  id: string | number,
): ListContext | undefined {
  const index = features.findIndex((f) => String(f.id) === String(id));
  return index < 0 ? undefined : { params: mapWalkRequest(params, geometry), index };
}

function mapWalkRequest(params: MapParams, geometry: string | undefined): ListRequest {
  const request = mapRequestOf(params);
  if (!geometry || request.filters?.[geometry] != null) return request;
  return { ...request, filters: { ...request.filters, [geometry]: { op: "notEmpty" } } };
}
