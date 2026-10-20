import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import { registerDefaultFieldRenderers } from "../fields/registerDefaultRenderers";
import type { EntityTypeConfig, PagedResult } from "../entities/types";
import { deleteViewState, getViewState, putViewState } from "../api/views";
import { ApiError } from "../api/client";
import { EntityListPanel } from "./EntityListPanel";
import { SAVE_DELAY_MS } from "./useUserViewState";
import { FicheTabsProvider } from "./ficheTabsContext";

registerDefaultFieldRenderers();

// What a user changed on a list is kept server-side per organization (GET/PUT /api/v1/view-states):
// applied before the first fetch, written when it changes, not otherwise.

interface FakeRow {
  id: string;
  name: string;
}

const listMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();

const config: EntityTypeConfig<FakeRow, FakeRow> = {
  key: "user-state-entity",
  labels: { singular: "Fake", plural: "Fakes" },
  collectionPath: "user-state-entities",
  icon: "bi bi-question",
  api: { list: listMock, get: vi.fn() },
  list: {
    columns: [
      { key: "name", header: "Name", render: (row) => row.name, sortable: true, filterable: true, identifier: true },
    ],
    defaultSort: "name:asc",
    searchable: true,
  },
  detail: { tabs: [] },
  routes: { list: "/fake", detail: (id) => `/fake/${id}` },
};

registerEntityType(config);

let container: HTMLDivElement;
let root: Root;

async function flush() {
  for (let round = 0; round < 2; round++) {
    await act(async () => {
      for (let i = 0; i < 5; i++) {
        await new Promise((resolve) => setTimeout(resolve, 0));
      }
    });
  }
}

async function renderList(
  props: {
    scope?: { entityType: string; id: number };
    fixedFilters?: Record<string, { op: "contains"; v: string } | { op: "in"; v: string[] }>;
    stateKey?: string;
    embedded?: boolean;
    volatile?: boolean;
    initialFilters?: Record<string, { op: "contains"; v: string }>;
    onFiltersChange?: (filters: Record<string, unknown>) => void;
    saveAsTab?: (target: string, filters: Record<string, unknown>) => void;
  } = {},
) {
  const { saveAsTab, ...listProps } = props;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <FicheTabsProvider value={saveAsTab ? { saveAsTab } : undefined}>
          <EntityListPanel entityType="user-state-entity" organizationId={7} {...listProps} />
        </FicheTabsProvider>
      </QueryClientProvider>,
    );
  });
  await flush();
}

