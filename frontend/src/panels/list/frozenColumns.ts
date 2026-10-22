import { useLayoutEffect } from "react";

// Frozen (sticky-left) columns, done here rather than with PrimeReact's Column `frozen`. PrimeReact
// positions every frozen cell on its own: on mount, each cell reads its left neighbour's width and
// writes its own `left` inline — one forced reflow of the whole table per frozen cell, then a
// re-render of each cell. A 100-row page with three frozen columns spent most of its time there
// (~400 layout reads, over half a second before any column was added). Here the offsets are
// measured once on the header cells — every cell of a column has its header's width — and handed
// down as CSS variables, which the body cells read without any script of their own.
export const FROZEN_COLUMN_CLASS = "entity-list-panel-frozen";

const offsetVar = (position: number) => `--entity-list-frozen-left-${position}`;

/** The inline style of the frozen column at `position` (0 = leftmost). */
export function frozenColumnStyle(position: number) {
  return { left: `var(${offsetVar(position)}, 0px)` };
}

/**
 * Keeps the frozen columns' offsets in sync with their header widths, on the table root. The widths
 * follow the content (the table's auto layout), so they can change with any new page, column or
 * font — a ResizeObserver catches all of them, after layout and before paint, reading all widths
 * before writing anything. `signature` names the frozen column set: the observer is re-attached
 * when it changes, since the header cells may then be different elements.
 */
export function useFrozenColumnOffsets(getTable: () => HTMLElement | null | undefined, signature: string) {
  useLayoutEffect(() => {
    const table = getTable();
    if (!table) return;
    const headers = Array.from(table.querySelectorAll<HTMLElement>(`thead th.${FROZEN_COLUMN_CLASS}`));
    if (headers.length === 0) return;
    const update = () => {
      const widths = headers.map((th) => th.getBoundingClientRect().width);
      let left = 0;
      widths.forEach((width, i) => {
        table.style.setProperty(offsetVar(i), `${left}px`);
        left += width;
      });
    };
    if (typeof ResizeObserver === "undefined") {
      update();
      return;
    }
    const observer = new ResizeObserver(update);
    headers.forEach((th) => observer.observe(th));
    return () => observer.disconnect();
    // Keyed on `signature` only: `getTable` is a fresh closure every render, and re-attaching the
    // observer on each one is exactly what the signature avoids.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);
}
