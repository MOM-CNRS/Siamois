import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig, PagedResult } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";

// Some PrimeReact internals (ripple, resize listeners) schedule state updates outside any act()
// call this file makes; this flag is React 18's own escape hatch for that noise and doesn't
// affect what the assertions below actually verify.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Exercises EntityListPanel as the generic component it is (plan §3/§8 phase 5) — registered
// against a throwaway fake entity type, not Project, so this stays a test of the mechanism
// (params built from search/sort/pagination state, row click → onNavigate) rather than of
// entities/project/config.tsx, which already has its own coverage (api.test.ts, columns.test.tsx).

interface FakeRow {
  id: string;
  name: string;
}

const listMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();

const fakeConfig: EntityTypeConfig<FakeRow, FakeRow> = {
  key: "fake-entity",
  labels: { singular: "Fake", plural: "Fakes" },
  icon: "bi bi-question",
  api: {
    list: listMock,
    get: vi.fn(),
  },
  list: {
    columns: [{ key: "name", header: "Name", render: (row) => row.name, sortable: true }],
    defaultSort: "name:asc",
    searchable: true,
  },
  detail: { tabs: [] },
  routes: { list: "/fake", detail: (id) => `/fake/${id}` },
};

registerEntityType(fakeConfig);

let container: HTMLDivElement;
let root: Root;

function renderPanel(onNavigate?: (entityType: string, id: string | number) => void, onCreate?: () => void) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <EntityListPanel entityType="fake-entity" onNavigate={onNavigate} onCreate={onCreate} />
      </QueryClientProvider>,
    );
  });
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

beforeEach(() => {
  listMock.mockReset();
  listMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 20, offset: 0 });
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

describe("EntityListPanel", () => {
  it("calls config.api.list with the default sort and pagination on first render", async () => {
    renderPanel();
    await flush();

    expect(listMock).toHaveBeenCalledWith(
      expect.objectContaining({ offset: 0, limit: 20, sort: "name:asc", search: undefined }),
    );
    expect(container.textContent).toContain("Row A");
  });

  it("resets the offset and passes the typed value when searching", async () => {
    renderPanel();
    await flush();

    const input = container.querySelector("input") as HTMLInputElement;
    expect(input).toBeTruthy();

    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "abc");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await flush();

    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ offset: 0, search: "abc" }));
  });

  it("calls onNavigate with the row's entity type and id on row click", async () => {
    const onNavigate = vi.fn();
    renderPanel(onNavigate);
    await flush();

    const row = container.querySelector("tbody tr") as HTMLElement;
    expect(row).toBeTruthy();

    await act(async () => {
      row.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith("fake-entity", "1");
  });

  it("renders the header icon, plural label and a count chip (actionUnitListPanelHeader.xhtml)", async () => {
    renderPanel();
    await flush();

    const header = container.querySelector(".entity-list-panel-header")!;
    expect(header.querySelector(".bi-question")).toBeTruthy();
    expect(header.textContent).toContain("Fakes");
    expect(header.textContent).toContain("1");
  });

  it("only renders the create button when onCreate is provided (list's own toolbar create button)", async () => {
    renderPanel(undefined, undefined);
    await flush();
    expect(Array.from(container.querySelectorAll("button")).some((b) => b.textContent === "Créer")).toBe(false);

    const onCreate = vi.fn();
    renderPanel(undefined, onCreate);
    await flush();
    const createButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer")!;
    expect(createButton).toBeTruthy();

    await act(async () => {
      createButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it("renders its toolbar inside its own header, not as a separate strip (plan §7/§8 follow-up)", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityListPanel
            entityType="fake-entity"
            toolbar={{ chrome: { resourceUri: "/fake", title: "Fakes", bookmarked: false }, actions: { refresh: () => {} } }}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    const header = container.querySelector(".p-panel-header")!;
    expect(header).toBeTruthy();
    expect(header.querySelector(".bi-arrow-clockwise")).toBeTruthy();
  });

  it("shows an unsupported message for an unregistered entity type", async () => {
    renderPanel.bind(null);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityListPanel entityType="does-not-exist" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.textContent).toContain("does-not-exist");
  });
});
