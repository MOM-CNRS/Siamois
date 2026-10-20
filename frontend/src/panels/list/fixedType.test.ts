import { describe, expect, it } from "vitest";
import { fixedTypeOf } from "./fixedType";

describe("fixedTypeOf", () => {
  it("is the one type an 'in' filter on the type field keeps", () => {
    expect(fixedTypeOf({ type: { op: "in", v: ["412"] } }, undefined)).toBe("412");
    expect(fixedTypeOf({ f12: { op: "in", v: ["7"] } }, { fields: { f12: { valueBinding: "type" } } })).toBe("7");
  });

  it("chooses nothing for several types, another field, another operator or no filter", () => {
    expect(fixedTypeOf({ type: { op: "in", v: ["1", "2"] } }, undefined)).toBeUndefined();
    expect(fixedTypeOf({ status: { op: "in", v: ["1"] } }, { fields: { status: { valueBinding: "status" } } })).toBeUndefined();
    expect(fixedTypeOf({ type: { op: "contains", v: "x" } }, undefined)).toBeUndefined();
    expect(fixedTypeOf(undefined, undefined)).toBeUndefined();
  });
});
