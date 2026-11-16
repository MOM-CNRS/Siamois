import type {MapData} from "../../../entities/shared/listApi";

/** What the banners above a map say, derived from the server's two counts and the features it placed. */
export interface MapSummary {
  total: number;
  examined: number;
  // Examined but not placed: no geometry, no SRID or an SRID PostGIS doesn't know.
  withoutGeometry: number;
  // The result set holds more rows than the server examines for one map.
  truncated: boolean;
  empty: boolean;
}

export function summarizeMap({ features, meta }: MapData): MapSummary {
  return {
    total: meta.total,
    examined: meta.examined,
    withoutGeometry: Math.max(0, meta.examined - features.length),
    truncated: meta.total > meta.examined,
    empty: meta.total === 0,
  };
}
