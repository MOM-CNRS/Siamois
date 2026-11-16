import {describe, expect, it} from "vitest";
import {boundsArea, boundsOf} from "./geoBounds";

describe("boundsOf", () => {
  it("spans every vertex of a multipolygon", () => {
    expect(boundsOf({ type: "MultiPolygon", coordinates: [[[[0, 0], [1, 0], [1, 1], [0, 0]]], [[[3, -2], [4, -2], [4, 5], [3, -2]]]] }))
      .toEqual([0, -2, 4, 5]);
  });

  it("reduces a point to itself", () => {
    expect(boundsOf({ type: "Point", coordinates: [2.3, 48.8] })).toEqual([2.3, 48.8, 2.3, 48.8]);
  });

  it("has none for an empty geometry", () => {
    expect(boundsOf({ type: "GeometryCollection", geometries: [] })).toBeNull();
  });
});

describe("boundsArea", () => {
  it("multiplies the spans", () => {
    expect(boundsArea([0, 0, 2, 3])).toBe(6);
  });
});
