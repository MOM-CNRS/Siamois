// Runs the conformance cases shared with the Java evaluator (FieldRulesEvaluatorConformanceTest):
// both sides must produce the same states for the same rules and values.
import { describe, expect, it } from "vitest";
import conformance from "../../../src/test/resources/form-rules/cases.json";
import { evaluateForm } from "./evaluate";
import type { FieldState, RuledColumn } from "./types";

interface ExpectedState {
  enabled?: boolean;
  required?: boolean;
  incoherent?: string[];
  optionsContext?: unknown;
  bounds?: { min?: { value: number; exclusive: boolean }; max?: { value: number; exclusive: boolean } };
}

interface Case {
  name: string;
  columns: RuledColumn[];
  values: Record<string, unknown>;
  expected: Record<string, ExpectedState>;
}

function subset(state: FieldState | undefined, expected: ExpectedState): ExpectedState {
  const out: ExpectedState = {};
  if (!state) return out;
  if ("enabled" in expected) out.enabled = state.enabled;
  if ("required" in expected) out.required = state.required;
  if ("incoherent" in expected) out.incoherent = state.incoherent.map((r) => r.kind);
  if ("optionsContext" in expected) out.optionsContext = state.optionsContext;
  if (expected.bounds) {
    out.bounds = {};
    for (const side of ["min", "max"] as const) {
      const b = state.bounds?.[side];
      if (expected.bounds[side]) out.bounds[side] = b ? { value: b.value, exclusive: b.exclusive } : undefined;
    }
  }
  return out;
}

describe("rules conformance", () => {
  for (const c of (conformance as unknown as { cases: Case[] }).cases) {
    it(c.name, () => {
      const states = evaluateForm(c.columns, (id) => c.values[id]);
      for (const [fieldId, expected] of Object.entries(c.expected)) {
        expect(subset(states.get(fieldId), expected), `field ${fieldId}`).toEqual(expected);
      }
    });
  }
});
