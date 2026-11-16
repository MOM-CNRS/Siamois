import {type QueryClient, useQuery, useQueryClient} from "@tanstack/react-query";
import {queryKeys} from "../api/queryKeys";
import {PROJECT_KEY} from "../entities/keys";
import {getEntityType} from "../entities/registry";
import {fetchCommuneContour} from "./communeContour";
import {type FocusLoaders, type FocusSource, type PlaceLike, type ProjectLike, resolveDrawingFocus} from "./drawingFocus";
import {MAP_STALE_TIME} from "./useEntityFeature";

const PLACE_KEY = "place";

/**
 * The area to frame the map drawing an entity's geometry on (drawingFocus): resolved in the browser
 * from the place and project fiches — read through their own cached detail queries, so an open fiche
 * costs no call — and the communes' outlines asked to geo.api.gouv.fr. Null when nothing can be
 * located, undefined until known.
 */
export function useDrawingFocus(source: FocusSource, enabled: boolean): GeoJSON.Geometry | null | undefined {
  const queryClient = useQueryClient();
  const { data } = useQuery({
    queryKey: queryKeys.drawingFocus(source.placeIds, source.projectId),
    queryFn: () => resolveDrawingFocus(source, loadersOf(queryClient)),
    enabled: enabled && (source.placeIds.length > 0 || source.projectId != null),
    staleTime: MAP_STALE_TIME,
  });
  return data;
}

// The fiches through the entity registry (the same queries their own panels read), the outlines once each.
function loadersOf(queryClient: QueryClient): FocusLoaders {
  const detail = <T,>(entityType: string, id: string) =>
    queryClient.fetchQuery({
      queryKey: queryKeys.entityDetail(entityType, id),
      queryFn: () => getEntityType(entityType)!.api.get(id),
      staleTime: MAP_STALE_TIME,
    }) as Promise<T>;
  return {
    place: (id) => detail<PlaceLike>(PLACE_KEY, id),
    project: (id) => detail<ProjectLike>(PROJECT_KEY, id),
    communeContour: (code) =>
      queryClient.fetchQuery({ queryKey: queryKeys.communeContour(code), queryFn: () => fetchCommuneContour(code), staleTime: Infinity }),
  };
}
