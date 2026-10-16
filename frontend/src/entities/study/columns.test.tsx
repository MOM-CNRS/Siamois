import { describe, expect, it } from "vitest";
import { studyColumns } from "./columns";
import type { StudySummary } from "./types";

describe("studyColumns", () => {
  it("marks exactly one column as the identifier", () => {
    const identifierColumns = studyColumns.filter((c) => c.identifier);
    expect(identifierColumns).toHaveLength(1);
    expect(identifierColumns[0].key).toBe("identifier");
  });

  it("renders the identifier and the type's resolved label", () => {
    const row: StudySummary = {
      resourceType: "studies",
      id: "1",
      identifier: "ETU0001",
      type: { resourceType: "concepts", id: "9", resolvedLabel: "Traitement" },
    };
    const byKey = Object.fromEntries(studyColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("ETU0001");
    expect(byKey.type).toBe("Traitement");
  });

  it("falls back to empty strings when identifier/type are absent", () => {
    const row: StudySummary = { resourceType: "studies", id: "1" };
    const byKey = Object.fromEntries(studyColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("");
    expect(byKey.type).toBe("");
  });
});
