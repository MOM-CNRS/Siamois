import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../../api/queryKeys";
import {fetchMap, type MapParams, mapRequestOf} from "../../../entities/shared/listApi";
// Like a list's page: coming back to the map, or switching views, within a minute reuses what was loaded.
import {MAP_STALE_TIME} from "../../../map/useEntityFeature";

/**
 * The map of a list's result set, in the list's order, by a geometry column (`geometry`: the rows' own
 * geometry when absent, or an additional geometry field). Keyed on what selects and orders the rows, not
 * their page, so paging the list never refetches the map; anything that refreshes the list
 * refreshes it too (queryKeys.entityListMap sits under the list's prefix).
 */
export function useEntityMapData(entityType: string, collectionPath: string, params: MapParams, enabled = true, geometry?: string) {
  const request = mapRequestOf(params);
  return useQuery({
    queryKey: queryKeys.entityListMap(entityType, request, geometry),
    queryFn: () => fetchMap(collectionPath, request, geometry),
    staleTime: MAP_STALE_TIME,
    enabled,
  });
}
