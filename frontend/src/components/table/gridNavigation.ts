import type {KeyboardEvent} from "react";

// Keyboard navigation of a table's body, as in a spreadsheet: the arrows move the focus to the neighbouring
// cell, Home/End to the ends of the row, and Enter/Space open what a focused cell offers (its cells are
// `role="button"` spans, which only a click activated until now). Tab still walks the cells one by one.

const FOCUSABLE = '[tabindex]:not([tabindex="-1"]), button:not(:disabled), a[href], input:not(:disabled):not([type="hidden"])';

// Where typing or the arrows mean something else.
const OWN_KEYS = 'input:not([type="checkbox"]):not([type="radio"]), textarea, select, [contenteditable="true"]';

function focusableIn(cell: Element | undefined): HTMLElement | null {
  return cell?.querySelector<HTMLElement>(FOCUSABLE) ?? null;
}

function cellsOf(row: Element | null | undefined): Element[] {
  return row ? [...row.children].filter((c) => c.matches("td")) : [];
}

/** The element to focus when `key` is pressed on `target`, or null when the key is not a move from this cell. */
export function gridTarget(target: HTMLElement, key: string): HTMLElement | null {
  const cell = target.closest("tbody td");
  const row = cell?.parentElement;
  if (!cell || !row) return null;
  const cells = cellsOf(row);
  const column = cells.indexOf(cell);

  if (key === "ArrowLeft" || key === "ArrowRight") {
    const step = key === "ArrowLeft" ? -1 : 1;
    for (let i = column + step; i >= 0 && i < cells.length; i += step) {
      const found = focusableIn(cells[i]);
      if (found) return found;
    }
    return null;
  }
  if (key === "Home" || key === "End") {
    const ordered = key === "Home" ? cells : [...cells].reverse();
    for (const c of ordered) {
      const found = focusableIn(c);
      if (found) return found;
    }
    return null;
  }
  if (key === "ArrowUp" || key === "ArrowDown") {
    const step = key === "ArrowUp" ? "previousElementSibling" : "nextElementSibling";
    // A row with nothing to focus in this column (a skeleton, an empty read-only cell) is passed over.
    for (let r = row[step]; r; r = r[step]) {
      const found = focusableIn(cellsOf(r)[column]);
      if (found) return found;
    }
    return null;
  }
  return null;
}

/** onKeyDown for a table's wrapper. */
export function onGridKeyDown(e: KeyboardEvent<HTMLElement>): void {
  const target = e.target as HTMLElement;
  if (e.altKey || e.metaKey || target.closest(OWN_KEYS)) return;
  if ((e.key === "Enter" || e.key === " ") && target.matches('span[role="button"]') && target.closest("tbody td")) {
    e.preventDefault();
    target.click();
    return;
  }
  if (e.ctrlKey || e.shiftKey) return;
  const next = gridTarget(target, e.key);
  if (!next) return;
  e.preventDefault();
  next.focus();
}
