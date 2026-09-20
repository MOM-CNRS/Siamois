import { describe, expect, it } from "vitest";
import { resolveValueBinding, type FieldResource } from "./types";

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

  it("reads a non-system field from the entity's answer map, keyed by valueBinding", () => {
    const binding = resolveValueBinding(field({ isSystemField: false, valueBinding: "custom-1" }));
    expect(binding.read({ answers: { "custom-1": "hello" } })).toBe("hello");
  });

  it("falls back to the field id as the answer-map key when valueBinding is absent", () => {
    const binding = resolveValueBinding(field({ id: "42", isSystemField: false, valueBinding: null }));
    expect(binding.read({ answers: { "42": "value" } })).toBe("value");
  });

  it("writes a non-system field into the answer map, preserving other keys", () => {
    const binding = resolveValueBinding(field({ isSystemField: false, valueBinding: "custom-1" }));
    const entity = { answers: { "custom-2": "kept" } };
    const updated = binding.write(entity, "written");
    expect(updated).toEqual({ answers: { "custom-2": "kept", "custom-1": "written" } });
  });

  it("reads undefined from a non-system field when the entity has no answers yet", () => {
    const binding = resolveValueBinding(field({ isSystemField: false, valueBinding: "custom-1" }));
    expect(binding.read({})).toBeUndefined();
  });
});
