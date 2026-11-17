import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {setLocale} from "../../../i18n";
import {MapNotices} from "./MapNotices";
import type {MapSummary} from "./mapSummary";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root | undefined;
let host: HTMLElement;

beforeEach(() => {
  setLocale("fr");
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
  host?.remove();
});

const quiet: MapSummary = { total: 10, examined: 10, withoutGeometry: 0, truncated: false, empty: false };

function renderNotices(overrides: Partial<MapSummary> = {}) {
  host = document.createElement("div");
  document.body.appendChild(host);
  root = createRoot(host);
  act(() => root!.render(<MapNotices summary={{ ...quiet, ...overrides }} />));
  return [...host.querySelectorAll(".entity-map-notice")];
}

describe("MapNotices", () => {
  it("shows no banner when every row was placed", () => {
    expect(renderNotices()).toHaveLength(0);
    expect(host.textContent).toBe("");
  });

  it("says how many rows have no usable geometry, as information", () => {
    const notices = renderNotices({ withoutGeometry: 3 });

    expect(notices).toHaveLength(1);
    expect(notices[0].textContent).toContain("3 non placé(s) sur la carte");
    expect(notices[0].classList.contains("p-inline-message-info")).toBe(true);
  });

  it("shows the banner from a single row without geometry", () => {
    expect(renderNotices({ withoutGeometry: 1 })[0].textContent).toContain("1 non placé(s) sur la carte");
  });

  it("shows no banner for a negative count (nothing left out)", () => {
    expect(renderNotices({ withoutGeometry: -1 })).toHaveLength(0);
  });

  it("warns that the result set was truncated, with what was examined out of the total", () => {
    const notices = renderNotices({ total: 12000, examined: 5000, truncated: true });

    expect(notices).toHaveLength(1);
    expect(notices[0].textContent).toContain("Seuls les 5000 premiers éléments sur 12000 sont examinés");
    expect(notices[0].classList.contains("p-inline-message-warn")).toBe(true);
  });

  it("says the map is empty when the result set is", () => {
    const notices = renderNotices({ total: 0, examined: 0, empty: true });

    expect(notices).toHaveLength(1);
    expect(notices[0].textContent).toContain("Aucune géométrie à afficher");
    expect(notices[0].classList.contains("p-inline-message-info")).toBe(true);
  });

  it("stacks every banner that applies, in order", () => {
    const notices = renderNotices({ withoutGeometry: 2, truncated: true, empty: true });

    expect(notices.map((n) => n.classList.contains("p-inline-message-warn"))).toEqual([false, true, false]);
    expect(notices[0].textContent).toContain("2 non placé(s)");
    expect(notices[2].textContent).toContain("Aucune géométrie");
  });

  it("follows the page language", () => {
    setLocale("en");
    try {
      expect(renderNotices({ empty: true })[0].textContent).toContain("No geometry to display");
    } finally {
      setLocale("fr");
    }
  });
});
