import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { BottomSheet } from "../components/BottomSheet";
import { useDebounced } from "../search/SearchOverlay";
import { t } from "../i18n";
import type { FilterOption, OptionList } from "./optionSources";

const SEARCH_DEBOUNCE_MS = 250;

export interface ReferencePickerSheetProps {
  multiple: boolean;
  selected: FilterOption[];
  disabled?: boolean;
  placeholder?: string;
  // Null while the field has nothing to search (no organization yet).
  loadOptions: ((q?: string) => Promise<FilterOption[]>) | null;
  itemTemplate?: (option: FilterOption) => ReactNode;
  // What the last search said about the sources it could not narrow.
  onSearched?: (found: OptionList) => void;
  // A pick: one option (or none, cleared) for a single field, the whole list for a multiple one.
  onCommit: (next: FilterOption[]) => void;
  // « Nouveau » is offered when this is given; it receives the trigger the form opens next to.
  onNew?: (trigger: HTMLElement | null) => void;
  newLabel?: string;
  hint?: string;
  emptyMessage?: string;
  style?: CSSProperties;
}

/**
 * The reference picker on a phone: the field is a button, and tapping it opens a drawer from the bottom with the
 * search box and the results — an AutoComplete's own dropdown would sit under the keyboard. The same loader as
 * the desktop picker feeds it; a single field picks on tap, a multiple one on « Terminé ».
 */
export function ReferencePickerSheet({
  multiple,
  selected,
  disabled,
  placeholder,
  loadOptions,
  itemTemplate,
  onSearched,
  onCommit,
  onNew,
  newLabel,
  hint,
  emptyMessage,
  style,
}: Readonly<ReferencePickerSheetProps>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState<FilterOption[]>(selected);
  const [options, setOptions] = useState<FilterOption[]>([]);
  const [loading, setLoading] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const debounced = useDebounced(query, SEARCH_DEBOUNCE_MS);

  useEffect(() => {
    if (!open || !loadOptions) return;
    let current = true;
    setLoading(true);
    loadOptions(debounced)
      .then((found) => {
        if (!current) return;
        setOptions(found);
        onSearched?.(found as OptionList);
      })
      .catch(() => current && setOptions([]))
      .finally(() => current && setLoading(false));
    return () => {
      current = false;
    };
    // The loader changes identity every render; what it searches for is the query.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, debounced]);

  function openSheet() {
    setQuery("");
    setDraft(selected);
    setOpen(true);
  }

  function pick(option: FilterOption) {
    if (!multiple) {
      setOpen(false);
      onCommit([option]);
      return;
    }
    setDraft((current) => (current.some((o) => o.id === option.id) ? current.filter((o) => o.id !== option.id) : [...current, option]));
  }

  function finish() {
    setOpen(false);
    if (multiple) onCommit(draft);
  }

  const chosen = (option: FilterOption) => (multiple ? draft : selected).some((o) => o.id === option.id);

  return (
    <>
      <button
        type="button"
        ref={trigger}
        className="resource-ref-sheet-trigger p-inputtext"
        style={style}
        disabled={disabled}
        aria-haspopup="dialog"
        onClick={openSheet}
      >
        {selected.length > 0 ? (
          selected.map((o) => (
            <span key={o.id} className="resource-ref-sheet-token">
              {o.label}
            </span>
          ))
        ) : (
          <span className="resource-ref-sheet-placeholder">{placeholder ?? t("field.pickerPlaceholder")}</span>
        )}
        <i className="bi bi-chevron-up resource-ref-sheet-chevron" aria-hidden="true" />
      </button>
      <BottomSheet visible={open} onHide={() => (multiple ? finish() : setOpen(false))} ariaLabel={placeholder ?? t("field.pickerPlaceholder")} className="resource-ref-sheet">
        <InputText
          className="resource-ref-sheet-search"
          value={query}
          placeholder={t("field.pickerSearch")}
          aria-label={t("field.pickerSearch")}
          autoFocus
          onChange={(e) => setQuery(e.target.value)}
        />
        {hint && <small className="resource-ref-narrow-hint">{hint}</small>}
        <ul className="resource-ref-sheet-list" role="listbox" aria-multiselectable={multiple}>
          {options.map((option) => (
            <li key={option.id} role="option" aria-selected={chosen(option)}>
              <button type="button" className={`resource-ref-sheet-option${chosen(option) ? " is-chosen" : ""}`} onClick={() => pick(option)}>
                <span className="resource-ref-sheet-option-body">{itemTemplate ? itemTemplate(option) : option.label}</span>
                {chosen(option) && <i className="bi bi-check-lg" aria-hidden="true" />}
              </button>
            </li>
          ))}
          {!loading && options.length === 0 && <li className="resource-ref-sheet-empty">{emptyMessage ?? t("field.noResult")}</li>}
        </ul>
        <div className="resource-ref-sheet-actions">
          {onNew && (
            <Button
              type="button"
              text
              size="small"
              icon="bi bi-plus-lg"
              label={newLabel}
              onClick={() => {
                setOpen(false);
                onNew(trigger.current);
              }}
            />
          )}
          {!multiple && selected.length > 0 && (
            <Button
              type="button"
              text
              size="small"
              icon="bi bi-x-lg"
              label={t("field.pickerClear")}
              onClick={() => {
                setOpen(false);
                onCommit([]);
              }}
            />
          )}
          {multiple && <Button type="button" size="small" label={t("field.pickerDone")} onClick={finish} />}
        </div>
      </BottomSheet>
    </>
  );
}
