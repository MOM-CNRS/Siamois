import { describe, expect, it } from "vitest";
import { recordingUnitColumns } from "./columns";
import type { RecordingUnitSummary } from "./types";

describe("recordingUnitColumns", () => {
  it("marks exactly one column as the identifier", () => {
    const identifierColumns = recordingUnitColumns.filter((c) => c.identifier);
    expect(identifierColumns).toHaveLength(1);
    expect(identifierColumns[0].key).toBe("fullIdentifier");
  });

  it("reads every count column from _counts, keyed the way RecordingUnitResourceCounts serializes them", () => {
    const row: RecordingUnitSummary = {
      resourceType: "recording-units",
      id: "1",
      fullIdentifier: "INST-PROJ-UE1",
      _counts: { parents: 2, children: 3, relationships: 4, finds: 5 },
    };

    const byKey = Object.fromEntries(recordingUnitColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.parentsCount).toBe(2);
    expect(byKey.childrenCount).toBe(3);
    expect(byKey.relationshipCount).toBe(4);
    expect(byKey.specimenCount).toBe(5);
  });

  it("falls back to 0 for every count column when _counts is absent", () => {
    const row: RecordingUnitSummary = { resourceType: "recording-units", id: "1", fullIdentifier: "INST-PROJ-UE1" };

    for (const col of recordingUnitColumns) {
      if (col.key === "fullIdentifier" || col.key === "project") continue;
      expect(col.render(row)).toBe(0);
    }
  });

  it("has a Projet column, shown only on the organization-wide list", () => {
    const col = recordingUnitColumns.find((c) => c.key === "project")!;
    const row: RecordingUnitSummary = {
      resourceType: "recording-units",
      id: "1",
      fullIdentifier: "OA-7-US1",
      project: { resourceId: "7", resourceType: "projects", label: "OA-7" },
    };

    expect(col.unscopedOnly).toBe(true);
    expect(col.render(row)).toBe("OA-7");
    expect(col.render({ resourceType: "recording-units", id: "2", fullIdentifier: "OA-7-US2" })).toBe("");
    expect(col.link!(row)).toEqual({ entityType: "project", id: "7" });
    expect(col.link!({ resourceType: "recording-units", id: "2", fullIdentifier: "OA-7-US2" })).toBeNull();
  });

  it("falls back to identifier when fullIdentifier is missing", () => {
    const identifierCol = recordingUnitColumns.find((c) => c.identifier)!;
    const row: RecordingUnitSummary = { resourceType: "recording-units", id: "1", fullIdentifier: "", identifier: "UE1" };
    expect(identifierCol.render(row)).toBe("UE1");
  });
});
