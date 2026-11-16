// The extent of a GeoJSON geometry, in degrees: pure, for choosing the widest of several places.

/** [west, south, east, north] — GeoJSON's bbox order. */
export type Bounds = readonly [number, number, number, number];

/** The bounds of a geometry's coordinates, null when it has none. */
export function boundsOf(geometry: GeoJSON.Geometry): Bounds | null {
  let west = Infinity;
  let south = Infinity;
  let east = -Infinity;
  let north = -Infinity;
  for (const [lng, lat] of positionsOf(geometry)) {
    west = Math.min(west, lng);
    south = Math.min(south, lat);
    east = Math.max(east, lng);
    north = Math.max(north, lat);
  }
  return west === Infinity ? null : [west, south, east, north];
}

/** The area of bounds in square degrees — enough to tell which of two places is the wider. */
export function boundsArea([west, south, east, north]: Bounds): number {
  return (east - west) * (north - south);
}

function positionsOf(geometry: GeoJSON.Geometry): GeoJSON.Position[] {
  switch (geometry.type) {
    case "Point":
      return [geometry.coordinates];
    case "MultiPoint":
    case "LineString":
      return geometry.coordinates;
    case "MultiLineString":
    case "Polygon":
      return geometry.coordinates.flat();
    case "MultiPolygon":
      return geometry.coordinates.flat(2);
    case "GeometryCollection":
      return geometry.geometries.flatMap(positionsOf);
  }
}
