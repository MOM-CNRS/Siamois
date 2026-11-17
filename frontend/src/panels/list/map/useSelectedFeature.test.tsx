import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import type {MapFeature} from "../../../entities/shared/listApi";
import {useEntityFeature} from "../../../map/useEntityFeature";
import {useSelectedFeature} from "./useSelectedFeature";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../../map/useEntityFeature", () => ({ useEntityFeature: vi.fn() }));

const point = (id: string): MapFeature => ({ id, label: `UE-${id}`, geometry: { type: "Point", coordinates: [2, 48] } });
const features = [point("1"), point("2")];
const fetched = point("99");

type Args = { features: readonly MapFeature[] | undefined; selectedId: string | number | undefined; geometry?: string };

let latest: MapFeature | undefined;
let root: Root | undefined;

function Probe({ features: f, selectedId, geometry }: Readonly<Args>) {
  latest = useSelectedFeature("recordingUnit", f, selectedId, geometry);
  return null;
}

function render(args: Args) {
  act(() => root!.render(<Probe {...args} />));
}

// The arguments of the last single-entity request: (entityType, id, enabled, geometry).
function lastRequest() {
  return vi.mocked(useEntityFeature).mock.lastCall;
}

beforeEach(() => {
  vi.mocked(useEntityFeature).mockReset().mockReturnValue({ feature: fetched, isLoading: false });
  root = createRoot(document.createElement("div"));
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
});

describe("useSelectedFeature", () => {
  it("takes the feature from the map when the result set placed it, without asking for it", () => {
    render({ features, selectedId: "2" });

    expect(latest).toBe(features[1]);
    expect(lastRequest()).toEqual(["recordingUnit", "2", false, undefined]);
  });

  it("matches a numeric overview id against the features' string ids", () => {
    render({ features, selectedId: 1 });

    expect(latest).toBe(features[0]);
    expect(lastRequest()?.[2]).toBe(false);
  });

  it("asks for the fiche on its own when the result set left it out", () => {
    render({ features, selectedId: "99", geometry: "-4" });

    expect(lastRequest()).toEqual(["recordingUnit", "99", true, "-4"]);
    expect(latest).toBe(fetched);
  });

  it("selects nothing when the fiche asked for has no placeable geometry", () => {
    vi.mocked(useEntityFeature).mockReturnValue({ feature: null, isLoading: false });

    render({ features, selectedId: "99" });

    expect(latest).toBeUndefined();
  });

  it("selects nothing while the fiche asked for is loading", () => {
    vi.mocked(useEntityFeature).mockReturnValue({ feature: undefined, isLoading: true });

    render({ features, selectedId: "99" });

    expect(latest).toBeUndefined();
  });

  it("waits for the map to load before deciding the fiche is missing", () => {
    render({ features: undefined, selectedId: "99" });

    expect(lastRequest()?.[2]).toBe(false);
    expect(latest).toBeUndefined();
  });

  it("selects nothing when no fiche is open", () => {
    render({ features, selectedId: undefined });

    expect(lastRequest()).toEqual(["recordingUnit", undefined, false, undefined]);
    expect(latest).toBeUndefined();
  });

  it("treats an empty map as leaving the fiche out", () => {
    render({ features: [], selectedId: "1" });

    expect(lastRequest()?.[2]).toBe(true);
    expect(latest).toBe(fetched);
  });

  it("ignores a stale single-entity answer once the map places the fiche", () => {
    render({ features, selectedId: "3" });
    expect(latest).toBe(fetched);

    const withIt = [...features, point("3")];
    render({ features: withIt, selectedId: "3" });

    expect(latest).toBe(withIt[2]);
    expect(lastRequest()?.[2]).toBe(false);
  });

  it("follows the overview to another fiche", () => {
    render({ features, selectedId: "1" });
    render({ features, selectedId: "2" });

    expect(latest).toBe(features[1]);
  });
});
