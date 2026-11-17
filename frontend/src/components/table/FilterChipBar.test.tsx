import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {FilterChipBar, type FilterSpec} from "./FilterChipBar";
import {operatorsOf} from "./filterOperators";

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
  document.querySelectorAll(".p-overlaypanel, .filter-operator-panel").forEach((el) => el.remove());
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

  it("picks the values of a filter with fixed options in a multi-select dropdown, not a search box", () => {
    const options = [{ id: "A", label: "En cours" }, { id: "B", label: "Validé" }];
    const specs: FilterSpec[] = [{ key: "status", label: "Statut", kind: "in", loadOptions: async () => options, options }];
    act(() => root.render(<FilterChipBar specs={specs} filters={{}} optionsByKey={{}} onChange={vi.fn()} />));

    act(() => (container.querySelector(".filter-chip-add") as HTMLElement).click());
    act(() => (document.querySelector(".filter-picker-item") as HTMLElement).click());

    expect(document.querySelector(".entity-list-panel-filter-editor .p-multiselect")).not.toBeNull();
    expect(document.querySelector(".entity-list-panel-filter-editor .p-autocomplete")).toBeNull();
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

  it("reads « <column>… [operator] ⋯ » in the editor of a datatable filter, and changes the operator of its value", () => {
    const onChange = vi.fn();
    const specs: FilterSpec[] = [{ key: "-12", label: "Nom", kind: "contains", operators: operatorsOf("contains") }];
    act(() => root.render(<FilterChipBar specs={specs} filters={{ "-12": { op: "contains", v: "ab" } }} optionsByKey={{}} onChange={onChange} />));

    act(() => (container.querySelector(".filter-chip-body") as HTMLElement).click());
    expect(document.querySelector(".filter-editor-label")?.textContent).toBe("Nom");
    expect(document.querySelector(".filter-operator .p-dropdown-label")?.textContent).toBe("Contient");
    expect((document.querySelector(".filter-editor-value input") as HTMLInputElement).placeholder).toBe("Saisissez une valeur");

    act(() => (document.querySelector(".filter-operator") as HTMLElement).click());
    const startsWith = [...document.querySelectorAll(".filter-operator-panel .p-dropdown-item")].find((el) => el.textContent === "Commence par");
    act(() => (startsWith as HTMLElement).click());

    expect(onChange).toHaveBeenCalledWith("-12", { op: "contains", v: "ab", operator: "startsWith" });
  });

  it("applies « est vide » at once, and shows the operator on the chip", () => {
    const onChange = vi.fn();
    const specs: FilterSpec[] = [{ key: "-12", label: "Nom", kind: "contains", operators: operatorsOf("contains") }];
    act(() => root.render(<FilterChipBar specs={specs} filters={{ "-12": { op: "empty" } }} optionsByKey={{}} onChange={onChange} />));

    expect(container.querySelector(".filter-chip-value")?.textContent).toBe("est vide");
    act(() => (container.querySelector(".filter-chip-body") as HTMLElement).click());
    // No value to type for « est vide ».
    expect(document.querySelector(".filter-editor-value")).toBeNull();
  });

  it("removes the filter from the « ⋯ » menu", () => {
    const onChange = vi.fn();
    const specs: FilterSpec[] = [{ key: "-12", label: "Nom", kind: "contains", operators: operatorsOf("contains") }];
    act(() => root.render(<FilterChipBar specs={specs} filters={{ "-12": { op: "contains", v: "ab" } }} optionsByKey={{}} onChange={onChange} />));

    act(() => (container.querySelector(".filter-chip-body") as HTMLElement).click());
    act(() => (document.querySelector(".filter-editor-actions-toggle") as HTMLElement).click());
    act(() => (document.querySelector(".filter-editor-actions .filter-picker-item") as HTMLElement).click());

    expect(onChange).toHaveBeenCalledWith("-12", undefined);
  });

  it("keeps the bare value widget for a filter without operators (outside the datatables)", () => {
    const specs: FilterSpec[] = [{ key: "kind", label: "Type", kind: "contains" }];
    act(() => root.render(<FilterChipBar specs={specs} filters={{ kind: { op: "contains", v: "a" } }} optionsByKey={{}} onChange={vi.fn()} />));

    act(() => (container.querySelector(".filter-chip-body") as HTMLElement).click());
    expect(document.querySelector(".filter-editor")).toBeNull();
    expect(document.querySelector(".entity-list-panel-filter-editor input")).not.toBeNull();
  });
});
