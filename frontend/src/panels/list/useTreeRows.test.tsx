import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import type {EntityTypeConfig, ListParams, PagedResult,} from "../../entities/shared/types";
import {type TreeRow, useTreeRows} from "./useTreeRows";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

// The tree view's data: roots page by page, children fetched when a branch opens, the keys that let one
// entity sit under several parents, and what happens when a request fails or a branch is cut.

type Params = Omit<ListParams, "offset" | "limit" | "fields">;

const listMock = vi.fn<(params: ListParams) => Promise<PagedResult<TreeRow>>>();

const baseConfig = {
  key: "tree-entity",
  collectionPath: "tree-entities",
  api: { list: listMock },
} as unknown as EntityTypeConfig<unknown, unknown>;
let config = baseConfig;

const page = (
  data: TreeRow[],
  totalCount = data.length,
): PagedResult<TreeRow> => ({ data, totalCount, limit: 20, offset: 0 });
const row = (id: number, extra: Partial<TreeRow> = {}): TreeRow => ({
  id,
  ...extra,
});

type Tree = ReturnType<typeof useTreeRows>;
let latest: Tree;
let container: HTMLDivElement;
let root: Root;
let params: Params = {};

function Probe() {
  latest = useTreeRows({
    entityType: "tree-entity",
    config,
    organizationId: 1,
    params,
    fields: [],
    offset: 0,
    limit: 20,
    enabled: true,
  });
  return null;
}

function mount() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const ui = (
    <QueryClientProvider client={queryClient}>
      <Probe />
    </QueryClientProvider>
  );
  act(() => root.render(ui));
  return () => act(() => root.render(ui));
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++)
      await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

const treeCalls = () =>
  listMock.mock.calls.map(([p]) => p).filter((p) => p.tree);
const childCalls = () => treeCalls().filter((p) => p.scope != null);

async function open(path: string) {
  const node = latest.tree.nodes
    .flatMap(function all(n): typeof latest.tree.nodes {
      return [
        n,
        ...((n.children as typeof latest.tree.nodes | undefined) ?? []).flatMap(
          all,
        ),
      ];
    })
    .find((n) => n.key === path)!;
  await act(async () => {
    latest.tree.onToggle({ ...latest.tree.expandedKeys, [path]: true });
    latest.tree.onExpand(node);
  });
  await flush();
}

