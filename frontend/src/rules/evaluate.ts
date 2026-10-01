// Evaluates the conditional rules of a form's columns against the entity's current values.
// Same semantics as the server's FieldRulesEvaluator (checked by the shared conformance cases in
// src/test/resources/form-rules/cases.json):
// - enabledWhen absent → enabled; a condition that can't be evaluated → disabled.
// - EQ / IN: at least one of the field's values equals one expected value (a multi-valued field
//   matches when it contains one). NEQ / NOT_IN: the negation — so true on an empty field.
// - GT / GTE / LT / LTE: compare the field's single number/date to values[0]; false when empty.
// - required = column.isRequired || requiredWhen — and never while disabled.
// - rules read values, never other fields' states (not transitive).
// - a constraint `A op B` is declared on A only, but bounds and incoherence apply to both sides.

import type {
  Bound,
  Condition,
  FieldConstraint,
  FieldState,
  IncoherenceReason,
  LeafCondition,
  OptionsContext,
  RuledColumn,
} from "./types";
import { expectedScalar, idOf, isEmptyValue, numberOf, sameScalar, scalarsOf } from "./values";

export type ValueOf = (fieldId: string) => unknown;

/**
 * Optional hook telling whether a value is still one the filtered options allow — online the
 * options source can't answer synchronously, so this is only supplied offline (relatedByConcept) or
 * by tests. Absent / undefined result = unknown, not flagged.
 */
export type IsAllowed = (fieldId: string, value: unknown, context: OptionsContext) => boolean | undefined;

export interface EvaluateOptions {
  isAllowed?: IsAllowed;
}

const key = (id: number | string) => String(id);

export function evaluateCondition(condition: Condition, valueOf: ValueOf): boolean {
  if ("all" in condition) return condition.all.every((c) => evaluateCondition(c, valueOf));
  if ("any" in condition) return condition.any.some((c) => evaluateCondition(c, valueOf));
  if ("not" in condition) return !evaluateCondition(condition.not, valueOf);
  return evaluateLeaf(condition, valueOf);
}

function evaluateLeaf(leaf: LeafCondition, valueOf: ValueOf): boolean {
  const value = valueOf(key(leaf.fieldId));
  const actual = scalarsOf(value);
  const expected = (leaf.values ?? []).map(expectedScalar);
  const matchesAny = () => actual.some((a) => expected.some((e) => e != null && sameScalar(a, e)));

  switch (leaf.op) {
    case "EMPTY":
      return actual.length === 0;
    case "NOT_EMPTY":
      return actual.length > 0;
    case "EQ":
    case "IN":
      return matchesAny();
    case "NEQ":
    case "NOT_IN":
      return !matchesAny();
    case "GT":
    case "GTE":
    case "LT":
    case "LTE": {
      const a = numberOf(value);
      const e = expected[0];
      if (a == null || typeof e !== "number") return false;
      return compare(a, leaf.op, e);
    }
    default:
      throw new Error(`Unknown op ${(leaf as LeafCondition).op}`);
  }
}

function compare(a: number, op: FieldConstraint["op"], b: number): boolean {
  switch (op) {
    case "GT":
      return a > b;
    case "GTE":
      return a >= b;
    case "LT":
      return a < b;
    case "LTE":
      return a <= b;
  }
}

const INVERSE: Record<FieldConstraint["op"], FieldConstraint["op"]> = {
  GT: "LT",
  GTE: "LTE",
  LT: "GT",
  LTE: "GTE",
};

function safe(condition: Condition | undefined | null, valueOf: ValueOf, whenAbsent: boolean): boolean {
  if (!condition) return whenAbsent;
  try {
    return evaluateCondition(condition, valueOf);
  } catch {
    return false;
  }
}

function tighter(current: Bound | undefined, next: Bound, side: "min" | "max"): Bound {
  if (!current) return next;
  if (next.value === current.value) return next.exclusive ? next : current;
  return side === "min" ? (next.value > current.value ? next : current) : next.value < current.value ? next : current;
}

/**
 * One FieldState per column with a fieldId, keyed by fieldId as a string. Columns without rules get
 * a plain state (enabled, required = isRequired) so callers can read every field the same way.
 */
export function evaluateForm(
  columns: Iterable<RuledColumn>,
  valueOf: ValueOf,
  options: EvaluateOptions = {},
): Map<string, FieldState> {
  const states = new Map<string, FieldState>();
  const cols = [...columns].filter((c) => c.fieldId != null);

  for (const col of cols) {
    const id = key(col.fieldId!);
    const rules = col.rules ?? {};
    const enabled = safe(rules.enabledWhen, valueOf, true);
    const required = enabled && (col.isRequired === true || safe(rules.requiredWhen, valueOf, false));
    const incoherent: IncoherenceReason[] = [];
    const value = valueOf(id);
    if (!enabled && !isEmptyValue(value)) incoherent.push({ kind: "DISABLED_WITH_VALUE" });

    const state: FieldState = { enabled, required, incoherent };
    if (rules.options) {
      const parentFieldId = key(rules.options.fieldId);
      const parentValue = idOf(valueOf(parentFieldId));
      state.optionsContext =
        rules.options.kind === "RELATED_CONCEPTS"
          ? { kind: "RELATED_CONCEPTS", parentFieldId, relatedTo: parentValue }
          : {
              kind: "REF_MATCH",
              parentFieldId,
              candidateFieldId: key(rules.options.candidateFieldId),
              value: parentValue,
            };
      if (!isEmptyValue(value) && options.isAllowed?.(id, value, state.optionsContext) === false) {
        incoherent.push({ kind: "OUT_OF_OPTIONS" });
      }
    }
    states.set(id, state);
  }

  // Constraints, both sides. A side missing from the columns still gets a state of its own only if
  // it is in the layout; otherwise just the declaring field is marked.
  for (const col of cols) {
    const id = key(col.fieldId!);
    for (const constraint of col.rules?.constraints ?? []) {
      const otherId = key(constraint.fieldId);
      applyConstraint(states.get(id), id, constraint.op, otherId, valueOf);
      applyConstraint(states.get(otherId), otherId, INVERSE[constraint.op], id, valueOf);
    }
  }
  return states;
}

/** `self op other`: bound self by other's value, flag self when violated. */
function applyConstraint(
  state: FieldState | undefined,
  selfId: string,
  op: FieldConstraint["op"],
  otherId: string,
  valueOf: ValueOf,
) {
  if (!state) return;
  const otherRaw = valueOf(otherId);
  const other = numberOf(otherRaw);
  if (other == null) return;
  const bound: Bound = { value: other, raw: otherRaw, exclusive: op === "GT" || op === "LT", fieldId: otherId };
  const side = op === "GT" || op === "GTE" ? "min" : "max";
  state.bounds = { ...state.bounds, [side]: tighter(state.bounds?.[side], bound, side) };

  const self = numberOf(valueOf(selfId));
  if (self != null && !compare(self, op, other)) {
    state.incoherent.push({ kind: "CONSTRAINT", op, otherFieldId: otherId });
  }
}
