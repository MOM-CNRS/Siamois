import { describe, expect, it } from "vitest";
import { phaseColumns } from "./columns";
import type { PhaseSummary } from "./types";

describe("phaseColumns", () => {
  it("marks exactly one column as the identifier", () => {
    const identifierColumns = phaseColumns.filter((c) => c.identifier);
    expect(identifierColumns).toHaveLength(1);
    expect(identifierColumns[0].key).toBe("identifier");
  });

  it("renders the label (title-or-identifier), the type's resolved label, and the title", () => {
    const row: PhaseSummary = {
      resourceType: "phases",
      id: "1",
      identifier: "PH1",
      label: "Phase titre",
      title: "Phase titre",
      type: { resourceType: "concepts", id: "9", resolvedLabel: "Comblement" },
    };
    const byKey = Object.fromEntries(phaseColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("Phase titre");
    expect(byKey.type).toBe("Comblement");
    expect(byKey.title).toBe("Phase titre");
  });

  it("falls back to identifier when label is missing, and empty strings for type/title", () => {
    const row: PhaseSummary = { resourceType: "phases", id: "1", identifier: "PH1" };
    const byKey = Object.fromEntries(phaseColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("PH1");
    expect(byKey.type).toBe("");
    expect(byKey.title).toBe("");
  });
});
