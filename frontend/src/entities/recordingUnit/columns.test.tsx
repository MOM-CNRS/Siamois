import { describe, expect, it } from "vitest";
import { recordingUnitColumns } from "./columns";
import type { RecordingUnitSummary } from "./types";

describe("recordingUnitColumns", () => {
  it("marks exactly one column as the identifier", () => {
    const identifierColumns = recordingUnitColumns.filter((c) => c.identifier);
    expect(identifierColumns).toHaveLength(1);
    expect(identifierColumns[0].key).toBe("fullIdentifier");
  });

  // Parents, children, stratigraphic relationships and finds are catalog fields now (answers), not
  // pinned count columns.
  it("has no pinned relation count column", () => {
    expect(recordingUnitColumns.map((c) => c.key)).toEqual(["fullIdentifier"]);
  });

  // The project is the catalog's own (read-only) project field column now, not a pinned one.
  it("has no pinned Projet column", () => {
    expect(recordingUnitColumns.find((c) => c.key === "project")).toBeUndefined();
  });

  it("falls back to identifier when fullIdentifier is missing", () => {
    const identifierCol = recordingUnitColumns.find((c) => c.identifier)!;
    const row: RecordingUnitSummary = { resourceType: "recording-units", id: "1", fullIdentifier: "", identifier: "UE1" };
    expect(identifierCol.render(row)).toBe("UE1");
  });
});
