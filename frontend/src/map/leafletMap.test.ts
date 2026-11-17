import L from "leaflet";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import type {MapFeature} from "../entities/shared/listApi";
import {setLocale} from "../i18n";
import {LIST_LOOK, SELECTED_LOOK} from "./featureLook";
import {createBaseMap, createFeatureLayer, fitLayer, MAX_ZOOM, revealLayer} from "./leafletMap";

// Real Leaflet over jsdom for building the map and its layers; the framing logic is checked against a
// map stand-in, since jsdom lays nothing out and Leaflet's own bounds math would see a 0×0 map.

const point = (id: string, lng: number, lat: number): MapFeature => ({
  id,
  label: `UE-${id}`,
  type: { resolvedLabel: "Fosse" },
  geometry: { type: "Point", coordinates: [lng, lat] },
});
const polygon: MapFeature = {
  id: "p",
  label: "Zone",
  geometry: { type: "Polygon", coordinates: [[[2, 48], [3, 48], [3, 49], [2, 49], [2, 48]]] },
};

describe("createBaseMap", () => {
  let container: HTMLDivElement;
  let map: L.Map | undefined;

  beforeEach(() => {
    setLocale("fr");
    container = document.createElement("div");
    document.body.appendChild(container);
  });

  afterEach(() => {
    map?.remove();
    map = undefined;
    container.remove();
  });

  it("centres the map on mainland France", () => {
    map = createBaseMap(container);

    expect(map.getCenter()).toEqual(L.latLng(46.6, 2.4));
    expect(map.getZoom()).toBe(5);
  });

  it("lets the user zoom past the tiles' own levels", () => {
    map = createBaseMap(container);

    expect(map.getMaxZoom()).toBe(MAX_ZOOM);
    expect(MAX_ZOOM).toBe(22);
  });

  it("shows OpenStreetMap at first and offers the satellite view, in the page language", () => {
    map = createBaseMap(container);

    const tiles: L.TileLayer[] = [];
    map.eachLayer((layer) => {
      if (layer instanceof L.TileLayer) tiles.push(layer);
    });
    expect(tiles).toHaveLength(1);
    expect((tiles[0] as unknown as { _url: string })._url).toContain("tile.openstreetmap.org");
    expect(container.querySelector(".leaflet-control-layers")!.textContent).toContain("Satellite");
    expect(container.querySelector(".leaflet-control-zoom")).not.toBeNull();
  });
});

describe("createFeatureLayer", () => {
  it("draws one layer per feature, carrying the feature itself", () => {
    const layer = createFeatureLayer([point("1", 2, 48), polygon], LIST_LOOK, vi.fn());

    const drawn = layer.getLayers() as (L.Layer & { feature: GeoJSON.Feature })[];
    expect(drawn).toHaveLength(2);
    expect(drawn.map((l) => (l.feature.properties as MapFeature).id)).toEqual(["1", "p"]);
  });

  it("draws points as circle markers in the look's radius", () => {
    const layer = createFeatureLayer([point("1", 2, 48)], LIST_LOOK, vi.fn());

    const [marker] = layer.getLayers();
    expect(marker).toBeInstanceOf(L.CircleMarker);
    expect((marker as L.CircleMarker).getRadius()).toBe(LIST_LOOK.radius);
  });

  it("draws shapes in the look's weight", () => {
    const layer = createFeatureLayer([polygon], SELECTED_LOOK, vi.fn());

    const [shape] = layer.getLayers();
    expect(shape).toBeInstanceOf(L.Polygon);
    expect((shape as L.Polygon).options.weight).toBe(SELECTED_LOOK.weight);
  });

  it("draws an empty layer for no feature", () => {
    expect(createFeatureLayer([], LIST_LOOK, vi.fn()).getLayers()).toHaveLength(0);
  });

  it("opens an interactive feature on click and names it in a tooltip", () => {
    const onOpen = vi.fn();
    const feature = point("1", 2, 48);
    const layer = createFeatureLayer([feature], LIST_LOOK, onOpen);

    const [marker] = layer.getLayers();
    marker.fire("click");

    expect(onOpen).toHaveBeenCalledWith(feature);
    expect(marker.getTooltip()?.getContent()).toBe("UE-1");
  });

  it("leaves a non-interactive feature (the selection) without click nor tooltip", () => {
    const onOpen = vi.fn();
    const layer = createFeatureLayer([point("1", 2, 48)], SELECTED_LOOK, onOpen);

    const [marker] = layer.getLayers();
    marker.fire("click");

    expect(onOpen).not.toHaveBeenCalled();
    expect(marker.getTooltip()).toBeUndefined();
  });
});

