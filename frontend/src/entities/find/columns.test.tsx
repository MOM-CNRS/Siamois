import { describe, expect, it } from "vitest";
import { findColumns } from "./columns";
import type { FindSummary } from "./types";

describe("findColumns", () => {
  it("marks exactly one column as the identifier", () => {
    const identifierColumns = findColumns.filter((c) => c.identifier);
    expect(identifierColumns).toHaveLength(1);
    expect(identifierColumns[0].key).toBe("fullIdentifier");
  });

  it("renders the type's resolved label", () => {
    const row: FindSummary = {
      resourceType: "finds",
      id: "1",
      fullIdentifier: "INST-PROJ-M1",
      type: { resourceType: "concepts", id: "9", resolvedLabel: "Céramique" },
    };
    const typeCol = findColumns.find((c) => c.key === "type")!;
    expect(typeCol.render(row)).toBe("Céramique");
  });

  it("falls back to an empty string when type or collectionDate is absent", () => {
    const row: FindSummary = { resourceType: "finds", id: "1", fullIdentifier: "INST-PROJ-M1" };
    for (const col of findColumns) {
      if (col.key === "fullIdentifier") continue;
      expect(col.render(row)).toBe("");
    }
  });
});
