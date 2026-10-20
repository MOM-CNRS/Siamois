import { describe, expect, it } from "vitest";
import { isSavedRelationListState } from "./useRelationList";

describe("isSavedRelationListState", () => {
  const ok = { filters: { kind: { op: "in", v: ["parents", "anterior"] } }, sort: "label:desc", limit: 50 };

  it("accepts what the list can still show", () => {
    expect(isSavedRelationListState(ok)).toBe(true);
    expect(isSavedRelationListState({ ...ok, filters: {} })).toBe(true);
  });

  it("refuses a kind, a sort column, a direction or a page size the list no longer has", () => {
    expect(isSavedRelationListState({ ...ok, filters: { kind: { op: "in", v: ["cousins"] } } })).toBe(false);
    expect(isSavedRelationListState({ ...ok, sort: "identifier:asc" })).toBe(false);
    expect(isSavedRelationListState({ ...ok, sort: "label:sideways" })).toBe(false);
    expect(isSavedRelationListState({ ...ok, limit: 500 })).toBe(false);
  });

  it("refuses anything that is not that shape", () => {
    expect(isSavedRelationListState(null)).toBe(false);
    expect(isSavedRelationListState("x")).toBe(false);
    expect(isSavedRelationListState({ sort: "label:asc", limit: 25 })).toBe(false);
  });
});
