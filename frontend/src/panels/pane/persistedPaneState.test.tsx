import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { PersistedTabState, usePersistedPaneState } from "./persistedPaneState";

let container: HTMLDivElement;
let root: Root;
let latest: ReturnType<typeof usePersistedPaneState<number>>;

function Harness({ isValid }: { isValid?: (saved: unknown) => boolean }) {
  latest = usePersistedPaneState<number>("n", 1, { isValid });
  return null;
}

function mount(read: (name: string) => unknown, write = vi.fn(), isValid?: (saved: unknown) => boolean) {
  act(() => {
    root.render(
      <PersistedTabState value={{ read, write }}>
        <Harness isValid={isValid} />
      </PersistedTabState>,
    );
  });
  return write;
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

describe("usePersistedPaneState", () => {
  it("starts from what was saved, and writes nothing for it", () => {
    const write = mount(() => 5);
    expect(latest[0]).toBe(5);
    expect(write).not.toHaveBeenCalled();
  });

  it("writes what the user changes, under its name", () => {
    const write = mount(() => undefined);
    act(() => latest[1](3));
    expect(write).toHaveBeenCalledWith("n", 3);
  });

  it("drops a saved value the component no longer accepts, for the initial one", () => {
    mount(() => "stale", vi.fn(), (saved) => typeof saved === "number");
    expect(latest[0]).toBe(1);
  });

  it("is plain pane state outside a fiche's tab", () => {
    act(() => root.render(<Harness />));
    expect(latest[0]).toBe(1);
    act(() => latest[1](2));
    expect(latest[0]).toBe(2);
  });
});
