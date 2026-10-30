import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {type RelationsMode, RelationsToolbar} from "./RelationsToolbar";

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
  const props = { onModeChange: vi.fn(), onDetailedChange: vi.fn(), ...over };
  act(() => root.render(<RelationsToolbar mode={mode} detailed={false} {...props} start={<i id="start" />} end={<i id="end" />} />));
  act(() => (container.querySelector("button.entity-list-panel-gear-button") as HTMLElement).click());
  return props;
}

const tile = (label: string) => [...document.querySelectorAll<HTMLElement>(".entity-list-panel-view-tile")].find((el) => el.textContent === label)!;

describe("RelationsToolbar", () => {
  it("holds what the view puts before and after the gear", () => {
    render("list");

    expect(container.querySelector("#start")).not.toBeNull();
    expect(container.querySelector("#end")).not.toBeNull();
  });

  it("offers the graph and the list as tiles, the one in use outlined, and switches from the gear", () => {
    const { onModeChange } = render("graph");

    expect(tile("Graphe").classList.contains("is-active")).toBe(true);
    act(() => tile("Liste").click());

    expect(onModeChange).toHaveBeenCalledWith("list");
  });

  it("does nothing when the mode in use is chosen again", () => {
    const { onModeChange } = render("graph");

    act(() => tile("Graphe").click());

    expect(onModeChange).not.toHaveBeenCalled();
  });

  it("offers the detail of the links with the center on the graph", () => {
    const { onDetailedChange } = render("graph");

    act(() => (document.querySelector("#graph-detailed") as HTMLElement).click());

    expect(onDetailedChange).toHaveBeenCalledWith(true);
  });

  it("offers no graph option on the list", () => {
    render("list");

    expect(document.querySelector("#graph-detailed")).toBeNull();
    expect(tile("Liste").classList.contains("is-active")).toBe(true);
  });
});
