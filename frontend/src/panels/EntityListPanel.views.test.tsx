import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {registerEntityType} from "../entities/registry";
import {registerDefaultFieldRenderers} from "../fields/registerDefaultRenderers";
import type {EntityTypeConfig, PagedResult} from "../entities/shared/types";
import {
    createView,
    deleteViewState,
    getView,
    getViewState,
    updateView,
    type ViewResource,
} from "../api/resources/views";
import {getBookmarkStatus} from "../api/resources/bookmarks";
import {ApiError} from "../api/client";
import {EntityListPanel} from "./EntityListPanel";
import {BridgeProvider} from "./pane/bridge";
import {SCROLL_CHUNK_SIZE} from "./list/tableState";

registerDefaultFieldRenderers();

vi.mock("../api/resources/bookmarks", () => ({
  createBookmark: vi.fn().mockResolvedValue(undefined),
  deleteBookmark: vi.fn().mockResolvedValue(undefined),
  getBookmarkStatus: vi.fn().mockResolvedValue(false),
}));

// The saved views of a list: one on screen, opened by its address, over which the user's own changes come;
// and the header button to update it, save it as a new view or reset it.

interface FakeRow {
  id: string;
  name: string;
}

const listMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();

const config: EntityTypeConfig<FakeRow, FakeRow> = {
  key: "views-entity",
  labels: { singular: "Fake", plural: "Fakes" },
  collectionPath: "views-entities",
  icon: "bi bi-question",
  api: { list: listMock, get: vi.fn() },
  list: {
    columns: [{ key: "name", header: "Name", render: (row) => row.name, sortable: true, filterable: true, identifier: true }],
    defaultSort: "name:asc",
    searchable: true,
  },
  detail: { tabs: [] },
  routes: { list: "/views-entity", detail: (id) => `/views-entity/${id}` },
};
registerEntityType(config);

const aView = (overrides: Partial<ViewResource> = {}): ViewResource => ({
  id: 12,
  resourceType: "views-entity",
  title: "Mes filtres",
  kind: "USER",
  state: { version: 2, sort: "name:desc", limit: 50, listFilters: { name: { op: "contains", v: "x" } } },
  ownerId: 7,
  ownerName: "Ada L",
  canEdit: true,
  ...overrides,
});

let container: HTMLDivElement;
let root: Root;
let onViewChange: ReturnType<typeof vi.fn>;
let refreshViews: ReturnType<typeof vi.fn>;

async function flush() {
  for (let round = 0; round < 2; round++) {
    await act(async () => {
      for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

async function renderList(props: { viewId?: number; withViews?: boolean; toolbar?: boolean } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <BridgeProvider value={{ refreshViews }}>
          <EntityListPanel
            entityType="views-entity"
            organizationId={7}
            viewId={props.viewId}
            onViewChange={props.withViews === false ? undefined : onViewChange}
            toolbar={props.toolbar ? { chrome: { resourceUri: "/views-entity", title: "Fakes" }, organizationId: 7 } : undefined}
          />
        </BridgeProvider>
      </QueryClientProvider>,
    );
  });
  await flush();
}

function pages(): Record<string, unknown>[] {
  return listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit !== 1);
}

// The header's view button (a split button): its main button, and the one opening its menu.
function viewsButton(): HTMLButtonElement | null {
  return container.querySelector(".entity-list-panel-views-button .p-splitbutton-defaultbutton");
}

async function openMenu() {
  await act(async () => {
    container
      .querySelector(".entity-list-panel-views-button .p-splitbutton-menubutton")!
      .dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await flush();
}

function inBody(text: string, selector = "button"): HTMLElement | undefined {
  return Array.from(document.body.querySelectorAll<HTMLElement>(selector)).find((el) => el.textContent?.includes(text));
}

async function click(el: HTMLElement | undefined) {
  expect(el).toBeDefined();
  await act(async () => {
    el!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await flush();
}

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  onViewChange = vi.fn();
  refreshViews = vi.fn();
  listMock.mockReset();
  listMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 25, offset: 0 });
  for (const mock of [getView, getViewState, createView, updateView, deleteViewState, getBookmarkStatus]) {
    vi.mocked(mock).mockClear();
  }
  vi.mocked(getViewState).mockImplementation((_org, viewKey) => Promise.resolve({ viewKey, state: null }));
  vi.mocked(getView).mockResolvedValue(aView());
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.querySelectorAll(".p-overlaypanel, .p-dialog-mask").forEach((el) => el.remove());
});

