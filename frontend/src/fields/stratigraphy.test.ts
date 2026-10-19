import { describe, expect, it } from "vitest";
import { stratigraphyPositionOf } from "./stratigraphy";
import type { FieldResource } from "./types";

const field = (answerType: string, valueBinding?: string) => ({ id: "1", resourceType: "fields", label: "f", answerType, isSystemField: true, valueBinding }) as FieldResource;

describe("stratigraphyPositionOf", () => {
  it.each([
    ["anteriorRelationships", "anterior"],
    ["posteriorRelationships", "posterior"],
    ["synchronousRelationships", "synchronous"],
  ])("reads where the units of %s stand", (binding, position) => {
    expect(stratigraphyPositionOf(field("SELECT_MULTIPLE_STRATIGRAPHY", binding))).toBe(position);
  });

  it("is undefined for any other field, even one bound like a side", () => {
    expect(stratigraphyPositionOf(field("SELECT_MULTIPLE_STRATIGRAPHY", "other"))).toBeUndefined();
    expect(stratigraphyPositionOf(field("SELECT_MULTIPLE_RECORDING_UNIT", "anteriorRelationships"))).toBeUndefined();
  });
});
