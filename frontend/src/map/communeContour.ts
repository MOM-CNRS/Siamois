import {fetchExternalJson} from "../api/client";

// geo.api.gouv.fr, the public API of the French communes (the INSEE source of places): CORS-open,
// asked directly by the browser rather than through SIAMOIS.
const GEO_API = "https://geo.api.gouv.fr/communes";

/** A commune's outline (GeoJSON, WGS84) by its INSEE code; null when the API has none. */
export async function fetchCommuneContour(inseeCode: string): Promise<GeoJSON.Geometry | null> {
  const commune = await fetchExternalJson<{ contour?: GeoJSON.Geometry | null }>(
    `${GEO_API}/${encodeURIComponent(inseeCode)}?fields=contour&format=json&geometry=contour`,
  );
  return commune.contour ?? null;
}
