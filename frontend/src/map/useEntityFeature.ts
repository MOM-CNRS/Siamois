import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../api/queryKeys";
import {getEntityType} from "../entities/registry";
import {fetchMapFeature, type MapFeature} from "../entities/shared/listApi";

// Like a list's page and its map: a fiche reopened within a minute reuses its geometry.
export const MAP_STALE_TIME = 60_000;

/**
 * One entity's geometry in WGS84 (GET .../{collection}/{id}/map), for an entity type whose config is
 * `mappable` — its own, or by `geometry` its answer to an additional geometry field. Cached under the entity's detail (queryKeys.entityMapFeature): the list map's selection
 * and the fiche's geometry field share it, and editing the fiche refreshes it. `feature` is null when
 * the entity has no placeable geometry, undefined until known.
 */
export function useEntityFeature(
  entityType: string,
  id: string | number | undefined,
  enabled = true,
  geometry?: string,
): { feature: MapFeature | null | undefined; isLoading: boolean } {
  const collectionPath = getEntityType(entityType)?.collectionPath;
  const { data, isLoading } = useQuery({
    queryKey: queryKeys.entityMapFeature(entityType, id ?? "", geometry),
    queryFn: () => fetchMapFeature(collectionPath!, id!, geometry),
    enabled: enabled && id != null && isMappable(entityType),
    staleTime: MAP_STALE_TIME,
  });
  return { feature: data, isLoading };
}

/** Whether an entity type can be placed on a map (its config's `list.mappable`). */
export function isMappable(entityType: string | undefined): boolean {
  return entityType != null && getEntityType(entityType)?.list.mappable === true;
}
