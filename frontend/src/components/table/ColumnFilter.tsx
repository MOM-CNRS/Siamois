import {ConceptOptionItem, useFieldWidthCap} from "../../fields/ConceptOptionItem";
import {type FocusEvent, useEffect, useRef, useState} from "react";
import {AutoComplete, type AutoCompleteCompleteEvent} from "primereact/autocomplete";
import {Calendar} from "primereact/calendar";
import {InputText} from "primereact/inputtext";
import type {InputNumberValueChangeEvent} from "primereact/inputnumber";
import {MultiSelect} from "primereact/multiselect";
import {SelectButton} from "primereact/selectbutton";
import type {FilterValue} from "../../panels/list/tableState";
import type {FilterOperator} from "../../entities/shared/types";
import {applyOperator, takesValue} from "./filterOperators";
import type {FilterKind, FilterOption} from "../../fields/optionSources";
import {t} from "../../i18n";
import {calendarDateFormat} from "../../i18n/dates";
import {ReferencePickerSheet} from "../../fields/ReferencePickerSheet";
import {SignedInputNumber} from "../../fields/SignedInputNumber";
import {useIsMobile} from "../../viewport";

/**
 * One filter widget, dispatching on {@link FilterKind} — the client-side counterpart of
 * {@code ProjectListFilter}'s five {@code Kind}s. Mirrors {@code pages/shared/table/filterTemplate.xhtml}:
 * a multi-select autocomplete for concept/spatial-unit filters, a from/to pair for a numeric range,
 * plain text for a contains filter.
 *
 * <p>Commits on blur/selection rather than per keystroke — {@code setFilters} resets the list's
 * offset, so committing per keystroke would refetch on every character.</p>
 *
 * <p>Selected options (labels, not just the ids {@link FilterValue} carries) are owned by the
 * caller: the filter chip bar needs them to label its chips, and re-opening a chip's editor has to
 * show the same labels it printed. Keeping them here would lose them on every close.</p>
 */
export interface ColumnFilterProps {
  label: string;
  kind: FilterKind;
  value: FilterValue | undefined;
  onChange: (value: FilterValue | undefined, options?: FilterOption[]) => void;
  // Only needed for concept-one/concept-many/spatial-one — the async option source.
  loadOptions?: (q?: string) => Promise<FilterOption[]>;
  // The labels matching `value`'s ids, for the same three kinds. Ignored by the others.
  selectedOptions?: FilterOption[];
  // A fixed list of values for an "in" filter: picked in a multi-select dropdown, not searched.
  options?: FilterOption[];
  autoFocus?: boolean;
  // A datatable filter's operator (filterOperators.ts): every value committed is compared with it, and a
  // number or date filter shows the bounds it uses. Absent: the kind's own comparison, as before operators.
  operator?: FilterOperator;
  // What an empty input shows; the label by default.
  placeholder?: string;
}

/** Which inputs a range filter shows: both bounds, or one value (« equals »), or only one of the bounds. */
type RangeBounds = "both" | "single" | "from" | "to";

const RANGE_BOUNDS: Partial<Record<FilterOperator, RangeBounds>> = { eq: "single", gte: "from", lte: "to" };

function rangeBoundsOf(operator: FilterOperator | undefined): RangeBounds {
  return (operator && RANGE_BOUNDS[operator]) ?? "both";
}

export function ColumnFilter({ operator, placeholder, onChange: commitValue, ...props }: Readonly<ColumnFilterProps>) {
  if (operator && !takesValue(operator)) return null;
  const onChange: ColumnFilterProps["onChange"] = operator
    ? (value, options) => commitValue(value ? applyOperator(value, operator) : undefined, options)
    : commitValue;
  return <KindFilter {...props} onChange={onChange} placeholder={placeholder} bounds={rangeBoundsOf(operator)} />;
}

function KindFilter({
  label,
  kind,
  value,
  onChange,
  loadOptions,
  selectedOptions,
  options,
  autoFocus,
  placeholder,
  bounds,
}: Omit<ColumnFilterProps, "operator"> & { bounds: RangeBounds }) {
  if (kind === "in" && options) {
    return <ChoiceFilter label={label} value={value} onChange={onChange} options={options} />;
  }
  switch (kind) {
    case "contains":
      return <ContainsFilter label={placeholder ?? label} value={value} onChange={onChange} autoFocus={autoFocus} />;
    case "range":
      return <RangeFilter label={label} value={value} onChange={onChange} autoFocus={autoFocus} bounds={bounds} placeholder={placeholder} />;
    case "date-range":
      return <DateRangeFilter label={label} value={value} onChange={onChange} autoFocus={autoFocus} bounds={bounds} placeholder={placeholder} />;
    case "boolean":
      return <BooleanFilter value={value} onChange={onChange} />;
    // Only having a value or not: the operator says it all.
    case "presence":
      return null;
    case "in":
    case "concept-one":
    case "concept-many":
    case "spatial-one":
      return (
        <OptionsFilter
          label={label}
          value={value}
          onChange={onChange}
          loadOptions={loadOptions}
          selectedOptions={selectedOptions}
          autoFocus={autoFocus}
        />
      );
  }
}

