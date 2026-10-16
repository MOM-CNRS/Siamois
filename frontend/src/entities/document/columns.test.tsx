import { describe, expect, it } from "vitest";
import { documentColumns } from "./columns";
import type { DocumentSummary } from "./types";

describe("documentColumns", () => {
  it("marks exactly one column as the identifier", () => {
    const identifierColumns = documentColumns.filter((c) => c.identifier);
    expect(identifierColumns).toHaveLength(1);
    expect(identifierColumns[0].key).toBe("identifier");
  });

  it("renders the identifier, the type's resolved label, and the title", () => {
    const row: DocumentSummary = {
      resourceType: "documents",
      id: "1",
      identifier: "DOC1",
      label: "Document titre",
      title: "Document titre",
      type: { resourceType: "concepts", id: "9", resolvedLabel: "Comblement" },
    };
    const byKey = Object.fromEntries(documentColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("DOC1");
    expect(byKey.type).toBe("Comblement");
    expect(byKey.title).toBe("Document titre");
  });

  it("falls back to the label when identifier is missing, and empty strings for type/title", () => {
    const row: DocumentSummary = { resourceType: "documents", id: "1", label: "Plan" };
    const byKey = Object.fromEntries(documentColumns.map((c) => [c.key, c.render(row)]));
    expect(byKey.identifier).toBe("Plan");
    expect(byKey.type).toBe("");
    expect(byKey.title).toBe("");
  });
});
