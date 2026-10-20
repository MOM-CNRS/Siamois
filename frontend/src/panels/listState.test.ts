import { describe, expect, it } from "vitest";
import { parsePersisted, toPersisted } from "./listState";
import { createTableState } from "./tableState";

describe("toPersisted", () => {
  it("keeps the limit, sort, filters and view mode, but neither the page nor the search", () => {
    const state = createTableState({
      offset: 50,
      limit: 50,
      sort: "name:asc",
      search: "abc",
      filters: { "f.x": { op: "contains", v: "a" } },
      viewMode: "tree",
    });
    expect(toPersisted(state, false)).toEqual({
      v: 1,
      limit: 50,
      sort: "name:asc",
      filters: { "f.x": { op: "contains", v: "a" } },
      viewMode: "tree",
    });
  });

  it("leaves the columns out until they are seeded, then keeps them, even none", () => {
    const state = createTableState({ visibleColumns: [] });
    expect(toPersisted(state, false)).not.toHaveProperty("visibleColumns");
    expect(toPersisted(state, true)).toHaveProperty("visibleColumns", []);
  });
});

describe("parsePersisted", () => {
  it("reads back what toPersisted wrote", () => {
    const state = createTableState({
      limit: 100,
      sort: "id:desc",
      visibleColumns: ["a", "b"],
      filters: { k: { op: "in", v: ["1", "2"] }, d: { op: "range", from: "2020-01-01" } },
      viewMode: "list",
    });
    expect(parsePersisted(toPersisted(state, true))).toEqual({
      limit: 100,
      sort: "id:desc",
      visibleColumns: ["a", "b"],
      filters: state.filters,
      viewMode: "list",
    });
  });

  it("reads nothing from an absent state or another version", () => {
    expect(parsePersisted(undefined)).toBeUndefined();
    expect(parsePersisted({ v: 2, limit: 50 })).toBeUndefined();
  });

  it("drops what is invalid and keeps the rest", () => {
    expect(
      parsePersisted({
        v: 1,
        limit: 500,
        sort: "",
        viewMode: "map",
        visibleColumns: ["a", 4],
        filters: { ok: { op: "contains", v: "x" }, bad: { op: "like", v: "x" }, worse: "text" },
      }),
    ).toEqual({ filters: { ok: { op: "contains", v: "x" } } });
  });
});
