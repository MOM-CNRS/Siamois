import {describe, expect, it} from "vitest";
import {declaredTypeOption} from "./useDeclaredTypes";

describe("declaredTypeOption", () => {
  it("carries what a concept suggestion shows: the link to the thesaurus, the alt labels, the definition and the parents", () => {
    const option = declaredTypeOption({
      id: "42",
      concept: {
        id: "42",
        resolvedLabel: "Couche",
        thesaurusUrl: "https://thesaurus.mom.fr/?idc=1234&idt=th1",
        altLabels: ["US", "Strate"],
        definition: "Une couche",
        parents: "Unité > Dépôt",
      },
    });

    expect(option).toEqual({
      id: "42",
      label: "Couche",
      concept: {
        thesaurusUrl: "https://thesaurus.mom.fr/?idc=1234&idt=th1",
        altLabels: ["US", "Strate"],
        definition: "Une couche",
        parents: "Unité > Dépôt",
      },
    });
  });

  it("is a bare option when the concept has nothing more than its label", () => {
    expect(declaredTypeOption({ id: "7", concept: { id: "7", resolvedLabel: "Fosse", altLabels: [] } })).toEqual({ id: "7", label: "Fosse" });
  });

  it("falls back on the type id when the type has no concept", () => {
    expect(declaredTypeOption({ id: "7" })).toEqual({ id: "7", label: "7" });
  });
});
