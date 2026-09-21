import { useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { OverlayPanel } from "primereact/overlaypanel";
import { ColumnFilter, describeFilterValue } from "./ColumnFilter";
import type { FilterKind, FilterOption } from "../../fields/optionSources";
import type { FilterValue } from "../../panels/tableState";

/**
 * Notion-style filter bar living in the table's own header: no enable/disable switch and no
 * always-present filter row — just chips. One chip per active filter (click to edit, × to drop it)
 * plus a single "Ajouter un filtre" chip that offers the columns not yet filtered on.
 *
 * <p>This is a deliberate divergence from JSF, whose filters are a per-column input row toggled
 * from the gear overlay ({@code tableToolbar.xhtml}'s "Filtres activés/désactivés"): the chips make
 * the currently-applied filters visible at a glance, which the JSF row does not, and they cost no
 * header space when nothing is filtered.</p>
 *
 * <p>Holds no filter state of its own beyond "which chips are open/being added" — {@code filters}
 * and {@code optionsByKey} are the panel's, so a restored view or a programmatic reset shows up
 * here without any syncing.</p>
 */
export interface FilterSpec {
  // Same key the f.<key> query param uses (ProjectListFilter's whitelist).
  key: string;
  label: string;
  kind: FilterKind;
  loadOptions?: (q?: string) => Promise<FilterOption[]>;
}

export interface FilterChipBarProps {
  specs: FilterSpec[];
  filters: Record<string, FilterValue>;
  // `options` carries the labels for an "in" filter, which FilterValue itself (ids only) cannot.
  onChange: (key: string, value: FilterValue | undefined, options?: FilterOption[]) => void;
  optionsByKey: Record<string, FilterOption[]>;
}

export function FilterChipBar({ specs, filters, onChange, optionsByKey }: FilterChipBarProps) {
  // A chip the user added but hasn't given a value to yet. It has no entry in `filters` (an empty
  // filter must not reach the query), so it needs tracking here to stay on screen while being
  // filled in.
  const [pendingKeys, setPendingKeys] = useState<string[]>([]);
  const [editingKey, setEditingKey] = useState<string | null>(null);

  const addPanel = useRef<OverlayPanel>(null);
  const editPanel = useRef<OverlayPanel>(null);

  const activeKeys = specs
    .map((s) => s.key)
    .filter((key) => filters[key] != null || pendingKeys.includes(key));
  const availableSpecs = specs.filter((s) => !activeKeys.includes(s.key));

  function openEditor(e: ReactMouseEvent, key: string, anchor: HTMLElement) {
    setEditingKey(key);
    editPanel.current?.show(e, anchor);
  }

  function addFilter(e: ReactMouseEvent, spec: FilterSpec, anchor: HTMLElement) {
    addPanel.current?.hide();
    setPendingKeys((keys) => (keys.includes(spec.key) ? keys : [...keys, spec.key]));
    openEditor(e, spec.key, anchor);
  }

  function removeFilter(key: string) {
    setPendingKeys((keys) => keys.filter((k) => k !== key));
    if (editingKey === key) {
      setEditingKey(null);
      editPanel.current?.hide();
    }
    if (filters[key] != null) onChange(key, undefined);
  }

  const editingSpec = specs.find((s) => s.key === editingKey);
  const addChipRef = useRef<HTMLButtonElement>(null);

  return (
    <div className="entity-list-panel-filter-chips">
      {activeKeys.map((key) => {
        const spec = specs.find((s) => s.key === key)!;
        const summary = describeFilterValue(filters[key], optionsByKey[key]);
        return (
          <span key={key} className={`filter-chip${summary ? "" : " filter-chip-empty"}`}>
            <button
              type="button"
              className="filter-chip-body"
              onClick={(e) => openEditor(e, key, e.currentTarget.parentElement as HTMLElement)}
            >
              <span className="filter-chip-label">{spec.label}</span>
              {summary && <span className="filter-chip-value">{summary}</span>}
            </button>
            <button
              type="button"
              className="filter-chip-remove"
              aria-label={`Retirer le filtre ${spec.label}`}
              onClick={() => removeFilter(key)}
            >
              <i className="bi bi-x" />
            </button>
          </span>
        );
      })}

      {availableSpecs.length > 0 && (
        <button
          type="button"
          ref={addChipRef}
          className="filter-chip filter-chip-add"
          onClick={(e) => addPanel.current?.toggle(e)}
        >
          <i className="bi bi-plus" />
          {activeKeys.length === 0 ? "Ajouter un filtre" : "Filtre"}
        </button>
      )}

      <OverlayPanel ref={addPanel} className="entity-list-panel-filter-picker">
        <ul className="filter-picker-list">
          {availableSpecs.map((spec) => (
            <li key={spec.key}>
              <button
                type="button"
                className="filter-picker-item"
                onClick={(e) => addFilter(e, spec, addChipRef.current ?? (e.currentTarget as HTMLElement))}
              >
                {spec.label}
              </button>
            </li>
          ))}
        </ul>
      </OverlayPanel>

      <OverlayPanel
        ref={editPanel}
        className="entity-list-panel-filter-editor"
        onHide={() => {
          // A chip the user opened but left empty disappears on close — an empty chip that
          // survives is indistinguishable from a real, unset filter and would accumulate.
          setPendingKeys((keys) => keys.filter((k) => filters[k] != null));
          setEditingKey(null);
        }}
      >
        {editingSpec && (
          <ColumnFilter
            key={editingSpec.key}
            label={editingSpec.label}
            kind={editingSpec.kind}
            value={filters[editingSpec.key]}
            selectedOptions={optionsByKey[editingSpec.key]}
            loadOptions={editingSpec.loadOptions}
            autoFocus
            onChange={(value, options) => onChange(editingSpec.key, value, options)}
          />
        )}
      </OverlayPanel>
    </div>
  );
}
