import { describe, expect, it } from "vitest";
import { resolveValueBinding, toAnswerInput, unwrapAnswer, type FieldResource } from "./types";

function field(overrides: Partial<FieldResource>): FieldResource {
  return {
    id: "1",
    resourceType: "fields",
    label: "Field",
    answerType: "TEXT",
    isSystemField: true,
    ...overrides,
  };
}

describe("unwrapAnswer", () => {
  it("passes a raw value straight through", () => {
    expect(unwrapAnswer("plain")).toBe("plain");
    expect(unwrapAnswer(null)).toBeNull();
    expect(unwrapAnswer({ id: "7", resourceType: "concepts", label: "En cours" }))
      .toEqual({ id: "7", resourceType: "concepts", label: "En cours" });
  });

  it("unwraps a single-valued FieldAnswer envelope", () => {
    expect(unwrapAnswer({ answerType: "TEXT", field: {}, value: "hello" })).toBe("hello");
  });

  it("unwraps a multi-valued FieldAnswer envelope", () => {
    expect(unwrapAnswer({ answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE", field: {}, values: [1, 2] }))
      .toEqual([1, 2]);
  });
});

describe("resolveValueBinding", () => {
  it("reads a system field from a fixed entity property path", () => {
    const binding = resolveValueBinding(field({ isSystemField: true, valueBinding: "name" }));
    expect(binding.read({ name: "Project A" })).toBe("Project A");
  });

  it("writes a system field to a fixed entity property path, without mutating the original", () => {
    const binding = resolveValueBinding(field({ isSystemField: true, valueBinding: "name" }));
    const entity = { name: "old" };
    const updated = binding.write(entity, "new");
    expect(updated).toEqual({ name: "new" });
    expect(entity.name).toBe("old");
  });

  // The answer map's key is the field id on every producer — ProjectAnswersProjector and
  // RecordingUnitOpenApiService#toFieldsMap both do put(String.valueOf(field.getId()), …), and
  // RecordingUnitPatchRequest reads the same namespace back. valueBinding names a property on the
  // entity, which is a different namespace entirely.
  it("reads from the entity's answer map keyed by field id, not by valueBinding", () => {
    const binding = resolveValueBinding(field({ id: "-118", isSystemField: false, valueBinding: "status" }));
    expect(binding.read({ answers: { "-118": "hello" } })).toBe("hello");
  });

  it("writes a non-system field into the answer map, preserving other keys", () => {
    const binding = resolveValueBinding(field({ id: "-118", isSystemField: false, valueBinding: "status" }));
    const entity = { answers: { "-119": "kept" } };
    const updated = binding.write(entity, "written");
    expect(updated).toEqual({ answers: { "-119": "kept", "-118": "written" } });
  });

  it("reads undefined from a non-system field when the entity has no answers yet", () => {
    const binding = resolveValueBinding(field({ isSystemField: false, valueBinding: "custom-1" }));
    expect(binding.read({})).toBeUndefined();
  });

  // A list row from ?fields=… carries answers; the flat detail response for the same entity does
  // not. The same binding has to serve both.
  it("prefers the answer map over the property path, and falls back to it when absent", () => {
    const binding = resolveValueBinding(field({ id: "-109", isSystemField: true, valueBinding: "oaCode" }));
    expect(binding.read({ oaCode: "flat", answers: { "-109": "projected" } })).toBe("projected");
    expect(binding.read({ oaCode: "flat" })).toBe("flat");
  });

  it("unwraps a FieldAnswer envelope coming from a recording-unit style answer map", () => {
    const binding = resolveValueBinding(field({ id: "7", isSystemField: false }));
    expect(binding.read({ answers: { "7": { answerType: "TEXT", field: {}, value: "wrapped" } } }))
      .toBe("wrapped");
  });

  // An explicit null in the map means "no value", and must not silently fall through to a stale
  // flat property.
  it("honours an explicit null in the answer map rather than falling back", () => {
    const binding = resolveValueBinding(field({ id: "-109", isSystemField: true, valueBinding: "oaCode" }));
    expect(binding.read({ oaCode: "flat", answers: { "-109": null } })).toBeNull();
  });
});


describe("toAnswerInput", () => {
  it("wraps a scalar as { value }", () => {
    expect(toAnswerInput(field({ answerType: "TEXT" }), "hello")).toEqual({ value: "hello" });
    expect(toAnswerInput(field({ answerType: "INTEGER" }), 42)).toEqual({ value: 42 });
  });

  it("wraps null as a scalar-clearing { value: null }", () => {
    expect(toAnswerInput(field({ answerType: "TEXT" }), null)).toEqual({ value: null });
  });

  it("extracts resourceId from a ResourceRef for a single-valued SELECT field", () => {
    const ref = { resourceId: "7", resourceType: "concepts", label: "En cours" };
    expect(toAnswerInput(field({ answerType: "SELECT_ONE_FROM_FIELD_CODE" }), ref)).toEqual({ value: "7" });
  });

  it("extracts id from a ResolvedConceptResource-shaped value (the flat-entity shape)", () => {
    const resolved = { resourceType: "concepts", id: "9", resolvedLabel: "Sondage" };
    expect(toAnswerInput(field({ answerType: "SELECT_ONE_FROM_FIELD_CODE" }), resolved)).toEqual({ value: "9" });
  });

  it("wraps an array of ResourceRefs as { values: [ids] } for a SELECT_MULTIPLE field", () => {
    const refs = [
      { resourceId: "1", resourceType: "concepts", label: "A" },
      { resourceId: "2", resourceType: "concepts", label: "B" },
    ];
    expect(toAnswerInput(field({ answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE" }), refs))
      .toEqual({ values: ["1", "2"] });
  });

  it("wraps a non-array/absent value as { values: [] } for a SELECT_MULTIPLE field (clears it)", () => {
    expect(toAnswerInput(field({ answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE" }), undefined))
      .toEqual({ values: [] });
    expect(toAnswerInput(field({ answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE" }), null))
      .toEqual({ values: [] });
  });
});