function nameHeader(): HTMLElement {
  return Array.from(container.querySelectorAll<HTMLElement>("th")).find((th) => th.textContent?.includes("Name"))!;
}

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  listMock.mockReset();
  listMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 25, offset: 0 });
  vi.mocked(getViewState).mockClear();
  vi.mocked(putViewState).mockClear();
  vi.mocked(deleteViewState).mockClear();
  vi.mocked(getViewState).mockImplementation((_org, viewKey) => Promise.resolve({ viewKey, state: null }));
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("EntityListPanel's state kept per user", () => {
  it("asks for the state of the organization-wide list, and of a relation tab apart, per parent fiche", async () => {
    await renderList();
    expect(getViewState).toHaveBeenCalledWith(7, "ctx:user-state-entity:global");

    act(() => root.render(null));
    await renderList({ scope: { entityType: "project", id: 5 } });
    expect(getViewState).toHaveBeenCalledWith(7, "ctx:user-state-entity:project:5-tab");
  });

  it("fetches its first page with the saved sort, page size and filters, never with the defaults first", async () => {
    vi.mocked(getViewState).mockResolvedValue({
      viewKey: "ctx:user-state-entity:global",
      state: { v: 1, limit: 50, sort: "name:desc", filters: { name: { op: "contains", v: "fos" } } },
    });

    await renderList();

    // The one-row request is the titlebar's unfiltered total, which a filter triggers.
    const pages = listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit !== 1);
    expect(pages).toEqual([
      expect.objectContaining({ offset: 0, limit: 50, sort: "name:desc", filters: { name: { op: "contains", v: "fos" } } }),
    ]);
  });

  it("is a configured tab of a fiche: its own state, its base filters always applied over the user's", async () => {
    vi.mocked(getViewState).mockResolvedValue({
      viewKey: "ctx:user-state-entity:project:5-tab:custom:1",
      state: { v: 1, filters: { name: { op: "contains", v: "mine" }, other: { op: "contains", v: "x" } } },
    });

    await renderList({
      scope: { entityType: "project", id: 5 },
      stateKey: "custom:1",
      fixedFilters: { name: { op: "in", v: ["a"] }, gone: { op: "contains", v: "unknown field" } },
    });

    expect(getViewState).toHaveBeenCalledWith(7, "ctx:user-state-entity:project:5-tab:custom:1");
    const pages = listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit !== 1);
    // The tab's filter wins on `name`; `other` is unknown to the list and `gone` too: neither reaches the server.
    expect(pages).toEqual([expect.objectContaining({ filters: { name: { op: "in", v: ["a"] } } })]);
    // What the tab defines is not the user's: nothing is saved.
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, SAVE_DELAY_MS + 100));
    });
    expect(putViewState).not.toHaveBeenCalled();
  });

  it("offers to keep the list it shows as a tab of the fiche, with its filters, when the fiche may be configured", async () => {
    vi.mocked(getViewState).mockResolvedValue({
      viewKey: "ctx:user-state-entity:project:5-tab",
      state: { v: 1, filters: { name: { op: "contains", v: "fos" } } },
    });
    const saveAsTab = vi.fn();

    await renderList({ embedded: true, scope: { entityType: "project", id: 5 }, saveAsTab });
    const button = container.querySelector<HTMLButtonElement>('button[aria-label="Enregistrer comme onglet…"]')!;
    await act(async () => button.click());

    expect(saveAsTab).toHaveBeenCalledWith("user-state-entity", { name: { op: "contains", v: "fos" } });
  });

  it("offers nothing when the fiche cannot be configured, or the list has no filter", async () => {
    await renderList({ embedded: true, scope: { entityType: "project", id: 5 } });
    expect(container.querySelector('button[aria-label="Enregistrer comme onglet…"]')).toBeNull();

    act(() => root.render(null));
    await renderList({ embedded: true, scope: { entityType: "project", id: 5 }, saveAsTab: vi.fn() });
    expect(container.querySelector('button[aria-label="Enregistrer comme onglet…"]')).toBeNull();
  });

  it("is a preview being configured: nothing read from or kept for the user, it opens on its initial filters and reports changes", async () => {
    const heard: unknown[] = [];

    await renderList({
      embedded: true,
      volatile: true,
      scope: { entityType: "project", id: 5 },
      initialFilters: { name: { op: "contains", v: "abc" } },
      onFiltersChange: (filters) => heard.push(filters),
    });

    expect(getViewState).not.toHaveBeenCalled();
    const pages = listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit !== 1);
    expect(pages).toEqual([expect.objectContaining({ filters: { name: { op: "contains", v: "abc" } } })]);
    expect(heard[heard.length - 1]).toEqual({ name: { op: "contains", v: "abc" } });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, SAVE_DELAY_MS + 100));
    });
    expect(putViewState).not.toHaveBeenCalled();
  });

  it("keeps the defaults, and saves nothing, when the user changed nothing", async () => {
    await renderList();
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, SAVE_DELAY_MS + 100));
    });

    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ sort: "name:asc", limit: 25 }));
    expect(putViewState).not.toHaveBeenCalled();
  });

  it("saves the sort the user picks, once the changes stop", async () => {
    await renderList();

    await act(async () => {
      nameHeader().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, SAVE_DELAY_MS + 100));
    });

    expect(putViewState).toHaveBeenCalledTimes(1);
    expect(putViewState).toHaveBeenCalledWith(
      7,
      "ctx:user-state-entity:global",
      expect.objectContaining({ v: 1, sort: expect.stringMatching(/^name:/), limit: 25, filters: {} }),
    );
  });

  it("still lists when the saved state can't be read", async () => {
    vi.mocked(getViewState).mockRejectedValue(new Error("down"));

    await renderList();

    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ sort: "name:asc" }));
    expect(container.textContent).toContain("Row A");
  });

  describe("when the definitions changed since the state was kept", () => {
    const stale = {
      v: 1,
      limit: 50,
      sort: "77:desc",
      filters: { name: { op: "contains", v: "keep" }, "99": { op: "contains", v: "gone" } },
    };

    function pages(): Record<string, unknown>[] {
      return listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit !== 1);
    }

    it("drops a filter and a sort on fields that are gone before the first request, keeps the rest", async () => {
      vi.mocked(getViewState).mockResolvedValue({ viewKey: "ctx:user-state-entity:global", state: stale });

      await renderList();

      expect(pages()).toEqual([
        expect.objectContaining({ limit: 50, sort: "name:asc", filters: { name: { op: "contains", v: "keep" } } }),
      ]);
    });

    it("ignores a page size the list no longer offers", async () => {
      vi.mocked(getViewState).mockResolvedValue({
        viewKey: "ctx:user-state-entity:global",
        state: { v: 1, limit: 500, sort: "name:desc" },
      });

      await renderList();

      expect(pages()).toEqual([expect.objectContaining({ limit: 25, sort: "name:desc" })]);
    });

    it("gives up what the user kept, once, when the server still refuses it, and lists with the defaults", async () => {
      vi.mocked(getViewState).mockResolvedValue({
        viewKey: "ctx:user-state-entity:global",
        state: { v: 1, limit: 50, filters: { name: { op: "contains", v: "refused" } } },
      });
      listMock.mockImplementation((params) => {
        const filters = (params as { filters?: unknown }).filters;
        return filters
          ? Promise.reject(new ApiError(400, "Champ inconnu", undefined, "/api/v1/x"))
          : Promise.resolve({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 25, offset: 0 });
      });

      await renderList();
      await flush();

      expect(deleteViewState).toHaveBeenCalledWith(7, "ctx:user-state-entity:global");
      expect(lastPage()).toEqual(expect.objectContaining({ limit: 25, sort: "name:asc" }));
      expect(lastPage()?.filters).toBeUndefined();
      expect(container.textContent).toContain("Row A");
    });
  });

  function lastPage(): Record<string, unknown> | undefined {
    const calls = listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit !== 1);
    return calls[calls.length - 1];
  }
});
