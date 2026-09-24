import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { useTableState } from "./useTableState";

// No @testing-library/react in this project (see EntityListPanel.test.tsx's own createRoot/act
// pattern) — a tiny harness component exposes the hook's latest return value for assertions.
let container: HTMLDivElement;
let root: Root;
let latest: ReturnType<typeof useTableState>;

function Harness({ defaultSort }: { defaultSort?: string }) {
  latest = useTableState({ defaultSort });
  return null;
}

function renderHarness(defaultSort?: string) {
  act(() => {
    root.render(<Harness defaultSort={defaultSort} />);
  });
}

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
});

describe("useTableState", () => {
  it("initializes with the given default sort", () => {
    renderHarness("name:asc");
    expect(latest.state).toEqual({
      v: 2,
      sort: "name:asc",
      visibleColumns: [],
      filters: {},
    });
  });

  it("setSearch normalizes an empty string to undefined", () => {
    renderHarness();
    act(() => latest.setSearch("abc"));
    expect(latest.state.search).toBe("abc");

    act(() => latest.setSearch(""));
    expect(latest.state.search).toBeUndefined();
  });

  it("setFilters replaces the filters", () => {
    renderHarness();
    act(() => latest.setFilters({ name: { op: "contains", v: "fos" } }));
    expect(latest.state.filters).toEqual({ name: { op: "contains", v: "fos" } });
  });

  it("seedVisibleColumns sets visibleColumns only the first time it's called", () => {
    renderHarness();
    act(() => latest.seedVisibleColumns(["-118", "-109"]));
    expect(latest.state.visibleColumns).toEqual(["-118", "-109"]);

    act(() => latest.seedVisibleColumns(["-999"]));
    expect(latest.state.visibleColumns).toEqual(["-118", "-109"]);
  });

  it("seedVisibleColumns never overwrites an explicit toggle, even back to the same catalog defaults", () => {
    renderHarness();
    act(() => latest.seedVisibleColumns(["-118"]));
    act(() => latest.setVisibleColumns([]));
    act(() => latest.seedVisibleColumns(["-118"]));
    expect(latest.state.visibleColumns).toEqual([]);
  });
});
