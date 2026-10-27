import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {FilterChipBar, type FilterSpec} from "./FilterChipBar";

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.querySelectorAll(".p-overlaypanel").forEach((el) => el.remove());
});

describe("FilterChipBar", () => {
  it("labels an active filter with its chosen options", () => {
    const specs: FilterSpec[] = [{ key: "kind", label: "Type", kind: "in" }];
    act(() => root.render(
      <FilterChipBar specs={specs} filters={{ kind: { op: "in", v: ["a"] } }} optionsByKey={{ kind: [{ id: "a", label: "Antérieur" }] }} onChange={vi.fn()} />,
    ));

    expect(container.querySelector(".filter-chip-value")?.textContent).toContain("Antérieur");
    // Nothing left to add.
    expect(container.querySelector(".filter-chip-add")).toBeNull();
  });

  it("opens the editor of the last filter on offer on the chip that replaces the « add » chip", () => {
    const specs: FilterSpec[] = [{ key: "kind", label: "Type", kind: "in", loadOptions: async () => [] }];
    act(() => root.render(<FilterChipBar specs={specs} filters={{}} optionsByKey={{}} onChange={vi.fn()} />));

    act(() => (container.querySelector(".filter-chip-add") as HTMLElement).click());
    act(() => (document.querySelector(".filter-picker-item") as HTMLElement).click());

    // The add chip went with the only filter there was to add; its chip stands in the bar, and the editor is open.
    expect(container.querySelector(".filter-chip-add")).toBeNull();
    expect(container.querySelector("[data-chip-key='kind']")).not.toBeNull();
    expect(document.querySelector(".entity-list-panel-filter-editor input")).not.toBeNull();
  });

  it("keeps the first filters in the bar and folds the rest behind « +N »", () => {
    const specs: FilterSpec[] = ["a", "b", "c", "d", "e"].map((key) => ({ key, label: key.toUpperCase(), kind: "contains" }));
    const filters = Object.fromEntries(specs.map((s) => [s.key, { op: "contains" as const, v: "x" }]));
    act(() => root.render(<FilterChipBar specs={specs} filters={filters} optionsByKey={{}} onChange={vi.fn()} />));

    expect(container.querySelectorAll(".entity-list-panel-filter-chips > [data-chip-key]")).toHaveLength(3);
    const more = container.querySelector(".filter-chip-more") as HTMLElement;
    expect(more.textContent).toBe("+2");

    act(() => more.click());
    expect(document.querySelectorAll(".entity-list-panel-filter-more [data-chip-key]")).toHaveLength(2);
  });

  it("narrows the « add a filter » list with the typed text, ignoring case and accents", () => {
    const specs: FilterSpec[] = ["État", "Type", "Période"].map((label) => ({ key: label, label, kind: "contains" }));
    act(() => root.render(<FilterChipBar specs={specs} filters={{}} optionsByKey={{}} onChange={vi.fn()} />));
    act(() => (container.querySelector(".filter-chip-add") as HTMLElement).click());
    const items = () => [...document.querySelectorAll(".filter-picker-item")].map((el) => el.textContent);
    expect(items()).toEqual(["État", "Type", "Période"]);

    const input = document.querySelector(".filter-picker-search") as HTMLInputElement;
    act(() => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, "etat");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    expect(items()).toEqual(["État"]);
  });
});
