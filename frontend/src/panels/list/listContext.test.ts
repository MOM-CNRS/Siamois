import { beforeEach, describe, expect, it } from "vitest";
import { recallListContext, rememberListContext } from "./listContext";

beforeEach(() => window.sessionStorage.clear());

describe("listContext", () => {
  it("recalls where an entity was opened from, per entity type and id", () => {
    rememberListContext("project", 5, { params: { sort: "name:asc", search: "foui" }, index: 7 });

    expect(recallListContext("project", "5")).toEqual({ params: { sort: "name:asc", search: "foui" }, index: 7 });
    expect(recallListContext("project", 6)).toBeUndefined();
    expect(recallListContext("recordingUnit", 5)).toBeUndefined();
  });

  it("keeps the walk's earlier entries, so browser Back still knows them", () => {
    const params = { sort: "creationTime:desc" };
    rememberListContext("find", 1, { params, index: 0 });
    rememberListContext("find", 2, { params, index: 1 });

    expect(recallListContext("find", 1)?.index).toBe(0);
    expect(recallListContext("find", 2)?.index).toBe(1);
  });

  it("lets the latest opening of an entity win", () => {
    rememberListContext("find", 1, { params: { sort: "a:asc" }, index: 3 });
    rememberListContext("find", 1, { params: { sort: "b:desc" }, index: 9 });

    expect(recallListContext("find", 1)).toEqual({ params: { sort: "b:desc" }, index: 9 });
  });

  it("forgets the oldest entries past its cap", () => {
    for (let id = 0; id < 60; id++) rememberListContext("find", id, { params: {}, index: id });

    expect(recallListContext("find", 0)).toBeUndefined();
    expect(recallListContext("find", 59)?.index).toBe(59);
    expect(recallListContext("find", 20)?.index).toBe(20);
  });

  it("reads corrupt or foreign storage as nothing remembered", () => {
    window.sessionStorage.setItem("siamois.listContext.v1", "{nope");
    expect(recallListContext("find", 1)).toBeUndefined();

    window.sessionStorage.setItem("siamois.listContext.v1", JSON.stringify({ v: 2, entries: {} }));
    expect(recallListContext("find", 1)).toBeUndefined();

    window.sessionStorage.setItem(
      "siamois.listContext.v1",
      JSON.stringify({ v: 1, entries: { "find:1": { index: -3, params: {} }, "find:2": { index: 1 } } }),
    );
    expect(recallListContext("find", 1)).toBeUndefined();
    expect(recallListContext("find", 2)).toBeUndefined();
  });
});
