import { describe, expect, it } from "vitest";
import { check, createdCount, indexStructure, selectAll, uncheck } from "./duplicateSelection";

//  1 (root)
//  ├─ 2
//  │  └─ 4
//  └─ 3
const nodes = [
  { id: 2, label: "UE-2", parentId: 1 },
  { id: 3, label: "UE-3", parentId: 1 },
  { id: 4, label: "UE-4", parentId: 2 },
];
const index = indexStructure(nodes, 1);

describe("duplicate selection", () => {
  it("checks a node alone, leaving what is under it to be chosen", () => {
    expect([...check(index, new Set(), 1, "2")].sort()).toEqual(["2"]);
  });

  it("checks the path down to a node, so it can be created under its parent's copy", () => {
    expect([...check(index, new Set(), 1, "4")].sort()).toEqual(["2", "4"]);
    expect([...check(index, new Set(), 1, "3")].sort()).toEqual(["3"]);
    const deep = indexStructure([...nodes, { id: 5, label: "UE-5", parentId: 4 }], 1);
    expect([...check(deep, new Set(), 1, "5")].sort()).toEqual(["2", "4", "5"]);
  });

  it("unchecks a node with everything under it, and leaves the rest", () => {
    const all = selectAll(nodes);

    expect([...uncheck(index, all, "2")].sort()).toEqual(["3"]);
  });

  it("counts the units created: root and selection, per copy", () => {
    expect(createdCount(0, 1)).toBe(1);
    expect(createdCount(2, 3)).toBe(9);
    expect(createdCount(2, 0)).toBe(3);
  });
});
