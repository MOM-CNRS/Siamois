import { describe, expect, it } from "vitest";
import { placeColumns } from "./columns";
import type { PlaceSummary } from "./types";

const row: PlaceSummary = {
  resourceType: "places",
  id: "5",
  name: "Cave A",
  placeNumber: 3,
  type: { resourceType: "concepts", id: "9", resolvedLabel: "Grotte" },
};

describe("placeColumns", () => {
  it("makes the name the one identifier column, sortable", () => {
    const identifiers = placeColumns.filter((c) => c.identifier);
    expect(identifiers.map((c) => c.key)).toEqual(["name"]);
    expect(identifiers[0].sortable).toBe(true);
    expect(identifiers[0].render(row)).toBe("Cave A");
  });

  it("pins nothing else: the type, code and place number are catalog columns", () => {
    expect(placeColumns.map((c) => c.key)).toEqual(["name"]);
  });

  it("renders an empty name as empty", () => {
    expect(placeColumns[0].render({ resourceType: "places", id: "6" })).toBe("");
  });
});
