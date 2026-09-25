import { useState, type DragEvent, type KeyboardEvent, type ReactNode } from "react";
import { InputText } from "primereact/inputtext";
import { InputSwitch } from "primereact/inputswitch";

// The gear's two settings overlays (columns, row actions) are the same control: what is shown, in
// which order, and what is put away. Two sections — the shown items, reorderable by dragging their
// handle (or Alt+↑/↓ on it), and the rest — each item with a switch that moves it across.

export interface VisibilityItem {
  id: string;
  label: string;
  icon?: string;
}

export interface VisibilityChooserProps {
  // Every item, in the order the hidden section lists them.
  items: VisibilityItem[];
  // The shown items' ids, in display order.
  visible: string[];
  onChange: (visible: string[]) => void;
  visibleTitle: string;
  hiddenTitle: string;
  // Shown first and always on (the bookmark): listed so the rule is visible, never movable.
  locked?: VisibilityItem[];
  searchable?: boolean;
  searchPlaceholder?: string;
  className?: string;
}

/** `items` with the one at `from` moved to `to` (both indices into the original array). */
export function moveItem<T>(items: readonly T[], from: number, to: number): T[] {
  const next = items.slice();
  if (from < 0 || from >= next.length || to < 0 || to >= next.length || from === to) return next;
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

export function VisibilityChooser({
  items,
  visible,
  onChange,
  visibleTitle,
  hiddenTitle,
  locked = [],
  searchable,
  searchPlaceholder = "Rechercher",
  className,
}: VisibilityChooserProps) {
  const [query, setQuery] = useState("");
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [overIndex, setOverIndex] = useState<number | null>(null);

  const byId = new Map(items.map((item) => [item.id, item]));
  const needle = query.trim().toLocaleLowerCase();
  const matches = (item: VisibilityItem) => !needle || item.label.toLocaleLowerCase().includes(needle);

  const shown = visible.map((id) => byId.get(id)).filter((item): item is VisibilityItem => item != null);
  const hidden = items.filter((item) => !visible.includes(item.id));
  // Reordering a filtered list would be ambiguous (moving past items you can't see), so dragging
  // is only offered on the full list.
  const canReorder = !needle;

  function hide(id: string) {
    onChange(visible.filter((v) => v !== id));
  }

  function show(id: string) {
    onChange([...visible, id]);
  }

  function reorder(from: number, to: number) {
    const ids = shown.map((item) => item.id);
    onChange(moveItem(ids, from, to));
  }

  function onDrop(e: DragEvent, to: number) {
    e.preventDefault();
    if (dragIndex != null) reorder(dragIndex, to);
    setDragIndex(null);
    setOverIndex(null);
  }

  function onHandleKey(e: KeyboardEvent, index: number) {
    if (!e.altKey) return;
    if (e.key === "ArrowUp" && index > 0) {
      e.preventDefault();
      reorder(index, index - 1);
    } else if (e.key === "ArrowDown" && index < shown.length - 1) {
      e.preventDefault();
      reorder(index, index + 1);
    }
  }

  function renderLabel(item: VisibilityItem): ReactNode {
    return (
      <span className="visibility-chooser-label" title={item.label}>
        {item.icon && <i className={item.icon} aria-hidden="true" />}
        <span>{item.label}</span>
      </span>
    );
  }

  const shownMatches = shown.map((item, index) => ({ item, index })).filter(({ item }) => matches(item));
  const hiddenMatches = hidden.filter(matches);

  return (
    <div className={`visibility-chooser${className ? ` ${className}` : ""}`}>
      {searchable && (
        <span className="p-input-icon-left visibility-chooser-search">
          <i className="bi bi-search" />
          <InputText
            value={query}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
            onChange={(e) => setQuery(e.target.value)}
            autoFocus
          />
        </span>
      )}
      <div className="visibility-chooser-body">
        <section className="visibility-chooser-section" data-section="visible">
          <h4 className="visibility-chooser-title">
            {visibleTitle} <span className="visibility-chooser-count">{locked.length + shown.length}</span>
          </h4>
          <ul>
            {locked.filter(matches).map((item) => (
              <li key={item.id} className="visibility-chooser-item is-locked" data-id={item.id}>
                <span className="visibility-chooser-handle is-disabled" aria-hidden="true">
                  <i className="bi bi-lock" />
                </span>
                {renderLabel(item)}
                <InputSwitch checked disabled aria-label={`${item.label} : toujours visible`} />
              </li>
            ))}
            {shownMatches.map(({ item, index }) => (
              <li
                key={item.id}
                data-id={item.id}
                className={[
                  "visibility-chooser-item",
                  dragIndex === index ? "is-dragging" : "",
                  overIndex === index && dragIndex !== index ? "is-drop-target" : "",
                ]
                  .filter(Boolean)
                  .join(" ")}
                onDragOver={(e) => {
                  if (dragIndex == null) return;
                  e.preventDefault();
                  setOverIndex(index);
                }}
                onDrop={(e) => onDrop(e, index)}
              >
                <span
                  className={`visibility-chooser-handle${canReorder ? "" : " is-disabled"}`}
                  draggable={canReorder}
                  role="button"
                  tabIndex={canReorder ? 0 : -1}
                  aria-label={`Déplacer « ${item.label} » (Alt+↑/↓)`}
                  title={canReorder ? "Glisser pour réordonner" : "Effacez la recherche pour réordonner"}
                  onDragStart={(e) => {
                    e.dataTransfer.effectAllowed = "move";
                    e.dataTransfer.setData("text/plain", item.id);
                    setDragIndex(index);
                  }}
                  onDragEnd={() => {
                    setDragIndex(null);
                    setOverIndex(null);
                  }}
                  onKeyDown={(e) => onHandleKey(e, index)}
                >
                  <i className="bi bi-grip-vertical" aria-hidden="true" />
                </span>
                {renderLabel(item)}
                <InputSwitch checked aria-label={`Masquer « ${item.label} »`} onChange={() => hide(item.id)} />
              </li>
            ))}
          </ul>
          {shownMatches.length === 0 && locked.filter(matches).length === 0 && (
            <p className="visibility-chooser-empty">Aucun élément</p>
          )}
        </section>
        <section className="visibility-chooser-section" data-section="hidden">
          <h4 className="visibility-chooser-title">
            {hiddenTitle} <span className="visibility-chooser-count">{hidden.length}</span>
          </h4>
          <ul>
            {hiddenMatches.map((item) => (
              <li key={item.id} className="visibility-chooser-item" data-id={item.id}>
                <span className="visibility-chooser-handle is-disabled" aria-hidden="true" />
                {renderLabel(item)}
                <InputSwitch checked={false} aria-label={`Afficher « ${item.label} »`} onChange={() => show(item.id)} />
              </li>
            ))}
          </ul>
          {hiddenMatches.length === 0 && <p className="visibility-chooser-empty">Aucun élément</p>}
        </section>
      </div>
    </div>
  );
}
