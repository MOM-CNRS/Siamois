// The operators a list filter can name, by shape of value, in menu order. The single source of the
// operator types below, of the saved-state validation (listState.ts) and of the filter menus
// (components/table/filterOperators.ts). FilterOperator on the Java side.

export const TEXT_FILTER_OPERATORS = ["is", "isNot", "contains", "notContains", "startsWith", "endsWith"] as const;
export const OPTIONS_FILTER_OPERATORS = ["in", "notIn"] as const;
// All sent as bounds: « equals » is from = to, « at least » a lone from, « at most » a lone to.
export const RANGE_FILTER_OPERATORS = ["eq", "between", "gte", "lte"] as const;
// No value at all: the column has one or not.
export const PRESENCE_FILTER_OPERATORS = ["empty", "notEmpty"] as const;

export type TextFilterOperator = (typeof TEXT_FILTER_OPERATORS)[number];
export type OptionsFilterOperator = (typeof OPTIONS_FILTER_OPERATORS)[number];
export type RangeFilterOperator = (typeof RANGE_FILTER_OPERATORS)[number];
export type PresenceFilterOperator = (typeof PRESENCE_FILTER_OPERATORS)[number];
export type FilterOperator = TextFilterOperator | OptionsFilterOperator | RangeFilterOperator | PresenceFilterOperator;

/**
 * One column's filter — mirrors ProjectListFilter's Kind whitelist. Lives in entities/shared (not in
 * panels/list/tableState.ts, which owns TableState) so both ListParams and TableState can reference it
 * without panels/ becoming something entities/ depends on.
 *
 * <p>`op` is the shape of the value (kept as is in saved views); `operator` refines how a datatable
 * filter compares it — absent, the shape's historical comparison (contains, any of, between). `empty`
 * and `notEmpty` carry no value at all.</p>
 */
export type FilterValue =
  | { op: "contains"; v: string; operator?: TextFilterOperator }
  // `labels`: what the ids were called when picked, kept with the filter so a saved view can show them.
  | { op: "in"; v: string[]; labels?: Record<string, string>; operator?: OptionsFilterOperator }
  | { op: "range"; from?: string; to?: string; operator?: RangeFilterOperator }
  | { op: "empty" }
  | { op: "notEmpty" };

/** A filter that only checks that the column has a value (or has none). */
export type PresenceFilter = Extract<FilterValue, { op: PresenceFilterOperator }>;
/** A filter that compares a value (or bounds) with the column. */
export type ValueFilter = Exclude<FilterValue, PresenceFilter>;

function isOneOf<T extends string>(operators: readonly T[], operator: unknown): operator is T {
  return typeof operator === "string" && (operators as readonly string[]).includes(operator);
}

export function isTextOperator(operator: unknown): operator is TextFilterOperator {
  return isOneOf(TEXT_FILTER_OPERATORS, operator);
}

export function isOptionsOperator(operator: unknown): operator is OptionsFilterOperator {
  return isOneOf(OPTIONS_FILTER_OPERATORS, operator);
}

export function isRangeOperator(operator: unknown): operator is RangeFilterOperator {
  return isOneOf(RANGE_FILTER_OPERATORS, operator);
}

export function isPresenceOperator(operator: unknown): operator is PresenceFilterOperator {
  return isOneOf(PRESENCE_FILTER_OPERATORS, operator);
}

/** Whether `value` only checks that the column has a value (or has none). */
export function isPresenceFilter(value: FilterValue): value is PresenceFilter {
  return isPresenceOperator(value.op);
}
