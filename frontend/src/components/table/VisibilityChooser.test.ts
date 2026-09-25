import { describe, expect, it } from "vitest";
import { moveItem } from "./VisibilityChooser";

describe("moveItem", () => {
  it("moves one item to another index", () => {
    expect(moveItem(["a", "b", "c"], 0, 2)).toEqual(["b", "c", "a"]);
    expect(moveItem(["a", "b", "c"], 2, 0)).toEqual(["c", "a", "b"]);
  });

  it("ignores out-of-range or no-op moves", () => {
    expect(moveItem(["a", "b"], 0, 5)).toEqual(["a", "b"]);
    expect(moveItem(["a", "b"], 1, 1)).toEqual(["a", "b"]);
  });
});
