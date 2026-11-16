import {describe, expect, it} from "vitest";
import type {MapFeature} from "../../../entities/shared/listApi";
import {summarizeMap} from "./mapSummary";

const feature = (id: string): MapFeature => ({ id, label: `UE-${id}`, geometry: { type: "Point", coordinates: [2, 48] } });

describe("summarizeMap", () => {
  it("counts the examined rows the server could not place", () => {
    expect(summarizeMap({ features: [feature("1")], meta: { total: 3, examined: 3 } }))
      .toEqual({ total: 3, examined: 3, withoutGeometry: 2, truncated: false, empty: false });
  });

  it("says the map is truncated when the result set holds more than was examined", () => {
    expect(summarizeMap({ features: [], meta: { total: 5000, examined: 2000 } }).truncated).toBe(true);
  });

  it("says an empty result set is empty", () => {
    expect(summarizeMap({ features: [], meta: { total: 0, examined: 0 } }))
      .toEqual({ total: 0, examined: 0, withoutGeometry: 0, truncated: false, empty: true });
  });
});