describe("EntityListPanel on a saved view", () => {
  it("opens on the view's own state: its sort and filters, in one first request", async () => {
    await renderList({ viewId: 12 });

    expect(getView).toHaveBeenCalledWith(7, 12);
    expect(getViewState).toHaveBeenCalledWith(7, "view:12");
    const requests = pages();
    expect(requests).toHaveLength(1);
    expect(requests[0]).toMatchObject({ sort: "name:desc", limit: SCROLL_CHUNK_SIZE, filters: { name: { op: "contains", v: "x" } } });
  });

  it("puts what the user changed on the view over it, key by key", async () => {
    vi.mocked(getViewState).mockResolvedValue({ viewKey: "view:12", state: { v: 1, sort: "name:asc" } });

    await renderList({ viewId: 12 });

    expect(pages()[0]).toMatchObject({ sort: "name:asc", limit: SCROLL_CHUNK_SIZE, filters: { name: { op: "contains", v: "x" } } });
  });

  it("goes back to the default list when the view no longer exists", async () => {
    vi.mocked(getView).mockRejectedValue(new ApiError(404, "Vue introuvable", undefined, "/api/v1/views/12"));

    await renderList({ viewId: 12 });

    expect(onViewChange).toHaveBeenCalledWith(undefined);
  });

  it("is bookmarked as the view: its address and its title", async () => {
    await renderList({ viewId: 12, toolbar: true });

    expect(getBookmarkStatus).toHaveBeenCalledWith("/views-entity?viewId=12", 7);
  });
});

// The old titlebar's button: shown once the list has been changed, « Mettre à jour » on a view of the
// user's own, « Enregistrer comme nouvelle vue » otherwise, the rest in its menu.
describe("the header's view button", () => {
  const changedOnView = { viewKey: "view:12", state: { v: 1, sort: "name:asc" } };

  it("is not there while the list is as it opened", async () => {
    await renderList();
    expect(viewsButton()).toBeNull();

    act(() => root.render(null));
    await renderList({ viewId: 12 });
    expect(viewsButton()).toBeNull();
  });

  it("is not offered where the pane can't open a view", async () => {
    vi.mocked(getViewState).mockResolvedValue(changedOnView);
    await renderList({ viewId: 12, withViews: false });
    expect(viewsButton()).toBeNull();
  });

  it("offers to save a list the user changed as a new view", async () => {
    vi.mocked(getViewState).mockResolvedValue({ viewKey: "ctx:views-entity:global", state: { v: 1, sort: "name:desc" } });
    await renderList();

    expect(viewsButton()!.textContent).toBe("Enregistrer comme nouvelle vue");
  });

  it("offers to update a view of the user's own that was changed", async () => {
    vi.mocked(getViewState).mockResolvedValue(changedOnView);
    await renderList({ viewId: 12 });
    expect(viewsButton()!.textContent).toBe("Mettre à jour");

    vi.mocked(updateView).mockResolvedValue(aView({ state: { version: 2, sort: "name:asc", limit: 50, listFilters: {} } }));
    await click(viewsButton()!);

    expect(updateView).toHaveBeenCalledWith(
      12,
      expect.objectContaining({ organizationId: 7, title: "Mes filtres", state: expect.objectContaining({ sort: "name:asc" }) }),
    );
    // What the user had changed is now the view itself: nothing is left to give up.
    expect(deleteViewState).toHaveBeenCalledWith(7, "view:12");
  });

  it("offers only to save as new on a view that isn't the user's", async () => {
    vi.mocked(getView).mockResolvedValue(aView({ canEdit: false }));
    vi.mocked(getViewState).mockResolvedValue(changedOnView);
    await renderList({ viewId: 12 });

    expect(viewsButton()!.textContent).toBe("Enregistrer comme nouvelle vue");
    await openMenu();
    expect(inBody("Mettre à jour")).toBeUndefined();
  });

  it("keeps the other actions in its menu", async () => {
    vi.mocked(getViewState).mockResolvedValue(changedOnView);
    await renderList({ viewId: 12 });
    await openMenu();

    expect(inBody("Enregistrer comme nouvelle vue", ".p-menuitem-link")).toBeDefined();
    expect(inBody("Réinitialiser la vue", ".p-menuitem-link")).toBeDefined();
  });

  it("saves the list as a new view, named in a dialog, and opens it", async () => {
    vi.mocked(getViewState).mockResolvedValue({ viewKey: "ctx:views-entity:global", state: { v: 1, sort: "name:desc" } });
    vi.mocked(createView).mockResolvedValue(aView({ id: 20, title: "Nouvelle" }));
    await renderList();
    await click(viewsButton()!);

    const input = document.body.querySelector<HTMLInputElement>("#entity-list-view-title")!;
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
      setter.call(input, "Nouvelle");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await click(inBody("Enregistrer", ".p-dialog button[type=submit]"));

    expect(createView).toHaveBeenCalledWith(
      expect.objectContaining({ organizationId: 7, resourceType: "views-entity", title: "Nouvelle", state: expect.objectContaining({ version: 2, sort: "name:desc" }) }),
    );
    expect(onViewChange).toHaveBeenCalledWith(20);
    // The sidebar's views list (JSF) is redrawn.
    expect(refreshViews).toHaveBeenCalledTimes(1);
  });

  it("redraws the sidebar's views list when a view is updated or renamed", async () => {
    vi.mocked(getViewState).mockResolvedValue(changedOnView);
    vi.mocked(updateView).mockResolvedValue(aView());
    await renderList({ viewId: 12 });

    await click(viewsButton()!);
    expect(updateView).toHaveBeenCalledTimes(1);
    expect(refreshViews).toHaveBeenCalledTimes(1);

    await click(container.querySelector<HTMLElement>(".entity-list-panel-header .sia-list-title-text")!);
    const input = container.querySelector<HTMLInputElement>(".entity-list-panel-view-title-input")!;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, "Autre nom");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();

    expect(updateView).toHaveBeenCalledTimes(2);
    expect(refreshViews).toHaveBeenCalledTimes(2);
  });

  it("gives up the user's changes on a view, back to the view's state", async () => {
    vi.mocked(getViewState).mockResolvedValue(changedOnView);
    await renderList({ viewId: 12 });
    expect(pages()[0]).toMatchObject({ sort: "name:asc" });
    await openMenu();

    await click(inBody("Réinitialiser la vue", ".p-menuitem-link"));

    expect(deleteViewState).toHaveBeenCalledWith(7, "view:12");
    expect(pages()[pages().length - 1]).toMatchObject({ sort: "name:desc", limit: SCROLL_CHUNK_SIZE });
    expect(viewsButton()).toBeNull();
  });
});