// A map stand-in: what it shows, at which zoom, and how it is moved.
function fakeMap({ shows = false, boundsZoom = 10, zoom = 10 } = {}) {
  return {
    fitBounds: vi.fn(),
    panTo: vi.fn(),
    getZoom: vi.fn(() => zoom),
    getBoundsZoom: vi.fn(() => boundsZoom),
    getBounds: vi.fn(() => ({ contains: () => shows })),
  };
}
const asMap = (m: ReturnType<typeof fakeMap>) => m as unknown as L.Map;

describe("fitLayer", () => {
  it("frames the whole layer at once by default", () => {
    const map = fakeMap();
    const layer = createFeatureLayer([point("1", 2, 48), point("2", 3, 49)], LIST_LOOK, vi.fn());

    expect(fitLayer(asMap(map), layer)).toBe(true);

    const [bounds, options] = map.fitBounds.mock.calls[0];
    expect((bounds as L.LatLngBounds).equals(L.latLngBounds([48, 2], [49, 3]))).toBe(true);
    expect(options).toEqual({ padding: L.point(24, 24), maxZoom: MAX_ZOOM, animate: false });
  });

  it("animates when asked", () => {
    const map = fakeMap();

    fitLayer(asMap(map), createFeatureLayer([polygon], LIST_LOOK, vi.fn()), true);

    expect(map.fitBounds.mock.calls[0][1]).toEqual(expect.objectContaining({ animate: true }));
  });

  it("frames a single point too", () => {
    const map = fakeMap();

    expect(fitLayer(asMap(map), createFeatureLayer([point("1", 2, 48)], LIST_LOOK, vi.fn()))).toBe(true);
  });

  it("says there was nothing to frame for an empty layer, leaving the map alone", () => {
    const map = fakeMap();

    expect(fitLayer(asMap(map), createFeatureLayer([], LIST_LOOK, vi.fn()))).toBe(false);
    expect(map.fitBounds).not.toHaveBeenCalled();
  });
});

describe("revealLayer", () => {
  const layer = () => createFeatureLayer([point("1", 2, 48), point("2", 3, 49)], SELECTED_LOOK, vi.fn());

  it("leaves a map that already shows the layer alone", () => {
    const map = fakeMap({ shows: true });

    revealLayer(asMap(map), layer());

    expect(map.panTo).not.toHaveBeenCalled();
    expect(map.fitBounds).not.toHaveBeenCalled();
  });

  it("pans to a layer that fits at the current zoom", () => {
    const map = fakeMap({ boundsZoom: 12, zoom: 10 });

    revealLayer(asMap(map), layer());

    expect(map.panTo).toHaveBeenCalledWith(L.latLngBounds([48, 2], [49, 3]).getCenter());
    expect(map.fitBounds).not.toHaveBeenCalled();
  });

  it("pans when the layer fits exactly at the current zoom", () => {
    const map = fakeMap({ boundsZoom: 10, zoom: 10 });

    revealLayer(asMap(map), layer());

    expect(map.panTo).toHaveBeenCalled();
  });

  it("zooms out to a layer too large for the current zoom", () => {
    const map = fakeMap({ boundsZoom: 9, zoom: 10 });

    revealLayer(asMap(map), layer());

    expect(map.panTo).not.toHaveBeenCalled();
    expect(map.fitBounds).toHaveBeenCalledTimes(1);
  });

  it("does nothing for an empty layer", () => {
    const map = fakeMap();

    revealLayer(asMap(map), createFeatureLayer([], SELECTED_LOOK, vi.fn()));

    expect(map.getBounds).not.toHaveBeenCalled();
    expect(map.panTo).not.toHaveBeenCalled();
    expect(map.fitBounds).not.toHaveBeenCalled();
  });
});
