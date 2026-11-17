import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../../api/queryKeys";
import {fetchMap, type ResultSetParams, resultSetOf} from "../../../entities/shared/listApi";
// Like a list's page: coming back to the map, or switching views, within a minute reuses what was loaded.
import {MAP_STALE_TIME} from "../../../map/useEntityFeature";

/**
 * The map of a list's result set, by a geometry column (`geometry`: the rows' own geometry when
 * absent, or an additional geometry field). Keyed on what selects the rows only (not their order nor their
 * page), so sorting or paging the list never refetches the map; anything that refreshes the list
 * refreshes it too (queryKeys.entityListMap sits under the list's prefix).
 */
export function useEntityMapData(entityType: string, collectionPath: string, params: ResultSetParams, enabled = true, geometry?: string) {
  const resultSet = resultSetOf(params);
  return useQuery({
    queryKey: queryKeys.entityListMap(entityType, resultSet, geometry),
    queryFn: () => fetchMap(collectionPath, resultSet, geometry),
    staleTime: MAP_STALE_TIME,
    enabled,
  });
}
