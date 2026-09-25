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

  it("renders the type label and the place number, empty when absent", () => {
    const type = placeColumns.find((c) => c.key === "type")!;
    const number = placeColumns.find((c) => c.key === "placeNumber")!;

    expect(type.render(row)).toBe("Grotte");
    expect(number.render(row)).toBe("3");
    expect(type.render({ resourceType: "places", id: "6" })).toBe("");
    expect(number.render({ resourceType: "places", id: "6" })).toBe("");
  });
});
