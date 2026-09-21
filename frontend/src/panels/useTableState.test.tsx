import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { DEFAULT_LIMIT } from "./tableState";
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
  it("initializes with the given default sort and DEFAULT_LIMIT", () => {
    renderHarness("name:asc");
    expect(latest.state).toEqual({
      v: 1,
      offset: 0,
      limit: DEFAULT_LIMIT,
      sort: "name:asc",
      visibleColumns: [],
      filters: {},
    });
  });

  it("setPage updates offset and limit together", () => {
    renderHarness();
    act(() => latest.setPage(20, 25));
    expect(latest.state.offset).toBe(20);
    expect(latest.state.limit).toBe(25);
  });

  it("setSearch resets offset to 0 and normalizes an empty string to undefined", () => {
    renderHarness();
    act(() => latest.setPage(20, 10));
    act(() => latest.setSearch("abc"));
    expect(latest.state.search).toBe("abc");
    expect(latest.state.offset).toBe(0);

    act(() => latest.setPage(20, 10));
    act(() => latest.setSearch(""));
    expect(latest.state.search).toBeUndefined();
  });

  it("setFilters resets offset to 0", () => {
    renderHarness();
    act(() => latest.setPage(20, 10));
    act(() => latest.setFilters({ name: { op: "contains", v: "fos" } }));
    expect(latest.state.filters).toEqual({ name: { op: "contains", v: "fos" } });
    expect(latest.state.offset).toBe(0);
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
