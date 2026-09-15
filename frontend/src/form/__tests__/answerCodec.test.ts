import { describe, expect, it } from "vitest";
import { toAnswerInput, toLocalValue } from "../answerCodec";
import type { FieldAnswer, FieldResource } from "../schema";

function field(answerType: FieldResource["answerType"]): FieldResource {
  return { id: "1", resourceType: "fields", label: "L", answerType, isSystemField: false };
}

describe("toLocalValue", () => {
  it("reads a scalar FieldAnswer's value", () => {
    const answer: FieldAnswer = { answerType: "TEXT", field: field("TEXT"), value: "hello" };
    expect(toLocalValue(answer)).toBe("hello");
  });

  it("reads a select-many FieldAnswer's values array", () => {
    const answer: FieldAnswer = {
      answerType: "SELECT_MULTIPLE_PHASE",
      field: field("SELECT_MULTIPLE_PHASE"),
      values: [{ resourceId: "1", resourceType: "phases" }],
    };
    expect(toLocalValue(answer)).toEqual([{ resourceId: "1", resourceType: "phases" }]);
  });

  it("returns null for a null scalar value", () => {
    const answer: FieldAnswer = { answerType: "INTEGER", field: field("INTEGER"), value: null };
    expect(toLocalValue(answer)).toBeNull();
  });
});

describe("toAnswerInput", () => {
  it("passes scalar values through as-is", () => {
    expect(toAnswerInput("abc", "TEXT")).toEqual({ value: "abc" });
    expect(toAnswerInput(42, "INTEGER")).toEqual({ value: 42 });
    expect(toAnswerInput(null, "TEXT")).toEqual({ value: null });
  });

  it("clears a select-one field with value:null", () => {
    expect(toAnswerInput(null, "SELECT_ONE_SPATIAL_UNIT")).toEqual({ value: null });
  });

  it("sends the resourceId for a select-one field", () => {
    const ref = { resourceId: "42", resourceType: "spatial-units", label: "Trench A" };
    expect(toAnswerInput(ref, "SELECT_ONE_SPATIAL_UNIT")).toEqual({ value: "42" });
  });

  it("clears a select-many field with values:[]", () => {
    expect(toAnswerInput(null, "SELECT_MULTIPLE_PHASE")).toEqual({ values: [] });
    expect(toAnswerInput([], "SELECT_MULTIPLE_PHASE")).toEqual({ values: [] });
  });

  it("sends resourceIds for a select-many field", () => {
    const refs = [
      { resourceId: "1", resourceType: "phases" },
      { resourceId: "2", resourceType: "phases" },
    ];
    expect(toAnswerInput(refs, "SELECT_MULTIPLE_PHASE")).toEqual({ values: ["1", "2"] });
  });

  it("round-trips a measurement value", () => {
    const measurement = { numericValue: 12.5, symbol: "cm", normalizedValue: 0.125, comment: "approx" };
    expect(toAnswerInput(measurement, "MEASUREMENT")).toEqual({
      value: { value: 12.5, unit: "cm", comment: "approx" },
    });
  });

  it("clears a measurement field with value:null", () => {
    expect(toAnswerInput(null, "MEASUREMENT")).toEqual({ value: null });
  });
});
