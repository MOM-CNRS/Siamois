import { VisibilityChooser } from "./VisibilityChooser";

// Mirrors pages/shared/table/tableToolbar.xhtml's gear-overlay column chooser
// (p:columnToggler → EntityTableViewModel.onToggle): every catalog column, split into the shown
// ones (in display order, draggable) and the hidden ones, with a search over both. Generic over
// field id/label pairs rather than the raw FieldCatalog shape, so it stays reusable by a future
// entity type.
export interface ColumnTogglerOption {
  fieldId: string;
  label: string;
}

export interface ColumnTogglerProps {
  // Every catalog column, in catalog order (the hidden section's order).
  options: ColumnTogglerOption[];
  // The shown columns, in display order.
  value: string[];
  onChange: (fieldIds: string[]) => void;
}

export function ColumnToggler({ options, value, onChange }: ColumnTogglerProps) {
  return (
    <VisibilityChooser
      className="entity-list-panel-column-toggler"
      items={options.map((o) => ({ id: o.fieldId, label: o.label }))}
      visible={value}
      onChange={onChange}
      visibleTitle="Colonnes visibles"
      hiddenTitle="Colonnes masquées"
      searchable
      searchPlaceholder="Rechercher une colonne"
    />
  );
}
