import { describe, expect, it } from "vitest";
import {
  createTableState,
  filtersToQueryParams,
} from "./tableState";

describe("createTableState", () => {
  it("defaults visibleColumns/filters", () => {
    expect(createTableState()).toEqual({
      v: 3,
      offset: 0,
      limit: 25,
      visibleColumns: [],
      filters: {},
    });
  });

  it("accepts overrides", () => {
    expect(createTableState({ sort: "name:asc" })).toEqual({
      v: 3,
      offset: 0,
      limit: 25,
      sort: "name:asc",
      visibleColumns: [],
      filters: {},
    });
  });
});

describe("filtersToQueryParams", () => {
  it("encodes a contains filter as a bare f.<key>", () => {
    const params = filtersToQueryParams({ name: { op: "contains", v: "fos" } });
    expect(params.get("f.name")).toBe("fos");
  });

  it("skips an empty contains value", () => {
    const params = filtersToQueryParams({ name: { op: "contains", v: "" } });
    expect(params.has("f.name")).toBe(false);
  });

  it("encodes an in filter as repeated f.<key> params", () => {
    const params = filtersToQueryParams({ status: { op: "in", v: ["12", "44"] } });
    expect(params.getAll("f.status")).toEqual(["12", "44"]);
  });

  it("encodes a range filter as f.<key>.from / f.<key>.to, omitting absent bounds", () => {
    const params = filtersToQueryParams({ zmin: { op: "range", from: "10", to: "40" } });
    expect(params.get("f.zmin.from")).toBe("10");
    expect(params.get("f.zmin.to")).toBe("40");

    const fromOnly = filtersToQueryParams({ zmin: { op: "range", from: "10" } });
    expect(fromOnly.get("f.zmin.from")).toBe("10");
    expect(fromOnly.has("f.zmin.to")).toBe(false);
  });
});
