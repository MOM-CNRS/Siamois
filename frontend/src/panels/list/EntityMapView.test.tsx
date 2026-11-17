import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import type L from "leaflet";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import type {MapData, MapFeature} from "../../entities/shared/listApi";
import type {EntityTypeConfig} from "../../entities/shared/types";
import {setLocale} from "../../i18n";
import {LIST_LOOK, SELECTED_LOOK, type FeatureLook} from "../../map/featureLook";
import {fitLayer, revealLayer} from "../../map/leafletMap";
import {focusOnMap} from "../../map/mapFocus";
import {useFeatureLayer} from "../../map/useFeatureLayer";
import {useLeafletMap} from "../../map/useLeafletMap";
import {EntityMapView, type EntityMapViewProps} from "./EntityMapView";
import {useEntityMapData} from "./map/useEntityMapData";
import {useSelectedFeature} from "./map/useSelectedFeature";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// The map view's wiring: what it fetches, which layers it draws in which look, when it frames them, how a
// click opens a row, and how the fiche's geometry chip zooms onto the selection. Leaflet and the data hooks
// are stand-ins (each has its own tests); mapFocus is real, so the chip's request goes through it.

vi.mock("../../map/useLeafletMap", () => ({ useLeafletMap: vi.fn() }));
vi.mock("../../map/useFeatureLayer", () => ({ useFeatureLayer: vi.fn() }));
vi.mock("../../map/leafletMap", () => ({ fitLayer: vi.fn(), revealLayer: vi.fn() }));
vi.mock("./map/useEntityMapData", () => ({ useEntityMapData: vi.fn() }));
vi.mock("./map/useSelectedFeature", () => ({ useSelectedFeature: vi.fn() }));

type Callbacks = Parameters<typeof useFeatureLayer>[3];
type LayerCall = { map: L.Map | null; features: readonly MapFeature[]; look: FeatureLook; callbacks: Callbacks };

const map = { id: "map" } as unknown as L.Map;
const listLayer = { id: "list" } as unknown as L.GeoJSON;
const selectionLayer = { id: "selection" } as unknown as L.GeoJSON;
const point = (id: string): MapFeature => ({
  id,
  label: `UE-${id}`,
  validationStatus: "VALIDATED",
  geometry: { type: "Point", coordinates: [2, 48] },
});
const features = [point("1"), point("2")];
const data: MapData = { features, meta: { total: 2, examined: 2 } };
const config = { key: "recordingUnit", collectionPath: "recording-units" } as unknown as EntityTypeConfig<unknown, unknown>;

let layerCalls: LayerCall[];
let selectionRef: { current: L.GeoJSON | null };
let root: Root | undefined;
let host: HTMLElement;

function mapData(overrides: Partial<ReturnType<typeof useEntityMapData>> = {}) {
  vi.mocked(useEntityMapData).mockReturnValue({ data, isLoading: false, error: null, ...overrides } as ReturnType<typeof useEntityMapData>);
}

function render(props: Partial<EntityMapViewProps> = {}) {
  act(() =>
    root!.render(
      <EntityMapView entityType="recordingUnit" config={config} params={{ organizationId: 3, search: "fosse" }} {...props} />,
    ),
  );
}

// The calls of the last render, one per layer: the list's, then the selection's.
function lastLayer(look: FeatureLook): LayerCall {
  const calls = layerCalls.filter((c) => c.look === look);
  return calls[calls.length - 1];
}
const lastListLayer = () => lastLayer(LIST_LOOK);
const lastSelectionLayer = () => lastLayer(SELECTED_LOOK);

beforeEach(() => {
  setLocale("fr");
  layerCalls = [];
  selectionRef = { current: selectionLayer };
  vi.mocked(useLeafletMap).mockReset().mockReturnValue(map);
  vi.mocked(useSelectedFeature).mockReset().mockReturnValue(undefined);
  vi.mocked(fitLayer).mockReset().mockReturnValue(true);
  vi.mocked(revealLayer).mockReset();
  vi.mocked(useFeatureLayer).mockReset().mockImplementation((m, f, look, callbacks) => {
    layerCalls.push({ map: m, features: f, look, callbacks });
    return look === SELECTED_LOOK ? selectionRef : { current: listLayer };
  });
  mapData();
  host = document.createElement("div");
  document.body.appendChild(host);
  root = createRoot(host);
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
  host.remove();
});

describe("EntityMapView data", () => {
  it("fetches the list's result set by the chosen geometry column", () => {
    render({ geometry: "-9" });

    expect(useEntityMapData).toHaveBeenCalledWith("recordingUnit", "recording-units", { organizationId: 3, search: "fosse" }, true, "-9");
  });

  it("draws the features the server placed in the list's look", () => {
    render();

    expect(lastListLayer().map).toBe(map);
    expect(lastListLayer().features).toBe(features);
  });

  it("draws no feature before the map has loaded", () => {
    mapData({ data: undefined, isLoading: true });

    render();

    expect(lastListLayer().features).toEqual([]);
    expect(host.querySelector(".entity-map-loading")).not.toBeNull();
    expect(host.querySelector(".entity-map-notice")).toBeNull();
  });

  it("shows no skeleton once loaded, and the banners of the result set", () => {
    mapData({ data: { features: [point("1")], meta: { total: 5, examined: 3 } } });

    render();

    expect(host.querySelector(".entity-map-loading")).toBeNull();
    const notices = [...host.querySelectorAll(".entity-map-notice")].map((n) => n.textContent);
    expect(notices.some((n) => n?.includes("2 non placé(s)"))).toBe(true);
    expect(notices.some((n) => n?.includes("Seuls les 3 premiers éléments sur 5"))).toBe(true);
  });

  it("says why the map could not be loaded", () => {
    mapData({ data: undefined, error: new Error("Serveur indisponible") as never });

    render();

    expect(host.querySelector(".entity-list-panel-error")!.textContent).toBe("Serveur indisponible");
  });

  it("always renders the canvas Leaflet lives in", () => {
    render();

    expect(host.querySelector(".entity-map-frame > .entity-map-canvas")).not.toBeNull();
  });
});

