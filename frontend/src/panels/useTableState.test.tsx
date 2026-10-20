import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { useTableState } from "./useTableState";
import { PaneStateProvider, PaneStateStore } from "./paneState";

// No @testing-library/react in this project (see EntityListPanel.test.tsx's own createRoot/act
// pattern) — a tiny harness component exposes the hook's latest return value for assertions.
let container: HTMLDivElement;
let root: Root;
let latest: ReturnType<typeof useTableState>;

type UserStateProp = NonNullable<Parameters<typeof useTableState>[0]["userState"]>;

function Harness({ defaultSort, userState }: { defaultSort?: string; userState?: UserStateProp }) {
  latest = useTableState({ defaultSort, userState });
  return null;
}

function renderHarness(defaultSort?: string, userState?: UserStateProp) {
  act(() => {
    root.render(<Harness defaultSort={defaultSort} userState={userState} />);
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
      v: 3,
      offset: 0,
      limit: 25,
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

  it("goes back to the first page when the result set changes, not when columns do", () => {
    renderHarness();
    act(() => latest.setPage(50, 25));
    expect(latest.state).toMatchObject({ offset: 50, limit: 25 });

    act(() => latest.setVisibleColumns(["a"]));
    expect(latest.state.offset).toBe(50);

    act(() => latest.setSearch("abc"));
    expect(latest.state).toMatchObject({ offset: 0, limit: 25 });

    act(() => latest.setPage(50, 25));
    act(() => latest.setSort("name:desc"));
    expect(latest.state.offset).toBe(0);

    act(() => latest.setPage(50, 25));
    act(() => latest.setFilters({}));
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

  it("restores the table state and the seeded columns when a list mounts again in the same store", () => {
    const store = new PaneStateStore();
    const renderWithStore = () =>
      act(() => {
        root.render(
          <PaneStateProvider store={store}>
            <Harness />
          </PaneStateProvider>,
        );
      });

    renderWithStore();
    act(() => latest.seedVisibleColumns(["a", "b"]));
    act(() => latest.setFilters({ name: { op: "contains", v: "fos" } }));
    act(() => latest.setSort("name:desc"));

    act(() => root.render(null));
    renderWithStore();

    expect(latest.columnsSeeded).toBe(true);
    expect(latest.state).toMatchObject({
      visibleColumns: ["a", "b"],
      filters: { name: { op: "contains", v: "fos" } },
      sort: "name:desc",
    });
    act(() => latest.seedVisibleColumns(["z"]));
    expect(latest.state.visibleColumns).toEqual(["a", "b"]);
  });

  describe("with the user's saved state", () => {
    const saved = {
      v: 1,
      limit: 100,
      sort: "name:desc",
      filters: { k: { op: "in", v: ["1"] } },
      viewMode: "tree",
      visibleColumns: ["b", "gone", "a"],
    };

    it("holds the list back until the saved state has been read", () => {
      renderHarness("id:asc", { settled: false, saved: undefined });
      expect(latest.userStateReady).toBe(false);
      act(() => latest.seedVisibleColumns(["a"]));
      expect(latest.columnsSeeded).toBe(false);
    });

    it("applies what was saved, over the defaults, once it is read", () => {
      renderHarness("id:asc", { settled: false, saved: undefined });
      renderHarness("id:asc", { settled: true, saved });
      expect(latest.userStateReady).toBe(true);
      expect(latest.state).toMatchObject({
        limit: 100,
        sort: "name:desc",
        filters: { k: { op: "in", v: ["1"] } },
        viewMode: "tree",
        offset: 0,
      });
    });

    it("seeds the saved columns, minus those the catalog lost, before the catalog's defaults", () => {
      renderHarness("id:asc", { settled: true, saved });
      act(() => latest.seedVisibleColumns(["x", "y"], ["a", "b", "x", "y"]));
      expect(latest.state.visibleColumns).toEqual(["b", "a"]);
    });

    it("keeps the defaults when nothing was saved", () => {
      renderHarness("id:asc", { settled: true, saved: undefined });
      expect(latest.userStateReady).toBe(true);
      expect(latest.state).toMatchObject({ sort: "id:asc", limit: 25 });
      act(() => latest.seedVisibleColumns(["x"]));
      expect(latest.state.visibleColumns).toEqual(["x"]);
    });

    it("applies it once: a later change of the saved state does not override the user's own", () => {
      renderHarness("id:asc", { settled: true, saved });
      act(() => latest.setSort("other:asc"));
      renderHarness("id:asc", { settled: true, saved: { ...saved, sort: "late:asc" } });
      expect(latest.state.sort).toBe("other:asc");
    });

    it("setViewMode changes only the mode", () => {
      renderHarness();
      act(() => latest.setViewMode("tree"));
      expect(latest.state.viewMode).toBe("tree");
      expect(latest.state.limit).toBe(25);
    });
  });
});
