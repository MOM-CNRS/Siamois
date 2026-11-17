import {describe, expect, it} from "vitest";
import type {MapFeature} from "../../../entities/shared/listApi";
import {mapWalkContext} from "./mapWalk";

const point = (id: string): MapFeature => ({ id, label: `UE-${id}`, geometry: { type: "Point", coordinates: [2, 48] } });
const features = [point("4"), point("9"), point("2")];
const params = { organizationId: 3, search: "fosse", sort: "identifier:asc", offset: 40, limit: 50 } as never;

describe("mapWalkContext", () => {
  it("walks the map's result set in its order, from the feature's position", () => {
    expect(mapWalkContext(params, undefined, features, "9")).toEqual({
      params: { organizationId: 3, search: "fosse", filters: undefined, scope: undefined, sort: "identifier:asc" },
      index: 1,
    });
  });

  it("matches a numeric id against the features' string ids", () => {
    expect(mapWalkContext(params, undefined, features, 2)?.index).toBe(2);
  });

  it("keeps to the rows the map can place: a value in the geometry column drawn", () => {
    const context = mapWalkContext({ organizationId: 3, filters: { "-1": { op: "in", v: ["4"] } } }, "-9", features, "4");

    expect(context?.params.filters).toEqual({ "-1": { op: "in", v: ["4"] }, "-9": { op: "notEmpty" } });
  });

  it("leaves the user's own filter on the geometry column as it is", () => {
    const filters = { "-9": { op: "notEmpty" as const } };

    expect(mapWalkContext({ organizationId: 3, filters }, "-9", features, "4")?.params.filters).toBe(filters);
  });

  it("gives no walk for a fiche the map does not show", () => {
    expect(mapWalkContext(params, "-9", features, "77")).toBeUndefined();
  });
});