describe("the title and the count of a list on a saved view", () => {
  const title = () => container.querySelector<HTMLElement>(".entity-list-panel-header .sia-list-title-text");
  const countChip = () => container.querySelector(".entity-list-panel-header .p-chip-text")?.textContent;

  it("shows the view's name in place of the type's", async () => {
    await renderList({ viewId: 12 });
    expect(title()!.textContent).toContain("Mes filtres");
    expect(container.querySelector(".entity-list-panel-header")!.textContent).not.toContain("Fakes");
  });

  it("keeps the type's name on the default list", async () => {
    await renderList();
    expect(title()!.textContent).toContain("Fakes");
  });

  it("lets the owner rename it, Enter saving, with the view's own state untouched", async () => {
    vi.mocked(updateView).mockResolvedValue(aView({ title: "Renommée" }));
    await renderList({ viewId: 12 });

    await click(title()!);
    const input = container.querySelector<HTMLInputElement>(".entity-list-panel-view-title-input")!;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, "  Renommée ");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();

    expect(updateView).toHaveBeenCalledWith(12, expect.objectContaining({ title: "Renommée", state: aView().state, resourceType: "views-entity" }));
  });

  it("gives up on Escape, and saves nothing for an unchanged name", async () => {
    await renderList({ viewId: 12 });

    await click(title()!);
    await act(async () => {
      container
        .querySelector(".entity-list-panel-view-title-input")!
        .dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });
    expect(container.querySelector(".entity-list-panel-view-title-input")).toBeNull();

    await click(title()!);
    await act(async () => {
      container.querySelector<HTMLInputElement>(".entity-list-panel-view-title-input")!.dispatchEvent(new FocusEvent("focusout", { bubbles: true }));
    });
    expect(updateView).not.toHaveBeenCalled();
  });

  it("is plain text for somebody else's view", async () => {
    vi.mocked(getView).mockResolvedValue(aView({ canEdit: false }));
    await renderList({ viewId: 12 });

    expect(title()!.textContent).toContain("Mes filtres");
    expect(container.querySelector(".entity-list-panel-view-title")).toBeNull();
  });

  it("counts what the view's own filters keep, whatever the user changes on top", async () => {
    listMock.mockImplementation((params) => {
      const p = params as { limit: number; filters?: unknown };
      return Promise.resolve({ data: [{ id: "1", name: "Row A" }], totalCount: p.limit === 1 ? (p.filters ? 3 : 99) : 1, limit: 25, offset: 0 });
    });

    await renderList({ viewId: 12 });

    const counts = listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit === 1);
    expect(counts).toEqual([expect.objectContaining({ filters: { name: { op: "contains", v: "x" } } })]);
    expect(countChip()).toBe("3");
  });

  it("counts the whole collection on a view without filters", async () => {
    vi.mocked(getView).mockResolvedValue(aView({ state: { version: 2, sort: "name:desc" } }));
    listMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 8, limit: 25, offset: 0 });

    await renderList({ viewId: 12 });

    expect(countChip()).toBe("8");
  });
});

