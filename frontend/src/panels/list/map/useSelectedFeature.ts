import {useMemo} from "react";
import type {MapFeature} from "../../../entities/shared/listApi";
import {useEntityFeature} from "../../../map/useEntityFeature";
import {selectedFeature} from "./mapSummary";

/**
 * The feature of the fiche the overview shows. Taken from the map's own features when it is among
 * them, so the usual case costs no call; asked for on its own only when the list's result set left
 * it out (filtered out, or past the server's cap) — and only once the map has loaded, so it is known
 * to be missing. Undefined when the fiche has no placeable geometry or nothing is open.
 */
export function useSelectedFeature(
  entityType: string,
  features: readonly MapFeature[] | undefined,
  selectedId: string | number | undefined,
): MapFeature | undefined {
  const inMap = useMemo(() => (features ? selectedFeature(features, selectedId) : undefined), [features, selectedId]);
  const missing = features != null && selectedId != null && inMap == null;
  const { feature } = useEntityFeature(entityType, selectedId, missing);
  if (inMap) return inMap;
  return missing ? feature ?? undefined : undefined;
}
