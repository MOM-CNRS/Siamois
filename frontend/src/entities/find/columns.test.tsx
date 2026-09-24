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

  it("has a UE column linking to the find's recording unit, in scoped and unscoped lists alike", () => {
    const col = findColumns.find((c) => c.key === "recordingUnit")!;
    const row: FindSummary = {
      resourceType: "finds",
      id: "1",
      fullIdentifier: "INST-PROJ-M1",
      recordingUnit: { resourceType: "recording-units", id: "42", fullIdentifier: "INST-PROJ-US42" },
    };

    expect(col.unscopedOnly).toBeFalsy();
    expect(col.render(row)).toBe("INST-PROJ-US42");
    expect(col.link!(row)).toEqual({ entityType: "recordingUnit", id: "42" });
    expect(col.link!({ resourceType: "finds", id: "2", fullIdentifier: "M2" })).toBeNull();
  });

  it("links the Projet column to the row's project", () => {
    const col = findColumns.find((c) => c.key === "project")!;
    const row: FindSummary = {
      resourceType: "finds",
      id: "1",
      fullIdentifier: "M1",
      project: { resourceId: "7", resourceType: "projects", label: "OA-7" },
    };

    expect(col.link!(row)).toEqual({ entityType: "project", id: "7" });
  });
});
