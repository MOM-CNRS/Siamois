import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import type {MapFeature} from "../entities/shared/listApi";
import {GeometryMapField} from "./GeometryMapField";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const state = vi.hoisted(() => ({
  feature: undefined as MapFeature | null | undefined,
  isLoading: false,
  focused: false,
}));

vi.mock("../map/useEntityFeature", () => ({
  useEntityFeature: () => ({ feature: state.feature, isLoading: state.isLoading }),
}));
vi.mock("../map/mapFocus", () => ({
  focusOnMap: vi.fn(() => state.focused),
}));
// Leaflet needs a laid-out container: the map itself is not what these tests look at.
vi.mock("./GeometryEditor", () => ({
  GeometryEditor: ({ wkt, focusSource }: { wkt: string | null; focusSource: { placeIds: string[]; projectId?: string } }) => (
    <div className="geometry-editor-stub">{`${wkt != null}:${focusSource.placeIds.join(",")}:${focusSource.projectId}`}</div>
  ),
}));
vi.mock("../map/FeatureMap", () => ({
  FeatureMap: ({ features }: { features: MapFeature[] }) => <div className="feature-map-stub">{features.map((f) => f.label).join(",")}</div>,
}));

const POLYGON = "POLYGON ((0 0, 1 0, 1 1, 0 0))";

let root: Root | undefined;
let host: HTMLElement | undefined;

beforeEach(() => {
  state.feature = { id: "12", label: "UE-12", geometry: { type: "Polygon", coordinates: [[[0, 0], [1, 0], [1, 1], [0, 0]]] } };
  state.isLoading = false;
  state.focused = false;
});

afterEach(() => {
  act(() => root?.unmount());
  host?.remove();
});

function renderField(overrides: { wkt?: string | null; editable?: boolean } = {}) {
  host = document.createElement("div");
  document.body.appendChild(host);
  root = createRoot(host);
  const wkt = overrides.wkt === undefined ? POLYGON : overrides.wkt;
  act(() =>
    root!.render(
      <GeometryMapField wkt={wkt} entityType="recordingUnit" entityId="12" focusSource={{ placeIds: ["7"], projectId: "5" }} editable={overrides.editable ?? false} onSaved={() => {}} />,
    ),
  );
  return host;
}

function editButton(container: HTMLElement) {
  return container.querySelector<HTMLButtonElement>(".field-geometry-edit");
}

function chip(container: HTMLElement) {
  return container.querySelector<HTMLButtonElement>(".field-geometry-chip")!;
}

describe("GeometryMapField", () => {
  it("shows only a chip naming the geometry's type", () => {
    const container = renderField();
    expect(chip(container).textContent).toBe("Polygone");
    expect(container.querySelector(".field-geometry-map-panel")).toBeNull();
  });

  it("opens a closable map of the shape below the chip", () => {
    const container = renderField();
    act(() => chip(container).click());
    expect(container.querySelector(".feature-map-stub")?.textContent).toBe("UE-12");

    act(() => container.querySelector<HTMLButtonElement>(".field-geometry-map-close")!.click());
    expect(container.querySelector(".field-geometry-map-panel")).toBeNull();
  });

  it("zooms the list's map onto the shape instead when one shows it", () => {
    state.focused = true;
    const container = renderField();
    act(() => chip(container).click());
    expect(container.querySelector(".field-geometry-map-panel")).toBeNull();
  });

  it("says why a geometry PostGIS can't place has no map, and shows its WKT", () => {
    state.feature = null;
    const container = renderField();
    act(() => chip(container).click());
    expect(container.querySelector(".feature-map-stub")).toBeNull();
    expect(container.textContent).toContain(POLYGON);
  });

  it("offers no drawing to a user who can't edit", () => {
    expect(editButton(renderField())).toBeNull();
  });

  it("offers to draw the shape of an entity that has none yet", () => {
    const container = renderField({ wkt: null, editable: true });
    expect(chip(container)).toBeNull();
    expect(editButton(container)!.textContent).toContain("Dessiner la forme");

    act(() => editButton(container)!.click());
    expect(container.querySelector(".geometry-editor-stub")?.textContent).toBe("false:7:5");
  });

  it("edits an existing shape from the pencil next to its chip", () => {
    const container = renderField({ editable: true });
    act(() => editButton(container)!.click());
    expect(container.querySelector(".geometry-editor-stub")?.textContent).toBe("true:7:5");
    expect(container.querySelector(".feature-map-stub")).toBeNull();
  });
});
