import { describe, expect, it } from "vitest";
import { containerColumns } from "./columns";
import type { ContainerSummary } from "./types";

describe("containerColumns", () => {
  it("marks exactly one column as the identifier", () => {
    const identifierColumns = containerColumns.filter((c) => c.identifier);
    expect(identifierColumns).toHaveLength(1);
    expect(identifierColumns[0].key).toBe("identifier");
  });

  it("renders the identifier and the type's resolved label", () => {
    const row: ContainerSummary = {
      resourceType: "containers",
      id: "1",
      identifier: "C1",
      type: { resourceType: "concepts", id: "9", resolvedLabel: "Caisse" },
    };
    const byKey = Object.fromEntries(containerColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("C1");
    expect(byKey.type).toBe("Caisse");
  });

  it("falls back to empty strings when identifier/type are absent", () => {
    const row: ContainerSummary = { resourceType: "containers", id: "1" };
    const byKey = Object.fromEntries(containerColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("");
    expect(byKey.type).toBe("");
  });
});
