import {describe, expect, it} from "vitest";
import {geometryKind} from "./geometryKind";

describe("geometryKind", () => {
  it("names a WKT by its geometry type", () => {
    expect(geometryKind("POLYGON ((0 0, 1 0, 1 1, 0 0))")).toEqual({ label: "map.kindPolygon", icon: "bi bi-pentagon", shape: "area" });
    expect(geometryKind("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))").label).toBe("map.kindMultiPolygon");
    expect(geometryKind("point (2 48)")).toEqual({ label: "map.kindPoint", icon: "bi bi-geo-alt", shape: "point" });
  });

  it("skips an EWKT SRID prefix", () => {
    expect(geometryKind("SRID=2154;LINESTRING (0 0, 1 1)").label).toBe("map.kindLineString");
  });

  it("falls back to a generic label", () => {
    expect(geometryKind("CIRCULARSTRING (0 0, 1 1, 2 0)").label).toBe("map.kindGeometry");
    expect(geometryKind("").label).toBe("map.kindGeometry");
  });
});
