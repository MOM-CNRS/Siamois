import { MultiSelect } from "primereact/multiselect";

// Mirrors pages/shared/table/tableToolbar.xhtml's gear-overlay column chooser
// (p:columnToggler → EntityTableViewModel.onToggle): a multi-select of every catalog column, in
// its default order, letting the user pick which are currently shown. Generic over field id/label
// pairs rather than the raw FieldCatalog shape, so it stays reusable by a future entity type.
export interface ColumnTogglerOption {
  fieldId: string;
  label: string;
}

export interface ColumnTogglerProps {
  options: ColumnTogglerOption[];
  value: string[];
  onChange: (fieldIds: string[]) => void;
}

export function ColumnToggler({ options, value, onChange }: ColumnTogglerProps) {
  return (
    <MultiSelect
      className="entity-list-panel-column-toggler"
      options={options}
      optionLabel="label"
      optionValue="fieldId"
      value={value}
      onChange={(e) => onChange(e.value as string[])}
      display="chip"
      filter
      placeholder="Colonnes"
    />
  );
}
