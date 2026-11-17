import {describe, expect, it} from "vitest";
import {createTableState, filtersToQueryParams,} from "./tableState";

describe("createTableState", () => {
  it("defaults visibleColumns/filters", () => {
    expect(createTableState()).toEqual({
      v: 3,
      offset: 0,
      limit: 100,
      visibleColumns: [],
      filters: {},
    });
  });

  it("accepts overrides", () => {
    expect(createTableState({ sort: "name:asc" })).toEqual({
      v: 3,
      offset: 0,
      limit: 100,
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

  it("names a non-default operator in f.<key>.op, and leaves a default one out", () => {
    const params = filtersToQueryParams({
      "-12": { op: "contains", v: "ab", operator: "startsWith" },
      "-13": { op: "in", v: ["4"], operator: "notIn" },
      "-14": { op: "contains", v: "x", operator: "contains" },
    });
    expect(params.get("f.-12")).toBe("ab");
    expect(params.get("f.-12.op")).toBe("starts_with");
    expect(params.get("f.-13.op")).toBe("not_in");
    expect(params.has("f.-14.op")).toBe(false);
  });

  it("sends an empty / not-empty filter as its operator alone", () => {
    const params = filtersToQueryParams({ "-12": { op: "empty" }, "-13": { op: "notEmpty" } });
    expect([...params.keys()]).toEqual(["f.-12.op", "f.-13.op"]);
    expect(params.get("f.-12.op")).toBe("empty");
    expect(params.get("f.-13.op")).toBe("not_empty");
  });

  it("sends a range operator as its bounds only", () => {
    const params = filtersToQueryParams({ "-12": { op: "range", from: "3", to: "3", operator: "eq" } });
    expect(params.get("f.-12.from")).toBe("3");
    expect(params.get("f.-12.to")).toBe("3");
    expect(params.has("f.-12.op")).toBe(false);
  });
});
