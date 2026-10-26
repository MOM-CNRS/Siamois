import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { ConceptOptionItem } from "./ConceptOptionItem";
import type { FilterOption } from "./optionSources";

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

function renderItem(option: FilterOption, wrapper?: (children: React.ReactNode) => React.ReactNode) {
  const el = <ConceptOptionItem option={option} />;
  act(() => root.render(wrapper ? wrapper(el) : el));
}

describe("ConceptOptionItem", () => {
  it("renders only the label when the option carries no concept info", () => {
    renderItem({ id: "1", label: "Argile" });

    expect(container.textContent).toBe("Argile");
    expect(container.querySelector("a")).toBeNull();
  });

  it("shows the alt-labels under the pref label, definition, parents and a thesaurus link", () => {
    renderItem({
      id: "1",
      label: "Argile",
      concept: {
        thesaurusUrl: "https://ot.example/?idc=4&idt=th1",
        altLabels: ["Glaise", "Terre glaise"],
        definition: "Roche meuble",
        parents: "Sédiment",
      },
    });

    // The main label is the prefLabel
    expect(container.querySelector(".concept-item-body > span")?.textContent).toBe("Argile");
    // Alt labels are shown underneath
    const altLabels = container.querySelector(".concept-item-altlabels");
    expect(altLabels?.textContent).toContain("Glaise");
    expect(altLabels?.textContent).toContain("Terre glaise");
    const definition = container.querySelector(".concept-item-definition");
    expect(definition?.textContent).toBe("Roche meuble");
    expect(definition?.getAttribute("title")).toBe("Roche meuble");
    expect(container.textContent).toContain("Sédiment");
    const link = container.querySelector("a");
    expect(link?.getAttribute("href")).toBe("https://ot.example/?idc=4&idt=th1");
    expect(link?.getAttribute("target")).toBe("_blank");
  });

  it("does not show the alt-labels line when there are none", () => {
    renderItem({
      id: "1",
      label: "Argile",
      concept: { definition: "Roche meuble" },
    });

    expect(container.querySelector(".concept-item-altlabels")).toBeNull();
  });

  it("does not let a press on the link reach the suggestion list", () => {
    const onMouseDown = vi.fn();
    renderItem({ id: "1", label: "X", concept: { thesaurusUrl: "https://ot.example" } }, (c) => (
      <div onMouseDown={onMouseDown}>{c}</div>
    ));

    act(() => {
      container.querySelector("a")!.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });

    expect(onMouseDown).not.toHaveBeenCalled();
  });
});
