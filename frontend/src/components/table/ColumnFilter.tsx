import { useEffect, useRef, useState } from "react";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { Calendar } from "primereact/calendar";
import { InputNumber } from "primereact/inputnumber";
import { InputText } from "primereact/inputtext";
import type { FilterValue } from "../../panels/tableState";
import type { FilterKind, FilterOption } from "../../fields/optionSources";

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
  autoFocus?: boolean;
}

export function ColumnFilter({ label, kind, value, onChange, loadOptions, selectedOptions, autoFocus }: ColumnFilterProps) {
  switch (kind) {
    case "contains":
      return <ContainsFilter label={label} value={value} onChange={onChange} autoFocus={autoFocus} />;
    case "range":
      return <RangeFilter label={label} value={value} onChange={onChange} autoFocus={autoFocus} />;
    case "date-range":
      return <DateRangeFilter label={label} value={value} onChange={onChange} autoFocus={autoFocus} />;
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
}: Pick<ColumnFilterProps, "label" | "value" | "onChange" | "autoFocus">) {
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

function RangeFilter({ label, value, onChange, autoFocus }: Pick<ColumnFilterProps, "label" | "value" | "onChange" | "autoFocus">) {
  const from = value?.op === "range" ? value.from : undefined;
  const to = value?.op === "range" ? value.to : undefined;

  function commit(nextFrom: string | undefined, nextTo: string | undefined) {
    onChange(nextFrom || nextTo ? { op: "range", from: nextFrom, to: nextTo } : undefined);
  }

  return (
    <span className="entity-list-panel-range-filter">
      <InputNumber
        placeholder={`${label} (min)`}
        autoFocus={autoFocus}
        value={from != null ? Number(from) : null}
        onBlur={(e) => commit(e.target.value ? e.target.value : undefined, to)}
        onValueChange={(e) => commit(e.value != null ? String(e.value) : undefined, to)}
      />
      <InputNumber
        placeholder={`${label} (max)`}
        value={to != null ? Number(to) : null}
        onBlur={(e) => commit(from, e.target.value ? e.target.value : undefined)}
        onValueChange={(e) => commit(from, e.value != null ? String(e.value) : undefined)}
      />
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

function DateRangeFilter({ label, value, onChange, autoFocus }: Pick<ColumnFilterProps, "label" | "value" | "onChange" | "autoFocus">) {
  const from = value?.op === "range" ? value.from : undefined;
  const to = value?.op === "range" ? value.to : undefined;

  function commit(nextFrom: string | undefined, nextTo: string | undefined) {
    onChange(nextFrom || nextTo ? { op: "range", from: nextFrom, to: nextTo } : undefined);
  }

  return (
    <span className="entity-list-panel-range-filter">
      <Calendar
        placeholder={`${label} (du)`}
        autoFocus={autoFocus}
        dateFormat="dd/mm/yy"
        value={fromIsoDate(from)}
        showButtonBar
        onChange={(e) => commit(toIsoDate(e.value as Date | null), to)}
      />
      <Calendar
        placeholder={`${label} (au)`}
        dateFormat="dd/mm/yy"
        value={fromIsoDate(to)}
        showButtonBar
        onChange={(e) => commit(from, toIsoDate(e.value as Date | null))}
      />
    </span>
  );
}

function OptionsFilter({
  label,
  onChange,
  loadOptions,
  selectedOptions,
  autoFocus,
}: Pick<ColumnFilterProps, "label" | "value" | "onChange" | "loadOptions" | "selectedOptions" | "autoFocus">) {
  const [suggestions, setSuggestions] = useState<FilterOption[]>([]);
  const selected = selectedOptions ?? [];

  async function search(e: AutoCompleteCompleteEvent) {
    if (!loadOptions) return;
    setSuggestions(await loadOptions(e.query));
  }

  function commit(next: FilterOption[]) {
    onChange(next.length > 0 ? { op: "in", v: next.map((o) => o.id) } : undefined, next);
  }

  return (
    <AutoComplete
      placeholder={label}
      autoFocus={autoFocus}
      value={selected}
      suggestions={suggestions}
      completeMethod={search}
      field="label"
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
export function describeFilterValue(value: FilterValue | undefined, options?: FilterOption[]): string {
  if (!value) return "";
  switch (value.op) {
    case "contains":
      return value.v;
    case "in": {
      const byId = new Map((options ?? []).map((o) => [o.id, o.label]));
      return value.v.map((id) => byId.get(id) ?? id).join(", ");
    }
    case "range": {
      if (value.from && value.to) return `${value.from} – ${value.to}`;
      if (value.from) return `≥ ${value.from}`;
      if (value.to) return `≤ ${value.to}`;
      return "";
    }
  }
}
