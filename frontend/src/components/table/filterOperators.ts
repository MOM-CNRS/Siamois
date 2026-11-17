import type {FilterOperator, FilterValue, RangeFilterOperator} from "../../entities/shared/types";
import type {FilterKind} from "../../fields/optionSources";
import {type MessageKey, t} from "../../i18n";

/**
 * The operators a datatable filter offers, by {@link FilterKind} — the client-side counterpart of the
 * matches each {@code Kind} accepts in {@code FieldQueryService} ({@code FilterOperator} on the Java
 * side). The first of a kind's list is not necessarily its default: the order is the menu's.
 */
const OPERATORS: Record<"text" | "range" | "options", FilterOperator[]> = {
  text: ["is", "isNot", "contains", "notContains", "startsWith", "endsWith", "empty", "notEmpty"],
  range: ["eq", "between", "gte", "lte", "empty", "notEmpty"],
  options: ["in", "notIn", "empty", "notEmpty"],
};

function familyOf(kind: FilterKind): keyof typeof OPERATORS {
  if (kind === "contains") return "text";
  if (kind === "range" || kind === "date-range") return "range";
  return "options";
}

/** Every operator a filter of `kind` can take, in menu order. */
export function operatorsOf(kind: FilterKind): FilterOperator[] {
  return OPERATORS[familyOf(kind)];
}

/** The operator of a filter that names none: what the filter of `kind` has always done. */
export function defaultOperatorOf(kind: FilterKind): FilterOperator {
  const defaults = { text: "contains", range: "between", options: "in" } as const;
  return defaults[familyOf(kind)];
}

/** Whether `operator` compares the column with a value (or bounds), rather than only checking it has one. */
export function takesValue(operator: FilterOperator): boolean {
  return operator !== "empty" && operator !== "notEmpty";
}

/** The operator `value` applies, `fallback` when there is no value yet (a chip still being filled in). */
export function operatorOf(value: FilterValue | undefined, fallback: FilterOperator): FilterOperator {
  if (!value) return fallback;
  if (value.op === "empty" || value.op === "notEmpty") return value.op;
  return value.operator ?? (value.op === "contains" ? "contains" : value.op === "in" ? "in" : "between");
}

const DEFAULT_OPERATORS: FilterOperator[] = ["contains", "in", "between"];

/**
 * `value` compared with `operator` instead: what is kept of it, or undefined when nothing can be (the
 * value must then be typed again). A default operator is left out of the value, so a filter that keeps
 * it is stored and sent exactly as before operators existed.
 */
export function applyOperator(value: FilterValue | undefined, operator: FilterOperator): FilterValue | undefined {
  if (!takesValue(operator)) return { op: operator as "empty" | "notEmpty" };
  if (!value || value.op === "empty" || value.op === "notEmpty") return undefined;
  const refinement = DEFAULT_OPERATORS.includes(operator) ? undefined : operator;
  switch (value.op) {
    case "contains":
      return familyFor(operator) === "text" ? withRefinement({ op: "contains", v: value.v }, refinement) : undefined;
    case "in":
      return familyFor(operator) === "options"
        ? withRefinement({ op: "in", v: value.v, ...(value.labels ? { labels: value.labels } : {}) }, refinement)
        : undefined;
    case "range":
      return familyFor(operator) === "range" ? rangeWith(value.from, value.to, operator as RangeFilterOperator) : undefined;
  }
}

// The family of an operator that takes a value: each belongs to one only.
function familyFor(operator: FilterOperator): keyof typeof OPERATORS {
  if (OPERATORS.text.includes(operator)) return "text";
  return OPERATORS.range.includes(operator) ? "range" : "options";
}

function withRefinement<T extends FilterValue>(value: T, operator: FilterOperator | undefined): T {
  return operator ? { ...value, operator } as T : value;
}

/**
 * A range filter with the bounds `operator` uses: « equals » keeps one value on both ends, « at least » the
 * lower bound only, « at most » the upper one. Undefined when no bound is left.
 */
export function rangeWith(from: string | undefined, to: string | undefined, operator: RangeFilterOperator): FilterValue | undefined {
  const single = from ?? to;
  const bounds = {
    between: { from, to },
    eq: { from: single, to: single },
    gte: { from: single, to: undefined },
    lte: { from: undefined, to: to ?? from },
  }[operator];
  if (!bounds.from && !bounds.to) return undefined;
  return {
    op: "range",
    ...(bounds.from ? { from: bounds.from } : {}),
    ...(bounds.to ? { to: bounds.to } : {}),
    ...(operator === "between" ? {} : { operator }),
  };
}

// A date reads better with « on », « on or after »… than with the arithmetic of a number.
const DATE_LABELS: Partial<Record<FilterOperator, MessageKey>> = {
  eq: "filter.op.dateEq",
  gte: "filter.op.dateGte",
  lte: "filter.op.dateLte",
};

/** The operator's name in the menu, for a filter of `kind`. */
export function operatorLabel(operator: FilterOperator, kind: FilterKind): string {
  const dateKey = kind === "date-range" ? DATE_LABELS[operator] : undefined;
  return t(dateKey ?? (`filter.op.${operator}` as MessageKey));
}