function ContainsFilter({
  label,
  value,
  onChange,
  autoFocus,
}: Readonly<Pick<ColumnFilterProps, "label" | "value" | "onChange" | "autoFocus">>) {
  const committed = value?.op === "contains" ? value.v : "";
  const [draft, setDraft] = useState(committed);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (autoFocus) inputRef.current?.focus();
  }, [autoFocus]);

  function commit() {
    onChange(draft ? { op: "contains", v: draft } : undefined);
  }

  return (
    <InputText
      ref={inputRef}
      placeholder={label}
      value={draft}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === "Enter") commit();
      }}
    />
  );
}

type RangeFilterProps = Pick<ColumnFilterProps, "label" | "value" | "onChange" | "autoFocus" | "placeholder"> & {
  bounds: RangeBounds;
};

/**
 * A range filter's bounds and how to commit new ones: both at once, or the one bound an operator taking a
 * single value edits — the upper one for « at most », the lower one otherwise (applyOperator copies it to
 * both ends for « equals »).
 */
function rangeBounds(value: FilterValue | undefined, onChange: ColumnFilterProps["onChange"], bounds: RangeBounds) {
  const from = value?.op === "range" ? value.from : undefined;
  const to = value?.op === "range" ? value.to : undefined;
  const commit = (nextFrom: string | undefined, nextTo: string | undefined) =>
    onChange(nextFrom || nextTo ? { op: "range", from: nextFrom, to: nextTo } : undefined);
  const upper = bounds === "to";
  return {
    from,
    to,
    commit,
    single: upper ? to : from,
    commitSingle: (next: string | undefined) => (upper ? commit(undefined, next) : commit(next, undefined)),
  };
}

/** The value and handlers of a number input editing one bound. */
function numberBoundProps(bound: string | undefined, commitBound: (next: string | undefined) => void) {
  return {
    value: bound != null ? Number(bound) : null,
    onBlur: (e: FocusEvent<HTMLInputElement>) => commitBound(e.target.value || undefined),
    onValueChange: (e: InputNumberValueChangeEvent) => commitBound(e.value != null ? String(e.value) : undefined),
  };
}

function RangeFilter({ label, value, onChange, autoFocus, bounds, placeholder }: RangeFilterProps) {
  const { from, to, commit, single, commitSingle } = rangeBounds(value, onChange, bounds);

  if (bounds !== "both") {
    return <SignedInputNumber placeholder={placeholder ?? label} autoFocus={autoFocus} {...numberBoundProps(single, commitSingle)} />;
  }

  return (
    <span className="entity-list-panel-range-filter">
      <SignedInputNumber
        placeholder={t("filter.min", { label })}
        autoFocus={autoFocus}
        {...numberBoundProps(from, (next) => commit(next, to))}
      />
      <SignedInputNumber placeholder={t("filter.max", { label })} {...numberBoundProps(to, (next) => commit(from, next))} />
    </span>
  );
}

