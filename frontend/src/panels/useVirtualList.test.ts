import { describe, expect, it } from "vitest";
import { assembleRows, CHUNK_SIZE, chunksForRange, isPlaceholderRow } from "./useVirtualList";

describe("chunksForRange", () => {
  it("maps a row window to the aligned chunks covering it (last exclusive)", () => {
    expect(chunksForRange(0, 20)).toEqual([0]);
    expect(chunksForRange(0, CHUNK_SIZE)).toEqual([0]);
    expect(chunksForRange(40, 60)).toEqual([0, 1]);
    expect(chunksForRange(120, 130)).toEqual([2]);
  });

  it("falls back to one chunk from `first` when the scroller reports a NaN bound", () => {
    expect(chunksForRange(0, NaN)).toEqual([0]);
    expect(chunksForRange(NaN, NaN)).toEqual([0]);
  });
});

describe("assembleRows", () => {
  it("places each loaded chunk at its own offset and fills the gaps with placeholders", () => {
    const rows = assembleRows(7, [{ index: 1, rows: ["c", "d"] }], 2);
    expect(rows).toHaveLength(7);
    expect(rows.slice(2, 4)).toEqual(["c", "d"]);
    expect(rows.filter(isPlaceholderRow)).toHaveLength(5);
    // Placeholder ids are unique and can't collide with a real id.
    expect(new Set(rows.filter(isPlaceholderRow).map((r) => r.id)).size).toBe(5);
  });

  it("ignores a chunk whose rows overrun a shrunken totalCount", () => {
    expect(assembleRows(1, [{ index: 0, rows: ["a", "b"] }], 2)).toEqual(["a"]);
  });

  it("skips a chunk that hasn't loaded yet", () => {
    expect(assembleRows(2, [{ index: 0, rows: undefined }], 2).every(isPlaceholderRow)).toBe(true);
  });
});