describe("EntityMapView opening a feature", () => {
  it("opens it in the overview with what the overview header shows meanwhile", () => {
    const onOpenOverview = vi.fn();
    const onNavigate = vi.fn();
    render({ onOpenOverview, onNavigate });

    lastListLayer().callbacks.onOpen!(features[1]);

    expect(onOpenOverview).toHaveBeenCalledWith("recordingUnit", "2", { label: "UE-2", validationStatus: "VALIDATED" });
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("navigates to it without an overview (focus mode)", () => {
    const onNavigate = vi.fn();
    render({ onNavigate });

    lastListLayer().callbacks.onOpen!(features[0]);

    expect(onNavigate).toHaveBeenCalledWith("recordingUnit", "1");
  });

  it("does nothing when nobody listens", () => {
    render();

    expect(() => lastListLayer().callbacks.onOpen!(features[0])).not.toThrow();
  });
});

describe("EntityMapView framing", () => {
  it("frames the features once drawn", () => {
    render();

    lastListLayer().callbacks.onDrawn!(map, listLayer);

    expect(fitLayer).toHaveBeenCalledWith(map, listLayer);
  });

  it("leaves the user's view alone when the same result set is drawn again (a refetch after an edit)", () => {
    render();
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    render();
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    expect(fitLayer).toHaveBeenCalledTimes(1);
  });

  it("frames again for another result set", () => {
    render();
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    render({ params: { organizationId: 3, search: "silo" } });
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    expect(fitLayer).toHaveBeenCalledTimes(2);
  });

  it("frames again for another geometry column", () => {
    render();
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    render({ geometry: "-9" });
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    expect(fitLayer).toHaveBeenCalledTimes(2);
  });

  it("ignores a change of page or order, which is not a change of result set", () => {
    render({ params: { organizationId: 3, search: "fosse", offset: 0 } as never });
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    render({ params: { organizationId: 3, search: "fosse", offset: 40, sort: "identifier" } as never });
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    expect(fitLayer).toHaveBeenCalledTimes(1);
  });

  it("tries again on the next draw when there was nothing to frame (empty result set)", () => {
    vi.mocked(fitLayer).mockReturnValueOnce(false);
    render();
    lastListLayer().callbacks.onDrawn!(map, listLayer);

    lastListLayer().callbacks.onDrawn!(map, listLayer);

    expect(fitLayer).toHaveBeenCalledTimes(2);
  });
});

describe("EntityMapView selection", () => {
  it("asks for the overview's fiche among the map's features, by the same geometry column", () => {
    render({ selectedId: "2", geometry: "-9" });

    expect(useSelectedFeature).toHaveBeenCalledWith("recordingUnit", features, "2", "-9");
  });

  it("highlights the selected feature in its own look, and brings it into view", () => {
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);

    render({ selectedId: "2" });

    expect(lastSelectionLayer().features).toEqual([features[1]]);
    expect(lastSelectionLayer().callbacks.onDrawn).toBe(revealLayer);
    expect(lastSelectionLayer().callbacks.onOpen).toBeUndefined();
  });

  it("highlights nothing without a selection, with a stable empty set", () => {
    render();
    const first = lastSelectionLayer().features;

    render();

    expect(first).toEqual([]);
    expect(lastSelectionLayer().features).toBe(first);
  });
});

describe("EntityMapView answering the fiche's geometry chip", () => {
  it("zooms onto the selected feature, animated", () => {
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    render({ selectedId: "2" });

    expect(focusOnMap("recordingUnit", "2")).toBe(true);
    expect(fitLayer).toHaveBeenCalledWith(map, selectionLayer, true);
  });

  it("matches a numeric id against the feature's string id", () => {
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    render({ selectedId: 2 });

    expect(focusOnMap("recordingUnit", 2)).toBe(true);
  });

  it("does not answer for another entity type", () => {
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    render({ selectedId: "2" });

    expect(focusOnMap("place", "2")).toBe(false);
  });

  it("does not answer for an entity other than the selected one", () => {
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    render({ selectedId: "2" });

    expect(focusOnMap("recordingUnit", "1")).toBe(false);
  });

  it("does not answer without a selection", () => {
    render();

    expect(focusOnMap("recordingUnit", "1")).toBe(false);
  });

  it("does not answer before the map exists", () => {
    vi.mocked(useLeafletMap).mockReturnValue(null);
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    render({ selectedId: "2" });

    expect(focusOnMap("recordingUnit", "2")).toBe(false);
  });

  it("does not answer before the selection is drawn", () => {
    selectionRef.current = null;
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    render({ selectedId: "2" });

    expect(focusOnMap("recordingUnit", "2")).toBe(false);
  });

  it("says when there was nothing to zoom onto", () => {
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    vi.mocked(fitLayer).mockReturnValue(false);
    render({ selectedId: "2" });

    expect(focusOnMap("recordingUnit", "2")).toBe(false);
  });

  it("stops answering once unmounted", () => {
    vi.mocked(useSelectedFeature).mockReturnValue(features[1]);
    render({ selectedId: "2" });

    act(() => root!.unmount());
    root = undefined;

    expect(focusOnMap("recordingUnit", "2")).toBe(false);
  });
});
