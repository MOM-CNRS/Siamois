import {placeRefsOf} from "./placeRefs";
import {boundsArea, boundsOf} from "./geoBounds";

// Where to frame the map that draws an entity's geometry when it has none yet: the INSEE commune of
// its place, else the widest place of its project that can be located, else nowhere. Pure but for the
// loaders it is given (cached queries in the app, fakes in tests).

/** What the focus needs of a place's detail: its own geometry, its answers and the fields they answer. */
export interface PlaceLike {
  geom?: { type?: string | null; coordinates?: unknown; srid?: number | null } | null;
  answers?: Record<string, unknown> | null;
  fields?: Record<string, { id: string; valueBinding?: string | null }> | null;
}

/** What the focus needs of a project's detail: its main place and its spatial context. */
export interface ProjectLike {
  mainLocation?: { id: string } | null;
  spatialContext?: readonly { id: string }[] | null;
}

export interface FocusLoaders {
  place: (id: string) => Promise<PlaceLike>;
  project: (id: string) => Promise<ProjectLike>;
  communeContour: (inseeCode: string) => Promise<GeoJSON.Geometry | null>;
}

export interface FocusSource {
  // The places the entity's own fiche names (its « lieu »), in the fiche's order.
  placeIds: readonly string[];
  projectId?: string;
}

const WGS84_SRID = 4326;
const INSEE_CODE_BINDING = "inseeCode";

/** The places an entity's answers name, as the focus source of its geometry. */
export function focusSourceOf(answers: Record<string, unknown> | null | undefined, projectId: string | undefined): FocusSource {
  return { placeIds: placeRefsOf(answers), projectId };
}

/**
 * The area to frame: the commune of the entity's first place when it is an INSEE one, otherwise the
 * widest locatable place of its project (main place and spatial context), otherwise null.
 */
export async function resolveDrawingFocus(source: FocusSource, loaders: FocusLoaders): Promise<GeoJSON.Geometry | null> {
  const [firstPlaceId] = source.placeIds;
  if (firstPlaceId != null) {
    const place = await orNull(loaders.place(firstPlaceId));
    const code = place ? inseeCodeOf(place) : null;
    const contour = code ? await orNull(loaders.communeContour(code)) : null;
    if (contour) return contour;
  }
  if (source.projectId == null) return null;
  const project = await orNull(loaders.project(source.projectId));
  if (!project) return null;
  const candidates = await Promise.all(projectPlaceIds(project).map((id) => orNull(placeExtent(id, loaders))));
  return widest(candidates.filter((g): g is GeoJSON.Geometry => g != null));
}

/** The INSEE code of a place (the answer of its `inseeCode` field), when it is a commune of the INSEE source. */
export function inseeCodeOf(place: PlaceLike): string | null {
  const field = Object.values(place.fields ?? {}).find((f) => f.valueBinding === INSEE_CODE_BINDING);
  const answer = field ? place.answers?.[field.id] : undefined;
  const value = answer != null && typeof answer === "object" && "value" in answer ? (answer as { value: unknown }).value : answer;
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

/** A place's own geometry when it is already in WGS84 — any other SRID would need a reprojection the browser can't do. */
export function wgs84GeometryOf(place: PlaceLike): GeoJSON.Geometry | null {
  const geom = place.geom;
  if (!geom?.type || geom.coordinates == null || geom.srid !== WGS84_SRID) return null;
  return { type: geom.type, coordinates: geom.coordinates } as GeoJSON.Geometry;
}

/** The widest of the geometries by their bounds, null when there is none. */
export function widest(geometries: readonly GeoJSON.Geometry[]): GeoJSON.Geometry | null {
  let best: GeoJSON.Geometry | null = null;
  let bestArea = -1;
  for (const geometry of geometries) {
    const bounds = boundsOf(geometry);
    if (bounds && boundsArea(bounds) > bestArea) {
      best = geometry;
      bestArea = boundsArea(bounds);
    }
  }
  return best;
}

function projectPlaceIds(project: ProjectLike): string[] {
  const ids = [project.mainLocation?.id, ...(project.spatialContext ?? []).map((p) => p.id)];
  return [...new Set(ids.filter((id): id is string => id != null).map(String))];
}

// Where a place is: its commune's outline for an INSEE one, else its own geometry when in WGS84.
async function placeExtent(id: string, loaders: FocusLoaders): Promise<GeoJSON.Geometry | null> {
  const place = await loaders.place(id);
  const code = inseeCodeOf(place);
  return (code ? await loaders.communeContour(code) : null) ?? wgs84GeometryOf(place);
}

// A place that can't be read, or a commune the API doesn't answer for, only leaves that place out.
async function orNull<T>(promise: Promise<T>): Promise<T | null> {
  try {
    return await promise;
  } catch {
    return null;
  }
}
