import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import type {ListSelection} from "./useListSelection";
import {type SelectionGuard, useSelectionGuard} from "./useSelectionGuard";

let container: HTMLDivElement;
let root: Root;
let current: SelectionGuard;

/**
 * A selection of `count` rows.
 */
function selectionOf(count: number): ListSelection {
  return { all: false, count, rows: [], resolve: vi.fn(), setRows: vi.fn(), setAll: vi.fn(), clear: vi.fn() };
}

/**
 * Renders the guard and its warning over this selection.
 */
function Probe({ selection }: { selection: ListSelection }) {
  current = useSelectionGuard(selection);
  return <>{current.dialog}</>;
}

/**
 * The button labelled `label` (the warning is portalled to the body).
 */
function button(label: string): HTMLButtonElement {
  const found = [...document.querySelectorAll("button")].find((b) => b.textContent?.trim() === label);
  if (!found) throw new Error(`No button « ${label} »`);
  return found;
}

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("useSelectionGuard", () => {
  it("applies a filter at once when nothing is selected", () => {
    const apply = vi.fn();
    act(() => root.render(<Probe selection={selectionOf(0)} />));

    act(() => current.guard(apply));

    expect(apply).toHaveBeenCalled();
  });

  it("warns before applying a filter over a selection, then empties it on agreement", () => {
    const apply = vi.fn();
    const selection = selectionOf(3);
    act(() => root.render(<Probe selection={selection} />));

    act(() => current.guard(apply));

    expect(apply).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain("3 éléments sont sélectionnés. Appliquer ce filtre videra la sélection.");

    act(() => button("Appliquer et vider la sélection").click());

    expect(selection.clear).toHaveBeenCalled();
    expect(apply).toHaveBeenCalled();
  });

  it("keeps the selection and drops the filter when the user cancels", () => {
    const apply = vi.fn();
    const selection = selectionOf(3);
    act(() => root.render(<Probe selection={selection} />));
    act(() => current.guard(apply));

    act(() => button("Annuler").click());

    expect(apply).not.toHaveBeenCalled();
    expect(selection.clear).not.toHaveBeenCalled();
  });
});