// A date range as the server takes it: bare ISO dates, both ends inclusive (FieldQueryService reads
// "to 2024-05-02" as the whole of that day).
function toIsoDate(date: Date | null | undefined): string | undefined {
  if (!date) return undefined;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function fromIsoDate(value: string | undefined): Date | null {
  if (!value) return null;
  const [y, m, d] = value.split("-").map(Number);
  return y && m && d ? new Date(y, m - 1, d) : null;
}

/** The value and handlers of a calendar editing one bound. */
function dateBoundProps(bound: string | undefined, commitBound: (next: string | undefined) => void) {
  return {
    dateFormat: calendarDateFormat(),
    value: fromIsoDate(bound),
    showButtonBar: true,
    onChange: (e: { value?: Date | null }) => commitBound(toIsoDate(e.value)),
  };
}

function DateRangeFilter({ label, value, onChange, autoFocus, bounds, placeholder }: RangeFilterProps) {
  const { from, to, commit, single, commitSingle } = rangeBounds(value, onChange, bounds);

  if (bounds !== "both") {
    return <Calendar placeholder={placeholder ?? label} autoFocus={autoFocus} {...dateBoundProps(single, commitSingle)} />;
  }

  return (
    <span className="entity-list-panel-range-filter">
      <Calendar
        placeholder={t("filter.from", { label })}
        autoFocus={autoFocus}
        {...dateBoundProps(from, (next) => commit(next, to))}
      />
      <Calendar placeholder={t("filter.to", { label })} {...dateBoundProps(to, (next) => commit(from, next))} />
    </span>
  );
}

/**
 * Several values picked among a fixed few, ticked in a dropdown: f.<key>=<id>, repeatable.
 */
function ChoiceFilter({
  label,
  value,
  onChange,
  options,
}: Pick<ColumnFilterProps, "label" | "value" | "onChange"> & { options: FilterOption[] }) {
  const picked = value?.op === "in" ? value.v : [];
  return (
    <MultiSelect
      className="entity-list-panel-choice-filter"
      value={picked}
      options={options}
      optionLabel="label"
      optionValue="id"
      placeholder={label}
      display="chip"
      showSelectAll={false}
      onChange={(e) => {
        const ids = (e.value as string[] | null) ?? [];
        onChange(
          ids.length > 0 ? { op: "in", v: ids } : undefined,
          options.filter((o) => ids.includes(o.id)),
        );
      }}
    />
  );
}

// A yes/no column filters on one answer at a time: the server takes it as f.<id>=true|false, which
// is the "in" shape with a single id. Picking the chosen one again clears the filter.
function BooleanFilter({ value, onChange }: Readonly<Pick<ColumnFilterProps, "value" | "onChange">>) {
  const picked = value?.op === "in" ? value.v[0] : null;
  const options = [
    { id: "true", label: t("field.yes") },
    { id: "false", label: t("field.no") },
  ];
  return (
    <SelectButton
      value={picked ?? null}
      options={options}
      optionLabel="label"
      optionValue="id"
      onChange={(e) => {
        const next = e.value as string | null;
        const option = options.find((o) => o.id === next);
        onChange(option ? { op: "in", v: [option.id] } : undefined, option ? [option] : []);
      }}
    />
  );
}

function OptionsFilter({
  label,
  onChange,
  loadOptions,
  selectedOptions,
  autoFocus,
}: Readonly<Pick<ColumnFilterProps, "label" | "value" | "onChange" | "loadOptions" | "selectedOptions" | "autoFocus">>) {
  const [suggestions, setSuggestions] = useState<FilterOption[]>([]);
  const selected = selectedOptions ?? [];
  const { capTo, panelStyle } = useFieldWidthCap();
  const mobile = useIsMobile();

  async function search(e: AutoCompleteCompleteEvent) {
    if (!loadOptions) return;
    capTo(e);
    setSuggestions(await loadOptions(e.query));
  }

  function commit(next: FilterOption[]) {
    onChange(next.length > 0 ? { op: "in", v: next.map((o) => o.id) } : undefined, next);
  }

  const itemTemplateFct = (o) => <ConceptOptionItem option={o} />;

  // Phone width: the options are picked in a drawer of their own (an autocomplete dropdown would sit under the keyboard).
  if (mobile) {
    return (
      <ReferencePickerSheet
        multiple
        selected={selected}
        placeholder={label}
        loadOptions={loadOptions ?? null}
        itemTemplate={itemTemplateFct}
        onCommit={commit}
      />
    );
  }

  const itemTemplateFct = (o: FilterOption) => <ConceptOptionItem option={o} />

  return (
    <AutoComplete
      placeholder={label}
      autoFocus={autoFocus}
      value={selected}
      suggestions={suggestions}
      completeMethod={search}
      field="label"
      panelStyle={panelStyle}
      itemTemplate={itemTemplateFct}
      multiple
      dropdown={false}
      onChange={(e) => commit(e.value as FilterOption[])}
    />
  );
}

/**
 * Short, human-readable rendering of an active filter, for its chip label. Falls back to the raw
 * ids when no labels are known (a restored `?s=`/saved view carries ids only) rather than printing
 * nothing — a chip with no value at all reads as "filter not set", which would be wrong.
 */
/** An "in" filter with the labels of its ids, so they are still known when it comes back from a saved view. */
export function withLabels(value: FilterValue, options?: FilterOption[]): FilterValue {
  if (value.op !== "in" || !options) return value;
  const byId = new Map(options.map((o) => [o.id, o.label]));
  const labels: Record<string, string> = { ...value.labels };
  for (const id of value.v) {
    const label = byId.get(id);
    if (label != null) labels[id] = label;
  }
  return Object.keys(labels).length > 0 ? { ...value, labels } : value;
}

export function describeFilterValue(value: FilterValue | undefined, options?: FilterOption[]): string {
  if (!value) return "";
  switch (value.op) {
    case "contains":
      return value.v;
    case "in": {
      const byId = new Map((options ?? []).map((o) => [o.id, o.label]));
      return value.v.map((id) => byId.get(id) ?? value.labels?.[id] ?? id).join(", ");
    }
    case "range": {
      if (value.from && value.to) return `${value.from} – ${value.to}`;
      if (value.from) return `≥ ${value.from}`;
      if (value.to) return `≤ ${value.to}`;
      return "";
    }
    // No value to show: the operator says it all.
    case "empty":
    case "notEmpty":
      return "";
  }
}
