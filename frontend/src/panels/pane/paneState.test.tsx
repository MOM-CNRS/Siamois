import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {PaneStateProvider, PaneStateScope, PaneStateStore, usePaneState} from "./paneState";

let container: HTMLDivElement;
let root: Root;
let latest: [number, (v: number | ((p: number) => number)) => void];

function Probe() {
  latest = usePaneState("count", 0);
  return null;
}

function render(store: PaneStateStore, scope: string, mounted = true) {
  act(() => {
    root.render(
      <PaneStateProvider store={store}>
        {mounted && (
          <PaneStateScope name={scope}>
            <Probe />
          </PaneStateScope>
        )}
      </PaneStateProvider>,
    );
  });
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

describe("usePaneState", () => {
  it("restores the value when a panel of the same identity mounts again", () => {
    const store = new PaneStateStore();
    render(store, "pane-a");
    act(() => latest[1](3));
    render(store, "pane-a", false);
    render(store, "pane-a");
    expect(latest[0]).toBe(3);
  });

  it("keeps identities apart", () => {
    const store = new PaneStateStore();
    render(store, "pane-a");
    act(() => latest[1](3));
    render(store, "pane-a", false);
    render(store, "pane-b");
    expect(latest[0]).toBe(0);
  });

  it("carries the value over when the scope changes while mounted", () => {
    const store = new PaneStateStore();
    render(store, "pane-a");
    act(() => latest[1](2));
    render(store, "pane-b");
    expect(latest[0]).toBe(2);
  });

  it("retain drops the panes that are neither open nor stacked", () => {
    const store = new PaneStateStore();
    store.set("/pane-a#x", 1);
    store.set("/pane-a/tab:t#y", 2);
    store.set("/pane-b#x", 3);
    store.retain(["pane-a"]);
    expect(store.has("/pane-a#x")).toBe(true);
    expect(store.has("/pane-a/tab:t#y")).toBe(true);
    expect(store.has("/pane-b#x")).toBe(false);
  });

  it("is plain local state without a store", () => {
    act(() => root.render(<Probe />));
    act(() => latest[1]((n) => n + 1));
    expect(latest[0]).toBe(1);
  });
});
