import {describe, expect, it} from "vitest";
import {isFieldPending, pagesInRange, mergeSupplementRows, missingFields} from "./usePagedList";

describe("mergeSupplementRows", () => {
  const base: { id: string; name: string; answers: Record<string, unknown> }[] = [
    { id: "1", name: "A", answers: { x: 1 } },
    { id: "2", name: "B", answers: { x: 2 } },
  ];

  it("adds each supplement's answers to the matching base row, by id", () => {
    const merged = mergeSupplementRows(base, { fieldIds: ["y", "z"], rows: [{ id: "2", name: "stale", answers: { y: "b", z: 3 } }] });
    expect(merged[0]).toBe(base[0]);
    // The base row stays authoritative for its own properties.
    expect(merged[1]).toEqual({ id: "2", name: "B", answers: { x: 2, y: "b", z: 3 } });
  });

  it("marks a still-loading supplement's field pending on every row", () => {
    const merged = mergeSupplementRows(base, { fieldIds: ["y", "z"], rows: undefined });
    expect(isFieldPending(merged[0], "y")).toBe(true);
    expect(isFieldPending(merged[0], "z")).toBe(true);
    expect(isFieldPending(merged[0], "x")).toBe(false);
  });

  it("returns the base rows untouched when there's nothing to merge", () => {
    expect(mergeSupplementRows(base, null)).toBe(base);
  });
});

describe("missingFields", () => {
  it("lists the visible fields a page wasn't fetched with", () => {
    expect(missingFields(["a", "b", "c"], ["a", "c"])).toEqual(["b"]);
    expect(missingFields(["a"], ["a", "b"])).toEqual([]);
  });
});

describe("pagesInRange", () => {
  it("lists every page the scroller's window touches", () => {
    expect(pagesInRange(0, 44, 100)).toEqual([0]);
    expect(pagesInRange(180, 230, 100)).toEqual([1, 2]);
    expect(pagesInRange(200, 300, 100)).toEqual([2]);
  });

  it("always asks for at least the page holding the first row", () => {
    expect(pagesInRange(0, 0, 100)).toEqual([0]);
    expect(pagesInRange(250, 250, 100)).toEqual([2]);
  });
});
