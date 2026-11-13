import {describe, expect, it} from "vitest";
import type {FieldResource} from "../../../fields/types";
import type {FieldState} from "../../../rules";
import {bulkFieldState} from "./bulkFieldState";

/**
 * A field with these rules.
 */
function field(rules: FieldResource["rules"]): FieldResource {
  return { id: "place", resourceType: "field", label: "Lieu", answerType: "SELECT_ONE_SPATIAL_UNIT", isSystemField: true, rules };
}

/**
 * A row's state of the field.
 */
function state(extra: Partial<FieldState>): FieldState {
  return { enabled: true, required: false, incoherent: [], ...extra };
}

const insee = field({ placeSources: [{ params: {} }] } as FieldResource["rules"]);

describe("bulkFieldState", () => {
  it("searches the place sources (INSEE…) as the fiche does, even when no row carries a state", () => {
    expect(bulkFieldState(insee, [undefined], undefined).placeContext).toEqual({ deps: {} });
  });

  it("gives no place context to a field without place sources", () => {
    expect(bulkFieldState(field(null), [], undefined).placeContext).toBeUndefined();
  });

  it("narrows a source by another field's place only when every row has the same one", () => {
    const same = [state({ placeContext: { deps: { "7": "12" } } }), state({ placeContext: { deps: { "7": "12" } } })];
    const differing = [state({ placeContext: { deps: { "7": "12" } } }), state({ placeContext: { deps: { "7": "13" } } })];

    expect(bulkFieldState(insee, same, undefined).placeContext).toEqual({ deps: { "7": "12" } });
    expect(bulkFieldState(insee, differing, undefined).placeContext).toEqual({ deps: { "7": null } });
  });

  it("keeps the options the rows share, and drops those that differ or wait for a parent", () => {
    const related = { kind: "RELATED_CONCEPTS", parentFieldId: "3", relatedTo: "40" } as const;
    const other = { kind: "RELATED_CONCEPTS", parentFieldId: "3", relatedTo: "41" } as const;
    const waiting = { kind: "RELATED_CONCEPTS", parentFieldId: "3", relatedTo: null } as const;

    expect(bulkFieldState(insee, [state({ optionsContext: related }), state({ optionsContext: related })], undefined).optionsContext).toEqual(related);
    expect(bulkFieldState(insee, [state({ optionsContext: related }), state({ optionsContext: other })], undefined).optionsContext).toBeUndefined();
    expect(bulkFieldState(insee, [state({ optionsContext: waiting })], undefined).optionsContext).toBeUndefined();
  });

  it("narrows to the selection's type when the rules say so and no row carries a state", () => {
    const typed = field({ options: { kind: "RELATED_TO_TYPE" } } as FieldResource["rules"]);

    expect(bulkFieldState(typed, [], "9").optionsContext).toEqual({ kind: "RELATED_TO_TYPE", relatedTo: "9" });
  });

  it("searches no place source when the rows' forms give the field none, as on their fiche", () => {
    expect(bulkFieldState(insee, [state({})], undefined).placeContext).toBeUndefined();
  });
});
