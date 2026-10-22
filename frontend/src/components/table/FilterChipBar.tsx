import { useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { flushSync } from "react-dom";
import { Button } from "primereact/button";
import { OverlayPanel } from "primereact/overlaypanel";
import { BottomSheet } from "../BottomSheet";
import { useIsMobile } from "../../viewport";
import { ColumnFilter, describeFilterValue } from "./ColumnFilter";
import type { FilterKind, FilterOption } from "../../fields/optionSources";
import type { FilterValue } from "../../panels/tableState";
import { t } from "../../i18n";

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
  // Phone width: both panels are drawers from the bottom, opened by these flags instead of an anchored overlay.
  const mobile = useIsMobile();
  const [addSheetOpen, setAddSheetOpen] = useState(false);
  const [editSheetOpen, setEditSheetOpen] = useState(false);
  const hideAdd = () => (mobile ? setAddSheetOpen(false) : addPanel.current?.hide());
  // Only the pickers go in a drawer; a text, number or date filter is typed into the overlay, as on a desktop.
  const sheetKinds = (key: string) => mobile && !["contains", "range", "date-range"].includes(specs.find((s) => s.key === key)?.kind ?? "");
  const hideEditor = () => {
    setEditSheetOpen(false);
    editPanel.current?.hide();
  };

  const activeKeys = specs
    .map((s) => s.key)
    .filter((key) => filters[key] != null || pendingKeys.includes(key));
  const availableSpecs = specs.filter((s) => !activeKeys.includes(s.key));

  function openEditor(e: ReactMouseEvent, key: string, anchor: HTMLElement) {
    setEditingKey(key);
    if (sheetKinds(key)) setEditSheetOpen(true);
    else editPanel.current?.show(e, anchor);
  }

  function addFilter(e: ReactMouseEvent, spec: FilterSpec, anchor: HTMLElement) {
    hideAdd();
    const addKey = (keys: string[]) => (keys.includes(spec.key) ? keys : [...keys, spec.key]);
    if (availableSpecs.length > 1) {
      setPendingKeys(addKey);
      openEditor(e, spec.key, anchor);
      return;
    }
    // The last filter on offer: the « add » chip it was picked from goes with it, so the editor is
    // anchored on the chip that takes its place — rendered now, not on the next update.
    flushSync(() => setPendingKeys(addKey));
    openEditor(e, spec.key, barRef.current?.querySelector<HTMLElement>(`[data-chip-key="${spec.key}"]`) ?? anchor);
  }

  function removeFilter(key: string) {
    setPendingKeys((keys) => keys.filter((k) => k !== key));
    if (editingKey === key) {
      setEditingKey(null);
      hideEditor();
    }
    if (filters[key] != null) onChange(key, undefined);
  }

  const editingSpec = specs.find((s) => s.key === editingKey);
  const addChipRef = useRef<HTMLButtonElement>(null);
  const barRef = useRef<HTMLDivElement>(null);

  const addList = (
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
  );

  // A chip the user opened but left empty disappears on close — an empty chip that survives is
  // indistinguishable from a real, unset filter and would accumulate.
  function onEditorHidden() {
    setPendingKeys((keys) => keys.filter((k) => filters[k] != null));
    setEditingKey(null);
  }

  const editor = editingSpec && (
    <ColumnFilter
      key={editingSpec.key}
      label={editingSpec.label}
      kind={editingSpec.kind}
      value={filters[editingSpec.key]}
      selectedOptions={optionsByKey[editingSpec.key]}
      loadOptions={editingSpec.loadOptions}
      autoFocus={!editSheetOpen}
      onChange={(value, options) => onChange(editingSpec.key, value, options)}
    />
  );

  return (
    <div className="entity-list-panel-filter-chips" ref={barRef}>
      {activeKeys.map((key) => {
        const spec = specs.find((s) => s.key === key)!;
        const summary = describeFilterValue(filters[key], optionsByKey[key]);
        return (
          <span key={key} data-chip-key={key} className={`filter-chip${summary ? "" : " filter-chip-empty"}`}>
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
              aria-label={t("filter.remove", { label: spec.label })}
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
          onClick={(e) => (mobile ? setAddSheetOpen(true) : addPanel.current?.toggle(e))}
        >
          <i className="bi bi-plus" />
          {activeKeys.length === 0 ? t("filter.add") : t("filter.one")}
        </button>
      )}

      {mobile ? (
        <BottomSheet visible={addSheetOpen} onHide={() => setAddSheetOpen(false)} ariaLabel={t("filter.add")} className="filter-sheet">
          {addList}
        </BottomSheet>
      ) : (
        <OverlayPanel ref={addPanel} className="entity-list-panel-filter-picker">
          {addList}
        </OverlayPanel>
      )}

      {mobile && (
        <BottomSheet
          visible={editSheetOpen}
          onHide={() => {
            setEditSheetOpen(false);
            onEditorHidden();
          }}
          ariaLabel={editingSpec?.label ?? t("filter.add")}
          className="filter-sheet"
        >
          {editor}
          <div className="filter-sheet-actions">
            <Button type="button" size="small" label={t("field.pickerDone")}
              onClick={() => {
                setEditSheetOpen(false);
                onEditorHidden();
              }}
            />
          </div>
        </BottomSheet>
      )}
      {!(mobile && editSheetOpen) && (
        <OverlayPanel ref={editPanel} className="entity-list-panel-filter-editor" onHide={onEditorHidden}>
          {editor}
        </OverlayPanel>
      )}
    </div>
  );
}
