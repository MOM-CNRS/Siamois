import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it } from "vitest";
import type { FieldResource } from "../fields/types";
import type { FieldRules } from "../rules";
import { useRowRules, useRuleFields } from "./useRowRules";
import type { TypeRules } from "./useTypeRules";

// Two fields: #10 is greyed unless #20 equals 7 — but only for the type "A"; type "B" has no rule.
const fields = {
  "10": { id: "10", label: "Dependent" },
  "20": { id: "20", label: "Driver" },
} as unknown as Record<string, FieldResource>;

const greyedUnlessSeven: FieldRules = {
  enabledWhen: { fieldId: "20", op: "EQ", values: [7] },
};

const typeRules: TypeRules = {
  columnSets: [[{ fieldId: "10", rules: greyedUnlessSeven }, { fieldId: "20", rules: null }]],
  rulesOf: (row, fieldId) => (row.type?.id === "A" && fieldId === "10" ? greyedUnlessSeven : undefined),
};

const rowA = { id: 1, projectId: "p", type: { id: "A" }, "20": null };
const rowB = { id: 2, projectId: "p", type: { id: "B" }, "20": null };


/** Runs a hook once in a throwaway component and returns what it returned. */
function renderHook<T>(hook: () => T): { result: { current: T } } {
  const result = {} as { current: T };
  function Probe() {
    result.current = hook();
    return null;
  }
  const root = createRoot(document.createElement("div"));
  act(() => root.render(<Probe />));
  return { result };
}

describe("rules per (project, type)", () => {
  it("asks for the fields the rules of any type read", () => {
    const { result } = renderHook(() => useRuleFields(fields, ["10"], typeRules));
    expect(result.current).toEqual(["20"]);
  });

  it("asks for nothing without rules", () => {
    const { result } = renderHook(() => useRuleFields(fields, ["10"], undefined));
    expect(result.current).toEqual([]);
  });

  it("evaluates each row with the rules of its own type", () => {
    const { result } = renderHook(() => useRowRules(fields, ["10"], ["20"], [rowA, rowB], typeRules));
    expect(result.current.stateOf(rowA, "10")?.enabled).toBe(false);
    expect(result.current.stateOf(rowB, "10")?.enabled).not.toBe(false);
  });
});
