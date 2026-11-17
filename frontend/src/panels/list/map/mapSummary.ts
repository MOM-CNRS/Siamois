import type {MapData, MapFeature} from "../../../entities/shared/listApi";
import {isOpenInOverview} from "../overviewSelection";

/** What the banners above a map say, derived from the server's two counts and the features it placed. */
export interface MapSummary {
  total: number;
  examined: number;
  // Rows of the list the map could not place: those without a geometry (the server walks only the others), and
  // those examined but left out (an unreadable geometry, no SRID or an SRID PostGIS doesn't know).
  withoutGeometry: number;
  // The result set holds more rows than the server examines for one map.
  truncated: boolean;
  empty: boolean;
}

export function summarizeMap({ features, meta }: MapData): MapSummary {
  return {
    total: meta.total,
    examined: meta.examined,
    withoutGeometry: Math.max(0, (meta.listTotal ?? meta.total) - meta.total) + Math.max(0, meta.examined - features.length),
    truncated: meta.total > meta.examined,
    empty: meta.total === 0,
  };
}

/**
 * The feature of the fiche the overview shows, when the map placed it: one with no geometry, or
 * outside the list's result set, selects nothing.
 */
export function selectedFeature(features: readonly MapFeature[], overviewEntityId: string | number | undefined): MapFeature | undefined {
  return overviewEntityId == null ? undefined : features.find((f) => isOpenInOverview(overviewEntityId, f.id));
}
