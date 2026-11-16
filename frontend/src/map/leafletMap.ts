import L from "leaflet";
import type {MapFeature} from "../entities/shared/listApi";
import {t} from "../i18n";
import {featureOptions, type FeatureLook} from "./featureLook";

const OSM = { url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png", attribution: "© OpenStreetMap" };
const SATELLITE = {
  url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
  attribution: "Tiles © Esri",
};

// Tiles stop at zoom 19 (OSM) — past it they are enlarged rather than missing, so a small
// excavation unit can still be looked at closely.
const MAX_NATIVE_ZOOM = 19;
export const MAX_ZOOM = 22;
const PADDING = L.point(24, 24);
// Mainland France, until the features say where to look.
const INITIAL_VIEW: L.LatLngTuple = [46.6, 2.4];

/** A map with the OSM and satellite base layers, centred on France. */
export function createBaseMap(container: HTMLElement): L.Map {
  const map = L.map(container, { zoomControl: true, maxZoom: MAX_ZOOM, zoomSnap: 0.5, zoomDelta: 0.5, wheelPxPerZoomLevel: 90 })
    .setView(INITIAL_VIEW, 5);
  const osm = L.tileLayer(OSM.url, { attribution: OSM.attribution, maxNativeZoom: MAX_NATIVE_ZOOM, maxZoom: MAX_ZOOM }).addTo(map);
  const satellite = L.tileLayer(SATELLITE.url, { attribution: SATELLITE.attribution, maxNativeZoom: MAX_NATIVE_ZOOM, maxZoom: MAX_ZOOM });
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
  const bounds = layer.getBounds();
  if (!bounds.isValid()) return false;
  map.fitBounds(bounds, { padding: PADDING, maxZoom: MAX_ZOOM, animate });
  return true;
}

/**
 * Brings the layer into view without reframing a map that already shows it: panned to when it fits
 * at the current zoom, zoomed out to otherwise.
 */
export function revealLayer(map: L.Map, layer: L.GeoJSON): void {
  const bounds = layer.getBounds();
  if (!bounds.isValid() || map.getBounds().contains(bounds)) return;
  if (map.getBoundsZoom(bounds, false, PADDING) >= map.getZoom()) map.panTo(bounds.getCenter());
  else fitLayer(map, layer);
}
