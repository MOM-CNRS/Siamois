import {
  type FilterOperator,
  type FilterValue,
  isOptionsOperator,
  isPresenceFilter,
  isPresenceOperator,
  isRangeOperator,
  isTextOperator,
  OPTIONS_FILTER_OPERATORS,
  PRESENCE_FILTER_OPERATORS,
  RANGE_FILTER_OPERATORS,
  type RangeFilterOperator,
  TEXT_FILTER_OPERATORS,
  type ValueFilter,
} from "../../entities/shared/filterValue";
import type {FilterKind} from "../../fields/optionSources";
import {type MessageKey, t} from "../../i18n";

/**
 * What a datatable filter offers: its operators in menu order, the one a filter without operator applies
 * (what the column has always done, left out of the stored value), and the names some operators take on
 * it (« Contient » for « any of » on a relation). The client-side counterpart of the matches each
 * {@code Kind} accepts in {@code FieldQueryService} ({@code FilterOperator} on the Java side), narrowed
 * where a choice makes no sense (an entity always has a type).
 */
export interface OperatorProfile {
  operators: FilterOperator[];
  defaultOperator: FilterOperator;
  labels?: Partial<Record<FilterOperator, MessageKey>>;
}

const TEXT: FilterOperator[] = [...TEXT_FILTER_OPERATORS];
const RANGE: FilterOperator[] = [...RANGE_FILTER_OPERATORS];
const OPTIONS: FilterOperator[] = [...OPTIONS_FILTER_OPERATORS];
const PRESENCE: FilterOperator[] = [...PRESENCE_FILTER_OPERATORS];

export const OPERATOR_PROFILES = {
  text: { operators: [...TEXT, ...PRESENCE], defaultOperator: "contains" },
  number: { operators: [...RANGE, ...PRESENCE], defaultOperator: "between" },
  // A date reads better with « on », « on or after »… than with the arithmetic of a number.
  date: {
    operators: [...RANGE, ...PRESENCE],
    defaultOperator: "between",
    labels: { eq: "filter.op.dateEq", gte: "filter.op.dateGte", lte: "filter.op.dateLte" },
  },
  options: { operators: [...OPTIONS, ...PRESENCE], defaultOperator: "in" },
  // Every fiche has a type: « empty » would match nothing, « not empty » everything.
  type: { operators: OPTIONS, defaultOperator: "in" },
  // The entities linked to the row (parents, children, finds, stratigraphy, places…): the row's links contain them or not.
  relation: {
    operators: [...OPTIONS, ...PRESENCE],
    defaultOperator: "in",
    labels: { in: "filter.op.contains", notIn: "filter.op.notContains" },
  },
  // A shape is only there or not.
  presence: { operators: PRESENCE, defaultOperator: "notEmpty" },
} satisfies Record<string, OperatorProfile>;

/** The profile of a filter of `kind`, before what the field itself narrows (a type, a relation). */
export function profileOf(kind: FilterKind): OperatorProfile {
  switch (kind) {
    case "contains":
      return OPERATOR_PROFILES.text;
    case "range":
      return OPERATOR_PROFILES.number;
    case "date-range":
      return OPERATOR_PROFILES.date;
    case "presence":
      return OPERATOR_PROFILES.presence;
    default:
      return OPERATOR_PROFILES.options;
  }
}

/** A profile offering nothing but `profile`'s default: a filter whose server side compares one way only. */
export function defaultOnly(profile: OperatorProfile): OperatorProfile {
  return { ...profile, operators: [profile.defaultOperator] };
}

/** Whether `operator` compares the column with a value (or bounds), rather than only checking it has one. */
export function takesValue(operator: FilterOperator): boolean {
  return !isPresenceOperator(operator);
}

// The comparison each shape of value had before operators: never stored, never sent.
const SHAPE_DEFAULT_OPERATORS = {
  contains: "contains",
  in: "in",
  range: "between",
} as const satisfies Record<ValueFilter["op"], FilterOperator>;

/** The operator `value` applies, `fallback` when there is no value yet (a chip still being filled in). */
export function operatorOf(value: FilterValue | undefined, fallback: FilterOperator): FilterOperator {
  if (!value) return fallback;
  if (isPresenceFilter(value)) return value.op;
  return value.operator ?? SHAPE_DEFAULT_OPERATORS[value.op];
}

/**
 * `value` compared with `operator` instead: what is kept of it, or undefined when nothing can be (the
 * value must then be typed again). A default operator is left out of the value, so a filter that keeps
 * it is stored and sent exactly as before operators existed.
 */
export function applyOperator(value: FilterValue | undefined, operator: FilterOperator): FilterValue | undefined {
  if (isPresenceOperator(operator)) return { op: operator };
  if (!value || isPresenceFilter(value)) return undefined;
  switch (value.op) {
    case "contains":
      return isTextOperator(operator) ? withRefinement({ op: "contains", v: value.v }, operator) : undefined;
    case "in":
      return isOptionsOperator(operator)
        ? withRefinement({ op: "in", v: value.v, ...(value.labels ? { labels: value.labels } : {}) }, operator)
        : undefined;
    case "range":
      return isRangeOperator(operator) ? rangeWith(value.from, value.to, operator) : undefined;
  }
}

/** `value` with `operator` in it, unless that is its shape's default (left out of the value). */
function withRefinement<T extends ValueFilter>(value: T, operator: FilterOperator): T {
  return operator === SHAPE_DEFAULT_OPERATORS[value.op] ? value : { ...value, operator };
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

/** The operator's name in the menu of a filter with `profile`. */
export function operatorLabel(operator: FilterOperator, profile: OperatorProfile): string {
  return t(profile.labels?.[operator] ?? (`filter.op.${operator}` as MessageKey));
}
