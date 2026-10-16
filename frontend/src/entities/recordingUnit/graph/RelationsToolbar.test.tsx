import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { RelationsToolbar, type RelationsMode } from "./RelationsToolbar";

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

function render(mode: RelationsMode, over: Partial<Parameters<typeof RelationsToolbar>[0]> = {}) {
  const props = { onModeChange: vi.fn(), onShowLinksChange: vi.fn(), ...over };
  act(() => root.render(<RelationsToolbar mode={mode} showLinks {...props} start={<i id="start" />} end={<i id="end" />} />));
  act(() => (container.querySelector("button.entity-list-panel-gear-button") as HTMLElement).click());
  return props;
}

describe("RelationsToolbar", () => {
  it("holds what the view puts before and after the gear", () => {
    render("list");

    expect(container.querySelector("#start")).not.toBeNull();
    expect(container.querySelector("#end")).not.toBeNull();
  });

  it("switches between the graph and the list from the gear", () => {
    const { onModeChange } = render("graph");

    expect(document.querySelector("#relations-mode-graph")?.closest(".p-radiobutton")?.getAttribute("data-p-checked")).toBe("true");
    act(() => (document.querySelector("#relations-mode-list") as HTMLElement).click());

    expect(onModeChange).toHaveBeenCalledWith("list");
  });

  it("offers the graph's links on the graph", () => {
    const { onShowLinksChange } = render("graph");

    act(() => (document.querySelector("#graph-show-links") as HTMLElement).click());

    expect(onShowLinksChange).toHaveBeenCalledWith(false);
  });

  it("offers no graph option on the list", () => {
    render("list");

    expect(document.querySelector("#graph-show-links")).toBeNull();
    expect(document.querySelector("#relations-mode-list")?.closest(".p-radiobutton")?.getAttribute("data-p-checked")).toBe("true");
  });
});
