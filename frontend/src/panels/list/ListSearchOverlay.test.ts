import {describe, expect, it} from "vitest";
import type {FilterSpec} from "../../components/table/FilterChipBar";
import {keywordsOf} from "./ListSearchOverlay";

const options = async () => [];

describe("keywordsOf", () => {
  it("names a keyword after its column, without accents or spaces", () => {
    const specs: FilterSpec[] = [{ key: "name", label: "Désignation de l'UE", kind: "contains" }];
    expect(keywordsOf(specs)).toEqual([{ key: "name", names: ["designation-de-l-ue"] }]);
  });

  it("keeps the filters a keyword can express: text, and options to suggest", () => {
    const specs: FilterSpec[] = [
      { key: "a", label: "Nom", kind: "contains" },
      { key: "b", label: "Type", kind: "concept-one", loadOptions: options },
      { key: "c", label: "Statut", kind: "concept-one" },
      { key: "d", label: "Date", kind: "date-range" },
      { key: "e", label: "Surface", kind: "range" },
    ];
    expect(keywordsOf(specs).map((k) => k.key)).toEqual(["a", "b"]);
  });

  it("drops a column whose name another one already took", () => {
    const specs: FilterSpec[] = [
      { key: "a", label: "Type", kind: "contains" },
      { key: "b", label: "type", kind: "contains" },
    ];
    expect(keywordsOf(specs).map((k) => k.key)).toEqual(["a"]);
  });
});
