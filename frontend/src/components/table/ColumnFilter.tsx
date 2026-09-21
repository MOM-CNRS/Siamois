import { useState } from "react";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
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
 */
export interface ColumnFilterProps {
  label: string;
  kind: FilterKind;
  value: FilterValue | undefined;
  onChange: (value: FilterValue | undefined) => void;
  // Only needed for concept-one/concept-many/spatial-one — the async option source.
  loadOptions?: (q?: string) => Promise<FilterOption[]>;
}

export function ColumnFilter({ label, kind, value, onChange, loadOptions }: ColumnFilterProps) {
  switch (kind) {
    case "contains":
      return <ContainsFilter label={label} value={value} onChange={onChange} />;
    case "range":
      return <RangeFilter label={label} value={value} onChange={onChange} />;
    case "concept-one":
    case "concept-many":
    case "spatial-one":
      return <OptionsFilter label={label} value={value} onChange={onChange} loadOptions={loadOptions} />;
  }
}

function ContainsFilter({
  label,
  value,
  onChange,
}: Pick<ColumnFilterProps, "label" | "value" | "onChange">) {
  const committed = value?.op === "contains" ? value.v : "";
  const [draft, setDraft] = useState(committed);

  function commit() {
    onChange(draft ? { op: "contains", v: draft } : undefined);
  }

  return (
    <InputText
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

function RangeFilter({ label, value, onChange }: Pick<ColumnFilterProps, "label" | "value" | "onChange">) {
  const from = value?.op === "range" ? value.from : undefined;
  const to = value?.op === "range" ? value.to : undefined;

  function commit(nextFrom: string | undefined, nextTo: string | undefined) {
    onChange(nextFrom || nextTo ? { op: "range", from: nextFrom, to: nextTo } : undefined);
  }

  return (
    <span className="entity-list-panel-range-filter">
      <InputNumber
        placeholder={`${label} (min)`}
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

function OptionsFilter({
  label,
  onChange,
  loadOptions,
}: Pick<ColumnFilterProps, "label" | "value" | "onChange" | "loadOptions">) {
  // FilterValue only carries ids (the shared, backend-facing shape) — this local state keeps the
  // matching labels so already-selected chips render with a name, not a bare id, without pulling
  // labels into the serializable TableState.
  const [selected, setSelected] = useState<FilterOption[]>([]);
  const [suggestions, setSuggestions] = useState<FilterOption[]>([]);

  async function search(e: AutoCompleteCompleteEvent) {
    if (!loadOptions) return;
    setSuggestions(await loadOptions(e.query));
  }

  function commit(next: FilterOption[]) {
    setSelected(next);
    onChange(next.length > 0 ? { op: "in", v: next.map((o) => o.id) } : undefined);
  }

  return (
    <AutoComplete
      placeholder={label}
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
