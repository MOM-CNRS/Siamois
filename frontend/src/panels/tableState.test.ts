import { describe, expect, it } from "vitest";
import {
  createTableState,
  decodeTableState,
  encodeTableState,
  filtersToQueryParams,
  type TableState,
} from "./tableState";

describe("createTableState", () => {
  it("defaults visibleColumns/filters", () => {
    expect(createTableState()).toEqual({
      v: 2,
      visibleColumns: [],
      filters: {},
    });
  });

  it("accepts overrides", () => {
    expect(createTableState({ sort: "name:asc" })).toEqual({
      v: 2,
      sort: "name:asc",
      visibleColumns: [],
      filters: {},
    });
  });
});

describe("encodeTableState / decodeTableState", () => {
  const state: TableState = {
    v: 2,
    sort: "name:desc",
    search: "fouillé été", // non-ASCII, exercises the UTF-8 round trip
    visibleColumns: ["-118", "-109"],
    filters: { name: { op: "contains", v: "fos" } },
  };

  it("round-trips a full state", () => {
    expect(decodeTableState(encodeTableState(state))).toEqual(state);
  });

  it("produces a URL-safe string (no +, / or padding =)", () => {
    const encoded = encodeTableState(state);
    expect(encoded).not.toMatch(/[+/=]/);
  });

  it("returns null for garbage input rather than throwing", () => {
    expect(decodeTableState("not valid base64url json!!")).toBeNull();
  });

  it("returns null for a differently-versioned or malformed state", () => {
    const badVersion = btoa(JSON.stringify({ ...state, v: 2 }));
    expect(decodeTableState(badVersion)).toBeNull();

    const missingFields = btoa(JSON.stringify({ v: 1, offset: 0 }));
    expect(decodeTableState(missingFields)).toBeNull();
  });

  it("rejects a filters map with an unrecognized shape", () => {
    const bad = btoa(JSON.stringify({
      v: 1,
      offset: 0,
      limit: 10,
      visibleColumns: [],
      filters: { name: { op: "bogus" } },
    }));
    expect(decodeTableState(bad)).toBeNull();
  });

  it("round-trips every filter kind", () => {
    const s: TableState = createTableState({
      filters: {
        name: { op: "contains", v: "fos" },
        status: { op: "in", v: ["12", "44"] },
        zmin: { op: "range", from: "10", to: "40" },
      },
    });
    expect(decodeTableState(encodeTableState(s))).toEqual(s);
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

describe("decodeTableState across versions", () => {
  // v1 carried offset/limit (paginated list); the list now virtual-scrolls, so an old saved view or
  // ?s= must start over rather than be half-applied.
  it("rejects a v1 (paginated) state", () => {
    const v1 = { v: 1, offset: 20, limit: 25, visibleColumns: [], filters: {} };
    const encoded = btoa(JSON.stringify(v1)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    expect(decodeTableState(encoded)).toBeNull();
  });
});
