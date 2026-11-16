// A geometry being drawn on a map, one click at a time: pure, in GeoJSON order ([lng, lat]).
// Two modes: polygons (each closed before the next is started; one or several) or a single point.

export type LngLat = readonly [number, number];
export type DraftMode = "polygon" | "point";

export interface ShapeDraft {
  mode: DraftMode;
  // Polygon mode: the polygons already closed, and the ring being drawn.
  polygons: readonly (readonly LngLat[])[];
  ring: readonly LngLat[];
  // Point mode: the point placed.
  point: LngLat | null;
}

/** A polygon needs three vertices; fewer is a line, not a shape. */
export const MIN_POLYGON_VERTICES = 3;

export function emptyDraft(mode: DraftMode): ShapeDraft {
  return { mode, polygons: [], ring: [], point: null };
}

/** A click: a vertex of the ring being drawn, or the point (replacing the previous one). */
export function addVertex(draft: ShapeDraft, vertex: LngLat): ShapeDraft {
  return draft.mode === "point" ? { ...draft, point: vertex } : { ...draft, ring: [...draft.ring, vertex] };
}

export function canCloseRing(draft: ShapeDraft): boolean {
  return draft.mode === "polygon" && draft.ring.length >= MIN_POLYGON_VERTICES;
}

/** Closes the ring being drawn into a polygon; the next click starts another one. */
export function closeRing(draft: ShapeDraft): ShapeDraft {
  return canCloseRing(draft) ? { ...draft, polygons: [...draft.polygons, draft.ring], ring: [] } : draft;
}

/** Takes back the last click: a vertex of the ring, or — the ring empty — reopens the last polygon. */
export function undo(draft: ShapeDraft): ShapeDraft {
  if (draft.mode === "point") return { ...draft, point: null };
  if (draft.ring.length > 0) return { ...draft, ring: draft.ring.slice(0, -1) };
  const last = draft.polygons[draft.polygons.length - 1];
  return last ? { ...draft, polygons: draft.polygons.slice(0, -1), ring: last.slice(0, -1) } : draft;
}

export function isEmpty(draft: ShapeDraft): boolean {
  return draft.point == null && draft.ring.length === 0 && draft.polygons.length === 0;
}

/**
 * Whether the draft is a geometry: a point, or at least one polygon with no ring left half-drawn
 * (a ring of three vertices or more counts as closed).
 */
export function canSave(draft: ShapeDraft): boolean {
  if (draft.mode === "point") return draft.point != null;
  return polygonsOf(draft) != null;
}

/** The draft as GeoJSON: a Point, a Polygon, or a MultiPolygon for several. */
export function draftToGeometry(draft: ShapeDraft): GeoJSON.Point | GeoJSON.Polygon | GeoJSON.MultiPolygon {
  if (draft.mode === "point") {
    if (!draft.point) throw new Error("No point placed");
    return { type: "Point", coordinates: [...draft.point] };
  }
  const polygons = polygonsOf(draft);
  if (!polygons) throw new Error(`A polygon needs at least ${MIN_POLYGON_VERTICES} vertices`);
  const rings = polygons.map((ring) => [closedRing(ring)]);
  return rings.length === 1 ? { type: "Polygon", coordinates: rings[0] } : { type: "MultiPolygon", coordinates: rings };
}

// The polygons to save: the closed ones and the ring if it is a shape; null when a ring is half-drawn or there is none.
function polygonsOf(draft: ShapeDraft): (readonly LngLat[])[] | null {
  if (draft.ring.length > 0 && draft.ring.length < MIN_POLYGON_VERTICES) return null;
  const polygons = draft.ring.length > 0 ? [...draft.polygons, draft.ring] : [...draft.polygons];
  return polygons.length > 0 ? polygons : null;
}

function closedRing(ring: readonly LngLat[]): GeoJSON.Position[] {
  const positions = ring.map(([lng, lat]) => [lng, lat]);
  return [...positions, [...positions[0]]];
}
