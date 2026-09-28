import { describe, expect, it } from "vitest";
import { isFieldPending, mergeSupplementRows, missingFields } from "./usePagedList";

describe("mergeSupplementRows", () => {
  const base: { id: string; name: string; answers: Record<string, unknown> }[] = [
    { id: "1", name: "A", answers: { x: 1 } },
    { id: "2", name: "B", answers: { x: 2 } },
  ];

  it("adds each supplement's answers to the matching base row, by id", () => {
    const merged = mergeSupplementRows(base, [{ fieldId: "y", rows: [{ id: "2", name: "stale", answers: { y: "b" } }] }]);
    expect(merged[0]).toBe(base[0]);
    // The base row stays authoritative for its own properties.
    expect(merged[1]).toEqual({ id: "2", name: "B", answers: { x: 2, y: "b" } });
  });

  it("marks a still-loading supplement's field pending on every row", () => {
    const merged = mergeSupplementRows(base, [{ fieldId: "y", rows: undefined }]);
    expect(isFieldPending(merged[0], "y")).toBe(true);
    expect(isFieldPending(merged[0], "x")).toBe(false);
  });

  it("returns the base rows untouched when there's nothing to merge", () => {
    expect(mergeSupplementRows(base, [])).toBe(base);
  });
});

describe("missingFields", () => {
  it("lists the visible fields a page wasn't fetched with", () => {
    expect(missingFields(["a", "b", "c"], ["a", "c"])).toEqual(["b"]);
    expect(missingFields(["a"], ["a", "b"])).toEqual([]);
  });
});