beforeEach(() => {
  config = baseConfig;
  params = {};
  listMock.mockReset();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("useTreeRows", () => {
  it("asks for the roots with tree=true and flags the leaves", async () => {
    listMock.mockResolvedValue(
      page([row(1, { hasChildren: true }), row(2, { hasChildren: false })]),
    );
    mount();
    await flush();

    expect(treeCalls()[0]).toMatchObject({ tree: true, offset: 0, limit: 20 });
    expect(latest.tree.nodes.map((n) => [n.key, n.leaf])).toEqual([
      ["1", false],
      ["2", true],
    ]);
    expect(latest.rows.map((r) => r.id)).toEqual([1, 2]);
  });

  it("opens nothing by itself, even while a search narrows the list", async () => {
    params = { search: "abc" };
    listMock.mockResolvedValue(
      page([row(1, { hasChildren: true, match: false })]),
    );
    mount();
    await flush();

    expect(childCalls()).toHaveLength(0);
    expect(latest.rows).toHaveLength(1);
    expect(latest.tree.expandedKeys).toEqual({});
  });

  it("fetches a branch's children from the children route when it opens, under their parent's path", async () => {
    listMock.mockImplementation(async (p) =>
      p.scope
        ? page([row(10), row(11)])
        : page([row(1, { hasChildren: true })]),
    );
    mount();
    await flush();
    await open("1");

    expect(childCalls()).toEqual([
      expect.objectContaining({
        scope: { entityType: "tree-entity", id: 1, path: "children" },
        tree: true,
      }),
    ]);
    expect(latest.tree.nodes[0].children?.map((n) => n.key)).toEqual([
      "1/10",
      "1/11",
    ]);
    expect(latest.rows.map((r) => r.id)).toEqual([1, 10, 11]);
  });

  it("fetches the children from the same scoped list with `parent` inside a scope", async () => {
    params = { scope: { entityType: "project", id: 5 } };
    listMock.mockImplementation(async (p) =>
      p.parent != null
        ? page([row(10)])
        : page([row(1, { hasChildren: true })]),
    );
    mount();
    await flush();
    await open("1");

    expect(treeCalls().some((p) => p.scope?.path === "children")).toBe(false);
    expect(treeCalls()[1]).toMatchObject({
      tree: true,
      parent: 1,
      scope: { entityType: "project", id: 5 },
    });
    expect(latest.rows.map((r) => r.id)).toEqual([1, 10]);
  });

  it("lists the roots through the children route and asks for the match count, when the list is everything below a parent", async () => {
    params = { scope: { entityType: "tree-entity", id: 5, path: "children" } };
    listMock.mockResolvedValue({
      ...page([row(1, { hasChildren: true })]),
      matchCount: 12,
    });
    mount();
    await flush();

    expect(treeCalls()[0]).toMatchObject({
      tree: true,
      matchCount: true,
      scope: { entityType: "tree-entity", id: 5, path: "children" },
    });
    expect(latest.matchCount).toBe(12);
  });

  it("opens a branch of that list through the row's own children route, not with `parent`", async () => {
    params = { scope: { entityType: "tree-entity", id: 5, path: "children" } };
    listMock.mockImplementation(async (p) =>
      p.scope?.id === 1
        ? page([row(10)])
        : page([row(1, { hasChildren: true })]),
    );
    mount();
    await flush();
    await open("1");

    const call = treeCalls().find((p) => p.scope?.id === 1)!;
    expect(call).toMatchObject({
      tree: true,
      scope: { entityType: "tree-entity", id: 1, path: "children" },
    });
    expect(call.parent).toBeUndefined();
    expect(call.matchCount).toBeUndefined();
    expect(latest.rows.map((r) => r.id)).toEqual([1, 10]);
  });

  it("asks a type with no children route for the children of a row from the list itself, with `parent`", async () => {
    config = {
      ...baseConfig,
      list: { treeChildren: "parent" },
    } as unknown as EntityTypeConfig<unknown, unknown>;
    listMock.mockImplementation(async (p) =>
      p.parent != null
        ? page([row(10)])
        : page([row(1, { hasChildren: true })]),
    );
    mount();
    await flush();
    await open("1");

    expect(treeCalls().some((p) => p.scope?.path === "children")).toBe(false);
    expect(treeCalls()[1]).toMatchObject({ tree: true, parent: 1 });
    expect(latest.rows.map((r) => r.id)).toEqual([1, 10]);
  });

  it("shows an entity under two parents once per branch, each with its own key", async () => {
    listMock.mockImplementation(async (p) => {
      if (!p.scope)
        return page([
          row(1, { hasChildren: true }),
          row(2, { hasChildren: true }),
        ]);
      return page([row(99)]);
    });
    mount();
    await flush();
    await open("1");
    await open("2");

    expect(latest.rows.map((r) => r.id)).toEqual([1, 99, 2, 99]);
    expect(latest.rows.map((r) => r.__treeKey)).toEqual([
      "1",
      "1/99",
      "2",
      "2/99",
    ]);
  });

  it("marks as context the parents that only lead to a match, only while narrowed", async () => {
    params = { search: "x" };
    listMock.mockResolvedValue(
      page([
        row(1, { hasChildren: true, match: false }),
        row(2, { match: true }),
      ]),
    );
    mount();
    await flush();
    expect(latest.tree.isContext(row(1, { match: false }))).toBe(true);
    expect(latest.tree.isContext(row(2, { match: true }))).toBe(false);

    params = {};
    const rerender = mount();
    await flush();
    rerender();
    expect(latest.tree.isContext(row(1, { match: false }))).toBe(false);
  });

  it("counts the matches from the roots page's matchCount, not the roots", async () => {
    listMock.mockResolvedValue({ ...page([row(1)], 1), matchCount: 37 });
    mount();
    await flush();

    expect(latest.totalCount).toBe(1);
    expect(latest.matchCount).toBe(37);
    // One request for the roots: no second list call to count.
    expect(listMock).toHaveBeenCalledTimes(1);
  });

  it("closes the branch and reports the error when its children can't be fetched", async () => {
    listMock.mockImplementation(async (p) => {
      if (p.scope) throw new Error("boom");
      return page([row(1, { hasChildren: true })]);
    });
    mount();
    await flush();
    await open("1");

    expect(latest.error).toEqual(expect.objectContaining({ message: "boom" }));
    expect(latest.tree.expandedKeys["1"]).toBe(false);
    expect(latest.rows.map((r) => r.id)).toEqual([1]);
  });

  it("says so when a branch holds more children than were fetched", async () => {
    listMock.mockImplementation(async (p) =>
      p.scope ? page([row(10)], 500) : page([row(1, { hasChildren: true })]),
    );
    mount();
    await flush();
    expect(latest.notice).toBeNull();
    await open("1");

    expect(latest.notice).toContain("200");
  });

  it("keeps the branches that were open when the roots are fetched again", async () => {
    listMock.mockImplementation(async (p) =>
      p.scope ? page([row(10)]) : page([row(1, { hasChildren: true })]),
    );
    mount();
    await flush();
    await open("1");
    expect(childCalls()).toHaveLength(1);

    params = { search: "10" };
    const rerender = mount();
    await flush();
    rerender();
    await flush();

    expect(childCalls().length).toBeGreaterThan(1);
    expect(latest.tree.expandedKeys["1"]).toBe(true);
    expect(latest.rows.map((r) => r.id)).toEqual([1, 10]);
  });
});
