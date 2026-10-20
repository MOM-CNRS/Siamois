import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { ListParams, PagedResult } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";
import { WriteModeProvider } from "./writeMode";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

// The list's tree view as the user sees it: the switch in the toolbar, the TreeTable with the same
// columns, the expander, the context rows and the counters.

interface Row {
  id: number;
  name: string;
  hasChildren?: boolean;
  match?: boolean;
}

const listMock = vi.fn<(params: ListParams) => Promise<PagedResult<Row>>>();

registerEntityType({
  key: "tree-panel-entity",
  labels: { singular: "T", plural: "Ts" },
  collectionPath: "tree-panel-entities",
  icon: "bi bi-question",
  api: { list: listMock, get: vi.fn() },
  list: {
    columns: [
      {
        key: "name",
        header: "Name",
        render: (row: Row) => row.name,
        identifier: true,
      },
      { key: "other", header: "Autre", render: () => "x" },
    ],
    searchable: true,
    tree: true,
  },
  detail: { tabs: [] },
  routes: { list: "/t", detail: (id) => `/t/${id}` },
});

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++)
      await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function click(el: Element) {
  await act(async () => {
    el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await flush();
}

function renderPanel(
  props: { scope?: { entityType: string; id: number; path: string } } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={false}>
          <EntityListPanel entityType="tree-panel-entity" {...props} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
}

const switchToTree = () =>
  container.querySelector<HTMLButtonElement>(
    'button[aria-label="Vue arborescente"]',
  );
const bodyRows = () =>
  Array.from(container.querySelectorAll(".p-treetable-tbody > tr"));
const names = () =>
  bodyRows().map(
    (tr) => tr.querySelector(".entity-nav-chip-label")?.textContent,
  );

beforeEach(() => {
  window.localStorage.clear();
  listMock.mockReset();
  listMock.mockImplementation(async (p) => {
    if (p.scope)
      return {
        data: [{ id: 10, name: "Child" }],
        totalCount: 1,
        limit: 200,
        offset: 0,
      };
    if (p.tree) {
      return {
        data: [
          {
            id: 1,
            name: "Parent",
            hasChildren: true,
            match: p.search ? false : undefined,
          },
          { id: 2, name: "Hit", match: p.search ? true : undefined },
        ],
        totalCount: 2,
        matchCount: 37,
        limit: 20,
        offset: 0,
      };
    }
    return {
      data: [{ id: 1, name: "Parent" }],
      totalCount: 99,
      limit: 20,
      offset: 0,
    };
  });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("EntityListPanel: the tree view", () => {
  it("starts as the flat list, with a switch to the tree", async () => {
    renderPanel();
    await flush();
    expect(container.querySelector(".p-datatable")).not.toBeNull();
    expect(container.querySelector(".p-treetable")).toBeNull();
    expect(switchToTree()).not.toBeNull();
  });

  it("offers no tree inside a context (a scoped list)", async () => {
    renderPanel({
      scope: { entityType: "other", id: 5, path: "tree-panel-entities" },
    });
    await flush();
    expect(switchToTree()).toBeNull();
  });

  it("draws the roots in a TreeTable with the list's columns, and an expander only on parents", async () => {
    renderPanel();
    await flush();
    await click(switchToTree()!);

    expect(container.querySelector(".p-treetable")).not.toBeNull();
    expect(container.querySelector(".p-datatable")).toBeNull();
    expect(names()).toEqual(["Parent", "Hit"]);
    expect(
      Array.from(container.querySelectorAll(".p-treetable-thead th")).some(
        (th) => th.textContent?.includes("Autre"),
      ),
    ).toBe(true);
    const togglers = Array.from(
      container.querySelectorAll<HTMLElement>(".p-treetable-toggler"),
    );
    expect(togglers.map((b) => b.style.visibility === "hidden")).toEqual([
      false,
      true,
    ]);
  });

  it("opens a branch on its expander and shows the children right below", async () => {
    renderPanel();
    await flush();
    await click(switchToTree()!);
    await click(container.querySelector(".p-treetable-toggler")!);

    expect(names()).toEqual(["Parent", "Child", "Hit"]);
  });

  it("counts the entities that match in the toolbar, not the roots", async () => {
    renderPanel();
    await flush();
    await click(switchToTree()!);

    expect(
      container.querySelector(".entity-list-panel-toolbar")?.textContent,
    ).toContain("0/37");
  });
});
