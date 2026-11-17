import L from "leaflet";
import type {MapFeature} from "../entities/shared/listApi";
import {t} from "../i18n";
import {featureOptions, type FeatureLook} from "./featureLook";

// `maxNativeZoom`: the last zoom the provider has tiles for. Past it Leaflet enlarges that zoom's tiles
// instead of asking for tiles that do not exist (a blank map), so the map can zoom in as far as MAX_ZOOM.
const OSM = { url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png", attribution: "© OpenStreetMap", maxNativeZoom: 19 };
const SATELLITE = {
  url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
  attribution: "Tiles © Esri",
  maxNativeZoom: 19,
};

// Far past the tiles' own levels, on enlarged tiles, so a small excavation unit can still be looked at closely.
export const MAX_ZOOM = 25;
const PADDING = L.point(24, 24);
// How close a map comes to the one feature it focuses on by itself: a point, whose bounds have no size,
// would otherwise be framed at MAX_ZOOM, on enlarged tiles with nothing around it.
const FOCUS_MAX_ZOOM = OSM.maxNativeZoom;
// Mainland France, until the features say where to look.
const INITIAL_VIEW: L.LatLngTuple = [46.6, 2.4];

/** A map with the OSM and satellite base layers, centred on France. */
export function createBaseMap(container: HTMLElement): L.Map {
  const map = L.map(container, { zoomControl: true, maxZoom: MAX_ZOOM, zoomSnap: 0.5, zoomDelta: 0.5, wheelPxPerZoomLevel: 90 })
    .setView(INITIAL_VIEW, 5);
  const osm = L.tileLayer(OSM.url, { attribution: OSM.attribution, maxNativeZoom: OSM.maxNativeZoom, maxZoom: MAX_ZOOM }).addTo(map);
  const satellite = L.tileLayer(SATELLITE.url, { attribution: SATELLITE.attribution, maxNativeZoom: SATELLITE.maxNativeZoom, maxZoom: MAX_ZOOM });
  L.control.layers({ [t("list.mapOsm")]: osm, [t("list.mapSatellite")]: satellite }).addTo(map);
  return map;
}

/** The features as one GeoJSON layer in a look; a click on an interactive one opens it. */
export function createFeatureLayer(features: readonly MapFeature[], look: FeatureLook, onOpen: (feature: MapFeature) => void): L.GeoJSON {
  const layer = L.geoJSON(undefined, {
    style: (feature) => featureOptions(feature?.properties as MapFeature | undefined, look),
    pointToLayer: (feature, latlng) => L.circleMarker(latlng, featureOptions(feature.properties as MapFeature, look)),
    onEachFeature: (feature, l) => {
      if (!look.interactive) return;
      const props = feature.properties as MapFeature;
      l.bindTooltip(props.label, { sticky: true });
      l.on("click", () => onOpen(props));
    },
  });
  features.forEach((f) => layer.addData({ type: "Feature", geometry: f.geometry, properties: f } as GeoJSON.Feature));
  return layer;
}

/**
 * Frames the whole layer. At once by default, so that a layer drawn right after (the selection) reads
 * the framed view; animated when the user asked for it. Says whether there was anything to frame.
 */
export function fitLayer(map: L.Map, layer: L.GeoJSON, animate = false): boolean {
  return frameLayer(map, layer, MAX_ZOOM, animate);
}

/**
 * Zooms onto the layer, at once, but no closer than the tiles' own levels: the feature a map focuses
 * on by itself (the first result), seen with its surroundings. Says whether there was anything to focus on.
 */
export function focusLayer(map: L.Map, layer: L.GeoJSON): boolean {
  return frameLayer(map, layer, FOCUS_MAX_ZOOM, false);
}

/**
 * Brings the layer into view without reframing a map that already shows it: panned to when it fits
 * at the current zoom, zoomed out to otherwise.
 */
export function revealLayer(map: L.Map, layer: L.GeoJSON): void {
  const bounds = layer.getBounds();
  if (!bounds.isValid()) return;
  map.invalidateSize();
  if (map.getBounds().contains(bounds)) return;
  moveToCenter(map, layer, bounds);
}

/**
 * Puts the layer in the middle of the map, even when the map already shows it (unlike revealLayer):
 * panned to when it fits at the current zoom, zoomed out to otherwise, animated either way.
 */
export function centerLayer(map: L.Map, layer: L.GeoJSON): void {
  const bounds = layer.getBounds();
  if (!bounds.isValid()) return;
  map.invalidateSize();
  moveToCenter(map, layer, bounds, true);
}

// Each move first reads the container's current size: a pane resized in the same render as the move
// (the overview opening on the first result) has not been reported to the map by its observer yet.
function frameLayer(map: L.Map, layer: L.GeoJSON, maxZoom: number, animate: boolean): boolean {
  const bounds = layer.getBounds();
  if (!bounds.isValid()) return false;
  map.invalidateSize();
  map.fitBounds(bounds, { padding: PADDING, maxZoom, animate });
  return true;
}

function moveToCenter(map: L.Map, layer: L.GeoJSON, bounds: L.LatLngBounds, animateZoomOut = false): void {
  if (map.getBoundsZoom(bounds, false, PADDING) >= map.getZoom()) map.panTo(bounds.getCenter());
  else fitLayer(map, layer, animateZoomOut);
}
