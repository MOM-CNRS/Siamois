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
    treeScopes: ["project"],
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
  props: { scope?: { entityType: string; id: number; path?: string } } = {},
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

const gear = () =>
  container.querySelector<HTMLButtonElement>(".entity-list-panel-gear-button");
// The display modes live in the gear menu, as one entry that opens a grid of tiles.
const modeEntry = () =>
  Array.from(document.body.querySelectorAll(".entity-list-panel-gear-menu .p-menuitem-link")).find((e) =>
    e.textContent?.includes("Mode d'affichage"),
  );
const viewEntries = () => Array.from(document.body.querySelectorAll(".entity-list-panel-view-tile"));
async function openModes() {
  await click(gear()!);
  await click(modeEntry()!);
}
async function pickView(label: string) {
  await openModes();
  await click(viewEntries().find((e) => e.textContent?.includes(label))!);
}
async function openGear() {
  await click(gear()!);
}
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
    if (p.parent != null || p.scope?.path === "children")
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
  it("starts as the flat list, and offers the tree among the display modes of the gear menu", async () => {
    renderPanel();
    await flush();
    expect(container.querySelector(".p-datatable")).not.toBeNull();
    expect(container.querySelector(".p-treetable")).toBeNull();

    await openGear();
    // One entry names the mode in use...
    expect(modeEntry()?.textContent).toContain("Liste");
    // ...and opens a grid of tiles, the one in use checked.
    await click(modeEntry()!);
    expect(viewEntries().map((e) => e.textContent)).toEqual(["Liste", "Arbre"]);
    expect(viewEntries().map((e) => e.getAttribute("aria-checked"))).toEqual(["true", "false"]);
  });

  it("offers no display mode but the table inside a context the type doesn't support", async () => {
    renderPanel({
      scope: { entityType: "other", id: 5, path: "tree-panel-entities" },
    });
    await flush();
    // Nothing to configure in this list: no gear at all.
    expect(gear()).toBeNull();
  });

  it("offers the tree inside a context the type supports, and keeps the children in that context", async () => {
    renderPanel({
      scope: { entityType: "project", id: 5 },
    });
    await flush();
    await pickView("Arbre");
    await click(container.querySelector(".p-treetable-toggler")!);

    expect(names()).toEqual(["Parent", "Child", "Hit"]);
    const childCall = listMock.mock.calls
      .map(([p]) => p)
      .find((p) => p.parent != null)!;
    expect(childCall).toMatchObject({
      parent: 1,
      tree: true,
      scope: { entityType: "project", id: 5 },
    });
  });

  it("draws the roots in a TreeTable with the list's columns, and an expander only on parents", async () => {
    renderPanel();
    await flush();
    await pickView("Arbre");

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

  it("goes back to the flat list from the same menu", async () => {
    renderPanel();
    await flush();
    await pickView("Arbre");
    await pickView("Liste");

    expect(container.querySelector(".p-datatable")).not.toBeNull();
    expect(container.querySelector(".p-treetable")).toBeNull();
  });

  it("opens a branch on its expander and shows the children right below", async () => {
    renderPanel();
    await flush();
    await pickView("Arbre");
    await click(container.querySelector(".p-treetable-toggler")!);

    expect(names()).toEqual(["Parent", "Child", "Hit"]);
  });

  it("counts the entities that match in the toolbar, not the roots", async () => {
    renderPanel();
    await flush();
    await pickView("Arbre");

    expect(
      container.querySelector(".entity-list-panel-toolbar")?.textContent,
    ).toContain("0/37");
  });
});
