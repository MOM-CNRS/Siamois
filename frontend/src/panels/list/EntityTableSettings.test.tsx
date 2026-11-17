import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterEach, describe, expect, it, vi} from "vitest";
import {EntityTableSettings, type EntityTableSettingsProps} from "./EntityTableSettings";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root | undefined;
let host: HTMLElement | undefined;

afterEach(() => {
  act(() => root?.unmount());
  host?.remove();
  document.body.innerHTML = "";
});

function renderSettings(overrides: Partial<EntityTableSettingsProps>) {
  host = document.createElement("div");
  document.body.appendChild(host);
  root = createRoot(host);
  act(() => root!.render(<EntityTableSettings views={null} openMode={null} columns={null} actionBar={null} mapGeometry={null} {...overrides} />));
  const gear = host.querySelector<HTMLButtonElement>(".entity-list-panel-gear-button");
  if (gear) act(() => gear.click());
}

function radiogroup(title: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[role="radiogroup"][aria-label="${title}"]`);
}

describe("EntityTableSettings' map section", () => {
  it("offers the geometry columns, the one in use checked, and says which one is picked", () => {
    const onChange = vi.fn();
    renderSettings({
      mapGeometry: {
        options: [
          { mode: "-332", label: "Géométrie", icon: "bi bi-bounding-box-circles" },
          { mode: "40", label: "Emprise de fouille", icon: "bi bi-bounding-box-circles" },
        ],
        value: "-332",
        onChange,
      },
    });

    const group = radiogroup("Géométrie affichée")!;
    const tiles = [...group.querySelectorAll<HTMLButtonElement>('[role="radio"]')];
    expect(tiles.map((tile) => [tile.textContent, tile.getAttribute("aria-checked")])).toEqual([
      ["Géométrie", "true"],
      ["Emprise de fouille", "false"],
    ]);

    act(() => tiles[1].click());
    expect(onChange).toHaveBeenCalledWith("40");
  });

  it("shows the section for a single geometry column, which says what the map draws", () => {
    renderSettings({
      mapGeometry: { options: [{ mode: "-332", label: "Géométrie", icon: "bi bi-bounding-box-circles" }], value: "-332", onChange: () => {} },
    });

    const tiles = [...radiogroup("Géométrie affichée")!.querySelectorAll('[role="radio"]')];
    expect(tiles.map((tile) => [tile.textContent, tile.getAttribute("aria-checked")])).toEqual([["Géométrie", "true"]]);
  });

  it("has no map section without a geometry column", () => {
    renderSettings({ openMode: { value: "overview", onChange: () => {} } });
    expect(radiogroup("Géométrie affichée")).toBeNull();
  });
});
