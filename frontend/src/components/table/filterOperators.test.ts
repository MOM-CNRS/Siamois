import {describe, expect, it} from "vitest";
import {applyOperator, defaultOnly, OPERATOR_PROFILES, operatorLabel, operatorOf, profileOf, rangeWith, takesValue} from "./filterOperators";

describe("profileOf", () => {
  it("offers the text operators of the mock-up, in its order", () => {
    expect(profileOf("contains").operators).toEqual(["is", "isNot", "contains", "notContains", "startsWith", "endsWith", "empty", "notEmpty"]);
  });

  it("offers bounds to numbers and dates, any / none of to the pickers, presence alone to a geometry", () => {
    expect(profileOf("range").operators).toEqual(profileOf("date-range").operators);
    expect(profileOf("range").operators).toContain("between");
    for (const kind of ["in", "boolean", "concept-one", "concept-many", "spatial-one"] as const) {
      expect(profileOf(kind).operators).toEqual(["in", "notIn", "empty", "notEmpty"]);
    }
    expect(profileOf("presence").operators).toEqual(["empty", "notEmpty"]);
  });

  it("keeps what each kind always did as its default", () => {
    expect(profileOf("contains").defaultOperator).toBe("contains");
    expect(profileOf("date-range").defaultOperator).toBe("between");
    expect(profileOf("concept-many").defaultOperator).toBe("in");
    expect(profileOf("presence").defaultOperator).toBe("notEmpty");
  });

  it("offers a type no « empty », a relation « contains », « does not contain » and « is empty »", () => {
    expect(OPERATOR_PROFILES.type.operators).toEqual(["in", "notIn"]);
    const relation = OPERATOR_PROFILES.relation;
    expect(relation.operators.map((op) => operatorLabel(op, relation))).toEqual(["Contient", "Ne contient pas", "Est vide"]);
  });

  it("names a date's bounds as dates", () => {
    expect(operatorLabel("gte", OPERATOR_PROFILES.date)).toBe("Le ou après le");
    expect(operatorLabel("gte", OPERATOR_PROFILES.number)).toBe("Supérieur ou égal à");
  });

  it("narrows a profile to its default", () => {
    expect(defaultOnly(OPERATOR_PROFILES.text).operators).toEqual(["contains"]);
  });
});

describe("operatorOf", () => {
  it("reads the operator of a value, its shape's default without one, the fallback without value", () => {
    expect(operatorOf({ op: "contains", v: "a", operator: "endsWith" }, "contains")).toBe("endsWith");
    expect(operatorOf({ op: "contains", v: "a" }, "is")).toBe("contains");
    expect(operatorOf({ op: "range", from: "1" }, "eq")).toBe("between");
    expect(operatorOf({ op: "empty" }, "contains")).toBe("empty");
    expect(operatorOf(undefined, "startsWith")).toBe("startsWith");
  });
});

describe("applyOperator", () => {
  it("keeps the value under another operator of its family, a default one left out", () => {
    expect(applyOperator({ op: "contains", v: "ab" }, "startsWith")).toEqual({ op: "contains", v: "ab", operator: "startsWith" });
    expect(applyOperator({ op: "contains", v: "ab", operator: "isNot" }, "contains")).toEqual({ op: "contains", v: "ab" });
    expect(applyOperator({ op: "in", v: ["1"], labels: { 1: "Un" } }, "notIn")).toEqual({
      op: "in",
      v: ["1"],
      labels: { 1: "Un" },
      operator: "notIn",
    });
  });

  it("needs no value for empty / not empty, and drops the value they replace", () => {
    expect(applyOperator({ op: "contains", v: "ab" }, "empty")).toEqual({ op: "empty" });
    expect(applyOperator(undefined, "notEmpty")).toEqual({ op: "notEmpty" });
    expect(takesValue("empty")).toBe(false);
  });

  it("has nothing to keep without a value, or coming back from empty", () => {
    expect(applyOperator(undefined, "contains")).toBeUndefined();
    expect(applyOperator({ op: "empty" }, "is")).toBeUndefined();
  });

  it("reshapes the bounds of a range for « equals », « at least » and « at most »", () => {
    expect(applyOperator({ op: "range", from: "2", to: "5" }, "eq")).toEqual({ op: "range", from: "2", to: "2", operator: "eq" });
    expect(applyOperator({ op: "range", from: "2", to: "5" }, "gte")).toEqual({ op: "range", from: "2", operator: "gte" });
    expect(applyOperator({ op: "range", from: "2", to: "5" }, "lte")).toEqual({ op: "range", to: "5", operator: "lte" });
    expect(applyOperator({ op: "range", from: "2", to: "5", operator: "eq" }, "between")).toEqual({ op: "range", from: "2", to: "5" });
  });
});

describe("rangeWith", () => {
  it("is nothing without a bound left", () => {
    expect(rangeWith(undefined, undefined, "between")).toBeUndefined();
    expect(rangeWith(undefined, "4", "lte")).toEqual({ op: "range", to: "4", operator: "lte" });
    expect(rangeWith("4", undefined, "lte")).toEqual({ op: "range", to: "4", operator: "lte" });
  });
});
