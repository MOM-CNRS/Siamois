import {act, type RefObject} from "react";
import {createRoot, type Root} from "react-dom/client";
import type L from "leaflet";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import type {MapFeature} from "../entities/shared/listApi";
import {LIST_LOOK, SELECTED_LOOK, type FeatureLook} from "./featureLook";
import {createFeatureLayer} from "./leafletMap";
import {useFeatureLayer} from "./useFeatureLayer";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./leafletMap", () => ({ createFeatureLayer: vi.fn() }));

// A stand-in for the GeoJSON layer: records being added to a map and removed, and keeps the click handler.
interface FakeLayer {
  addTo: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
  click: (feature: MapFeature) => void;
}

const layers: FakeLayer[] = [];
const map = { id: "map" } as unknown as L.Map;
const otherMap = { id: "other" } as unknown as L.Map;
const feature: MapFeature = { id: "1", label: "UE-1", geometry: { type: "Point", coordinates: [2, 48] } };
const features: MapFeature[] = [feature];

type Callbacks = Parameters<typeof useFeatureLayer>[3];
type Args = { map: L.Map | null; features: readonly MapFeature[]; look: FeatureLook; callbacks: Callbacks };

let layerRef: RefObject<L.GeoJSON | null>;
let root: Root | undefined;

function Probe({ map: m, features: f, look, callbacks }: Readonly<Args>) {
  layerRef = useFeatureLayer(m, f, look, callbacks);
  return null;
}

function render(args: Args) {
  act(() => root!.render(<Probe {...args} />));
}

beforeEach(() => {
  layers.length = 0;
  vi.mocked(createFeatureLayer).mockReset().mockImplementation((_features, _look, onOpen) => {
    const layer: FakeLayer = { addTo: vi.fn(), remove: vi.fn(), click: onOpen };
    layer.addTo.mockReturnValue(layer);
    layers.push(layer);
    return layer as unknown as L.GeoJSON;
  });
  root = createRoot(document.createElement("div"));
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
});

describe("useFeatureLayer", () => {
  it("draws nothing until there is a map", () => {
    const onDrawn = vi.fn();

    render({ map: null, features, look: LIST_LOOK, callbacks: { onDrawn } });

    expect(createFeatureLayer).not.toHaveBeenCalled();
    expect(onDrawn).not.toHaveBeenCalled();
    expect(layerRef.current).toBeNull();
  });

  it("draws the features in the look, adds the layer to the map and frames it", () => {
    const onDrawn = vi.fn();

    render({ map, features, look: LIST_LOOK, callbacks: { onDrawn } });

    expect(createFeatureLayer).toHaveBeenCalledWith(features, LIST_LOOK, expect.any(Function));
    expect(layers[0].addTo).toHaveBeenCalledWith(map);
    expect(onDrawn).toHaveBeenCalledWith(map, layers[0]);
    expect(layerRef.current).toBe(layers[0]);
  });

  it("draws an empty set too", () => {
    render({ map, features: [], look: LIST_LOOK, callbacks: {} });

    expect(createFeatureLayer).toHaveBeenCalledWith([], LIST_LOOK, expect.any(Function));
    expect(layerRef.current).toBe(layers[0]);
  });

  it("opens a clicked feature with the latest callback, without redrawing for a new one", () => {
    const first = vi.fn();
    const second = vi.fn();
    render({ map, features, look: LIST_LOOK, callbacks: { onOpen: first } });

    render({ map, features, look: LIST_LOOK, callbacks: { onOpen: second } });
    layers[0].click(feature);

    expect(createFeatureLayer).toHaveBeenCalledTimes(1);
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith(feature);
  });

  it("ignores a click when nobody listens", () => {
    render({ map, features, look: LIST_LOOK, callbacks: {} });

    expect(() => layers[0].click(feature)).not.toThrow();
  });

  it("redraws when the features change, removing the previous layer", () => {
    render({ map, features, look: LIST_LOOK, callbacks: {} });

    const next = [{ ...feature, id: "2" }];
    render({ map, features: next, look: LIST_LOOK, callbacks: {} });

    expect(layers).toHaveLength(2);
    expect(layers[0].remove).toHaveBeenCalledTimes(1);
    expect(layers[1].remove).not.toHaveBeenCalled();
    expect(layerRef.current).toBe(layers[1]);
  });

  it("redraws when the look changes", () => {
    render({ map, features, look: LIST_LOOK, callbacks: {} });
    render({ map, features, look: SELECTED_LOOK, callbacks: {} });

    expect(vi.mocked(createFeatureLayer).mock.calls[1][1]).toBe(SELECTED_LOOK);
    expect(layers[0].remove).toHaveBeenCalled();
  });

  it("moves to another map", () => {
    render({ map, features, look: LIST_LOOK, callbacks: {} });
    render({ map: otherMap, features, look: LIST_LOOK, callbacks: {} });

    expect(layers[0].remove).toHaveBeenCalled();
    expect(layers[1].addTo).toHaveBeenCalledWith(otherMap);
  });

  it("removes the layer and forgets it when the map goes away", () => {
    render({ map, features, look: LIST_LOOK, callbacks: {} });
    render({ map: null, features, look: LIST_LOOK, callbacks: {} });

    expect(layers[0].remove).toHaveBeenCalledTimes(1);
    expect(layerRef.current).toBeNull();
  });

  it("removes the layer on unmount", () => {
    render({ map, features, look: LIST_LOOK, callbacks: {} });
    const ref = layerRef;

    act(() => root!.unmount());
    root = undefined;

    expect(layers[0].remove).toHaveBeenCalledTimes(1);
    expect(ref.current).toBeNull();
  });
});
