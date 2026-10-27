import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {type RelationListModel, useRelationList} from "./useRelationList";

const getRelationList = vi.fn();
vi.mock("./api", () => ({ getRelationList: (...args: unknown[]) => getRelationList(...args) }));

let container: HTMLDivElement;
let root: Root;
let latest: RelationListModel;

function Probe() {
  latest = useRelationList(42);
  return null;
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeEach(async () => {
  getRelationList.mockReset().mockResolvedValue({ data: [{ kind: "parents", unit: { resourceId: "1", resourceType: "recording-units" } }], meta: { total: 61, limit: 25, offset: 0 } });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Probe />
      </QueryClientProvider>,
    );
  });
  await flush();
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("useRelationList", () => {
  it("starts on the first page, by kind, and reads the total from the answer", () => {
    expect(getRelationList).toHaveBeenCalledWith(42, { kinds: [], search: undefined, sort: "kind:asc", offset: 0, limit: 100 });
    expect(latest.total).toBe(61);
    expect(latest.rows).toHaveLength(61);
    expect(latest.rows[0]?.unit.resourceId).toBe("1");
  });

  it("fetches the pages the scroll reaches, and holds the whole result set", async () => {
    getRelationList.mockImplementation((_id: number, p: { offset: number }) =>
      Promise.resolve({ data: [{ kind: "anterior", unit: { resourceId: `at-${p.offset}`, resourceType: "recording-units" } }], meta: { total: 250, limit: 100, offset: p.offset } }),
    );
    await act(async () => latest.setScrollWindow(180, 230));
    await flush();

    expect(getRelationList).toHaveBeenCalledWith(42, expect.objectContaining({ offset: 100, limit: 100 }));
    expect(getRelationList).toHaveBeenCalledWith(42, expect.objectContaining({ offset: 200, limit: 100 }));
    expect(latest.total).toBe(250);
    expect(latest.rows).toHaveLength(250);
    expect(latest.rows[100]?.unit.resourceId).toBe("at-100");
    expect(latest.rows[200]?.unit.resourceId).toBe("at-200");
    expect(latest.rows[150]).toBeNull();
  });

  it("asks again for the kinds the filter keeps, from the first page", async () => {
    await act(async () => latest.setScrollWindow(150, 200));
    await flush();
    expect(getRelationList).toHaveBeenLastCalledWith(42, expect.objectContaining({ offset: 100 }));

    await act(async () => latest.setFilters({ kind: { op: "in", v: ["anterior", "synchronous"] } }));
    await flush();

    expect(getRelationList).toHaveBeenLastCalledWith(42, expect.objectContaining({ kinds: ["anterior", "synchronous"], offset: 0 }));
  });

  it("searches and sorts from the first page too, and falls back on the kind order", async () => {
    await act(async () => latest.setScrollWindow(150, 200));
    await act(async () => latest.setSearch("us 4"));
    await flush();
    expect(getRelationList).toHaveBeenLastCalledWith(42, expect.objectContaining({ search: "us 4", offset: 0 }));

    await act(async () => latest.setSort("label:desc"));
    await flush();
    expect(getRelationList).toHaveBeenLastCalledWith(42, expect.objectContaining({ sort: "label:desc" }));

    await act(async () => latest.setSort(undefined));
    await flush();
    expect(getRelationList).toHaveBeenLastCalledWith(42, expect.objectContaining({ sort: "kind:asc" }));
  });
});
