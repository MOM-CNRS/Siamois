import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig } from "../entities/types";
import { EntityDetailPanel } from "./EntityDetailPanel";
import { recallListContext, rememberListContext } from "./listContext";
import { WriteModeProvider } from "./writeMode";
import { BridgeProvider } from "./bridge";
import type { PanelBridge } from "../mountOptions";

// Generic-component test (plan §8 phase 6), registered against a throwaway fake entity type —
// entities/project/config.tsx and FicheTab.tsx already have their own coverage. This exercises
// the mechanism EntityDetailPanel itself is responsible for: rendering config.detail.tabs and
// handing each one a working `refetch` helper (plan §8 phase 6 — added so a tab that mutates the
// entity, like Project's fiche, can ask the panel's own query to reload).


interface FakeEntity {
  id: string;
  name: string;
}

const getMock = vi.fn<(id: string | number) => Promise<FakeEntity>>();

const fakeConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
  key: "fake-detail-entity",
  labels: { singular: "Fake", plural: "Fakes" },
  collectionPath: "fake-detail-entities",
  icon: "bi bi-question",
  api: { list: vi.fn(), get: getMock },
  list: { columns: [], searchable: false },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Fiche",
        render: (entity, helpers) => (
          <div>
            <span data-testid="name">{entity.name}</span>
            <button className="fake-refetch" onClick={() => helpers.refetch()}>refresh</button>
          </div>
        ),
      },
    ],
  },
  routes: { list: "/fake", detail: (id) => `/fake/${id}` },
};

registerEntityType(fakeConfig);

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

beforeEach(() => {
  getMock.mockReset();
  getMock.mockResolvedValue({ id: "1", name: "First" });
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

describe("EntityDetailPanel", () => {
  it("renders the registered tab with the fetched entity", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(getMock).toHaveBeenCalledWith("1");
    expect(container.textContent).toContain("First");
  });

  it("re-invokes config.api.get when the tab calls the refetch helper", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();
    expect(getMock).toHaveBeenCalledTimes(1);

    // By class: the scrollable TabView renders its own ‹ › buttons ahead of the tab content.
    const button = container.querySelector("button.fake-refetch") as HTMLElement;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(getMock).toHaveBeenCalledTimes(2);
  });

  it("refreshes the lists of the entity's type, and only those, when the tab saves", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    const button = container.querySelector("button.fake-refetch") as HTMLElement;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entity-list", "fake-detail-entity"] });
  });

  it("renders its toolbar inside its own header, not as a separate strip (plan §7/§8 follow-up)", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel
            entityType="fake-detail-entity"
            entityId="1"
            toolbar={{ chrome: { resourceUri: "/fake/1", title: "First", bookmarked: false }, actions: { closeFocus: () => {} } }}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    const header = container.querySelector(".p-panel-header")!;
    expect(header).toBeTruthy();
    expect(header.querySelector(".bi-arrows-angle-contract")).toBeTruthy();
  });

  it("renders config.detail.header content inside its own panel header, alongside the toolbar", async () => {
    const withHeaderConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
      ...fakeConfig,
      key: "fake-detail-entity-with-header",
      detail: { ...fakeConfig.detail, header: (entity) => <span data-testid="detail-header">Header of {entity.name}</span> },
    };
    registerEntityType(withHeaderConfig);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity-with-header" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    const header = container.querySelector(".p-panel-header")!;
    expect(header.textContent).toContain("Header of First");
  });

  it("shows an unsupported message for an unregistered entity type", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="does-not-exist" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.textContent).toContain("does-not-exist");
  });
});

describe("EntityDetailPanel breadcrumb", () => {
  async function renderPanel(onNavigate?: (entityType: string, id?: string | number) => void) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" onNavigate={onNavigate} />
        </QueryClientProvider>,
      );
    });
    await flush();
    return container.querySelector(".p-breadcrumb.panel-bc") as HTMLElement;
  }

  it("renders singleUnitPanel.xhtml's two crumbs: the home icon and the entity's list", async () => {
    const bc = await renderPanel();
    expect(bc).toBeTruthy();
    expect(bc.textContent).toContain("Tous les fakes");
    // createHomeItem is a real redirect to the dashboard, not a React navigation.
    const home = bc.querySelector("a[href]") as HTMLAnchorElement;
    expect(home.getAttribute("href")).toContain("/focus/L3dlbGNvbWU=");
  });

  it("switches the pane to that entity's list when the list crumb is pressed", async () => {
    const onNavigate = vi.fn();
    const bc = await renderPanel(onNavigate);

    const crumb = Array.from(bc.querySelectorAll(".p-menuitem-link")).find((el) =>
      el.textContent?.includes("Tous les fakes"),
    ) as HTMLElement;
    await act(async () => {
      crumb.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith("fake-detail-entity");
  });

  it("still renders the crumbs without onNavigate (the overview pane does not pass one)", async () => {
    const bc = await renderPanel(undefined);
    expect(bc.textContent).toContain("Tous les fakes");
  });
});

describe("EntityDetailPanel tab badge and helpers (plan: generic related-list tab)", () => {
  it("renders a tab's badge count next to its label", async () => {
    const withBadgeConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
      ...fakeConfig,
      key: "fake-detail-entity-with-badge",
      detail: {
        tabs: [{ ...fakeConfig.detail.tabs[0], badge: () => 7 }],
      },
    };
    registerEntityType(withBadgeConfig);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity-with-badge" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    const tabHeader = container.querySelector(".p-tabview-nav");
    expect(tabHeader?.textContent).toContain("Fiche");
    expect(tabHeader?.textContent).toContain("7");
  });

  it("renders a plain label with no badge chip when the tab declares none", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    const tabHeader = container.querySelector(".p-tabview-nav")!;
    expect(tabHeader.querySelector(".p-chip")).toBeNull();
  });

  it("forwards organizationId/onOpenOverview/overviewEntityId into DetailTabHelpers", async () => {
    const onOpenOverview = vi.fn();
    let seen: unknown;
    const withHelpersConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
      ...fakeConfig,
      key: "fake-detail-entity-with-helpers",
      detail: {
        tabs: [
          {
            key: "fiche",
            label: "Fiche",
            render: (_entity, helpers) => {
              seen = helpers;
              return <div />;
            },
          },
        ],
      },
    };
    registerEntityType(withHelpersConfig);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel
            entityType="fake-detail-entity-with-helpers"
            entityId="1"
            organizationId={42}
            onOpenOverview={onOpenOverview}
            overviewEntityId="9"
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(seen).toMatchObject({ organizationId: 42, onOpenOverview, overviewEntityId: "9" });
  });
});

describe("EntityDetailPanel sibling navigation (plan: fiche précédente/suivante)", () => {
  const siblingsMock = vi.fn();

  const withSiblingsConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
    ...fakeConfig,
    key: "fake-detail-entity-with-siblings",
    api: { ...fakeConfig.api, siblings: siblingsMock },
  };
  registerEntityType(withSiblingsConfig);

  beforeEach(() => {
    siblingsMock.mockReset();
  });

  describe("walking the list the fiche was opened from", () => {
    const listMock = vi.fn();
    const walkConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
      ...fakeConfig,
      key: "fake-walked-entity",
      api: { ...fakeConfig.api, list: listMock, siblings: siblingsMock },
    };
    registerEntityType(walkConfig);
    // The rows of "name:asc, search=zz", as the list request serves them.
    const ROWS = ["50", "51", "52", "53"].map((id) => ({ id, fullIdentifier: `row ${id}` }));

    beforeEach(() => {
      window.sessionStorage.clear();
      listMock.mockReset();
      listMock.mockImplementation(async (p: { offset: number; limit: number }) => {
        // The real endpoints refuse an offset that isn't a multiple of the page size (a 400).
        if (p.offset % p.limit !== 0) throw new Error("offset doit être un multiple de limit");
        return { data: ROWS.slice(p.offset, p.offset + p.limit), totalCount: ROWS.length };
      });
    });

    function renderFiche(entityId: string, onNavigateSibling = vi.fn()) {
      const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      act(() => {
        root.render(
          <QueryClientProvider client={queryClient}>
            <EntityDetailPanel entityType="fake-walked-entity" entityId={entityId} onNavigateSibling={onNavigateSibling} />
          </QueryClientProvider>,
        );
      });
      return onNavigateSibling;
    }

    it("takes the arrows from that list's own request, not the default order", async () => {
      rememberListContext("fake-walked-entity", "51", { params: { sort: "name:asc", search: "zz" }, index: 1 });
      const onNavigateSibling = renderFiche("51");
      await flush();

      expect(listMock).toHaveBeenCalledWith({ sort: "name:asc", search: "zz", offset: 0, limit: 1 });
      expect(siblingsMock).not.toHaveBeenCalled();
      const buttons = container.querySelectorAll(".sideview-topbar-button");
      await act(async () => {
        buttons[1].dispatchEvent(new MouseEvent("click", { bubbles: true }));
      });
      expect(onNavigateSibling).toHaveBeenCalledWith("52", { label: "row 52" });
    });

    it("carries the walk on: the neighbour opens knowing its own position in the same list", async () => {
      const params = { sort: "name:asc", search: "zz" };
      rememberListContext("fake-walked-entity", "51", { params, index: 1 });
      renderFiche("51");
      await flush();
      const buttons = container.querySelectorAll(".sideview-topbar-button");
      await act(async () => {
        buttons[1].dispatchEvent(new MouseEvent("click", { bubbles: true }));
      });

      expect(recallListContext("fake-walked-entity", "52")).toEqual({ params, index: 2 });
    });

    it("falls back to the default order when that list no longer holds the entity", async () => {
      siblingsMock.mockResolvedValue({ previous: undefined, next: { id: "77", label: "Seventy-seven", resourceUri: "/x/77" } });
      // Position 1 of the list is row 51 now, not the entity being shown.
      rememberListContext("fake-walked-entity", "999", { params: { sort: "name:asc" }, index: 1 });
      const onNavigateSibling = renderFiche("999");
      await flush();

      expect(siblingsMock).toHaveBeenCalledWith("999", { organizationId: undefined });
      const buttons = container.querySelectorAll(".sideview-topbar-button");
      await act(async () => {
        buttons[1].dispatchEvent(new MouseEvent("click", { bubbles: true }));
      });
      expect(onNavigateSibling).toHaveBeenCalledWith("77", { label: "Seventy-seven" });
      // A default-order neighbour has no list position to remember.
      expect(recallListContext("fake-walked-entity", "77")).toBeUndefined();
    });

    it("uses the default order for an entity that was not opened from a list", async () => {
      siblingsMock.mockResolvedValue({ previous: undefined, next: undefined });
      renderFiche("51");
      await flush();

      expect(siblingsMock).toHaveBeenCalledTimes(1);
      expect(listMock).not.toHaveBeenCalled();
    });
  });

  it("renders no arrows when config.api.siblings is absent", async () => {
    const onNavigateSibling = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" onNavigateSibling={onNavigateSibling} />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector(".sideview-topbar-button")).toBeNull();
    expect(siblingsMock).not.toHaveBeenCalled();
  });

  it("renders no arrows when onNavigateSibling is absent, even with config.api.siblings present", async () => {
    siblingsMock.mockResolvedValue({ previous: undefined, next: undefined });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity-with-siblings" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector(".sideview-topbar-button")).toBeNull();
  });

  it("calls onNavigateSibling with the neighbour's id when an arrow is clicked", async () => {
    siblingsMock.mockResolvedValue({
      previous: { id: "0", label: "Zero", resourceUri: "/action-unit/0" },
      next: { id: "2", label: "Two", resourceUri: "/action-unit/2" },
    });
    const onNavigateSibling = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel
            entityType="fake-detail-entity-with-siblings"
            entityId="1"
            onNavigateSibling={onNavigateSibling}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(siblingsMock).toHaveBeenCalledWith("1", { organizationId: undefined });
    const buttons = container.querySelectorAll(".sideview-topbar-button");
    expect(buttons).toHaveLength(2);
    await act(async () => {
      buttons[1].dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    // With the neighbour's label, so the next fiche can draw its header before it loads.
    expect(onNavigateSibling).toHaveBeenCalledWith("2", { label: expect.any(String) });
  });

  it("disables an arrow whose neighbour is undefined and does not navigate on click", async () => {
    siblingsMock.mockResolvedValue({ previous: undefined, next: { id: "2", label: "Two", resourceUri: "/action-unit/2" } });
    const onNavigateSibling = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel
            entityType="fake-detail-entity-with-siblings"
            entityId="1"
            onNavigateSibling={onNavigateSibling}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    const buttons = container.querySelectorAll(".sideview-topbar-button");
    expect((buttons[0] as HTMLButtonElement).disabled).toBe(true);
    await act(async () => {
      buttons[0].dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigateSibling).not.toHaveBeenCalled();
  });

  it("preserves the active tab across a sibling jump (the TabView stays mounted, only entityId changes)", async () => {
    const twoTabsConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
      ...withSiblingsConfig,
      key: "fake-detail-entity-with-siblings-and-tabs",
      detail: {
        tabs: [
          { key: "fiche", label: "Fiche", render: (entity) => <span>Fiche of {entity.name}</span> },
          { key: "other", label: "Autre", render: (entity) => <span>Autre of {entity.name}</span> },
        ],
      },
    };
    registerEntityType(twoTabsConfig);
    siblingsMock.mockResolvedValue({
      previous: undefined,
      next: { id: "2", label: "Two", resourceUri: "/action-unit/2" },
    });
    getMock.mockImplementation((id) => Promise.resolve({ id: String(id), name: `Entity ${id}` }));

    function Harness() {
      const [entityId, setEntityId] = useState<string | number>("1");
      return (
        <EntityDetailPanel
          entityType="fake-detail-entity-with-siblings-and-tabs"
          entityId={entityId}
          onNavigateSibling={setEntityId}
        />
      );
    }
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <Harness />
        </QueryClientProvider>,
      );
    });
    await flush();

    const tabs = container.querySelectorAll(".p-tabview-nav li");
    await act(async () => {
      (tabs[1].querySelector("a") as HTMLElement).dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    expect(container.textContent).toContain("Autre of Entity 1");

    const nextButton = container.querySelectorAll(".sideview-topbar-button")[1] as HTMLElement;
    await act(async () => {
      nextButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.textContent).toContain("Autre of Entity 2");
  });
});

// JSF's titlebar create/duplicate/settings, now built by the panel itself from its entity's own
// data (`_permissions`, the write mode) — so they are right for whichever entity is displayed.
describe("EntityDetailPanel entity actions", () => {
  interface ActionEntity {
    id: string;
    name: string;
    _permissions?: { canEdit: boolean; canManageSettings?: boolean };
  }
  const actionGet = vi.fn<(id: string | number) => Promise<ActionEntity>>();
  const duplicateMock = vi.fn<(id: string | number) => Promise<ActionEntity>>();
  const actionConfig: EntityTypeConfig<ActionEntity, ActionEntity> = {
    key: "fake-action-entity",
    labels: { singular: "Action", plural: "Actions" },
    collectionPath: "fake-action-entities",
    icon: "bi bi-question",
    api: { list: vi.fn(), get: actionGet, duplicate: duplicateMock },
    list: {
      columns: [],
      searchable: false,
      createForm: (ctx) => <div data-testid="sia-create-form">scope {String(ctx.scope?.id)}</div>,
    },
    detail: {
      tabs: [{ key: "fiche", label: "Fiche", render: (e) => <span>{e.name}</span> }],
      createScope: () => ({ entityType: "project", id: "42" }),
      settingsProjectId: (e) => (e._permissions?.canManageSettings ? e.id : undefined),
    },
    routes: { list: "/fake-action", detail: (id) => `/fake-action/${id}` },
  };
  registerEntityType(actionConfig);

  function renderPanel(opts: { writeMode: boolean; bridge?: PanelBridge; onOpenOverview?: (t: string, id: string | number) => void }) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={opts.writeMode}>
            <BridgeProvider value={opts.bridge}>
              <EntityDetailPanel
                entityType="fake-action-entity"
                entityId="7"
                onOpenOverview={opts.onOpenOverview}
                toolbar={{ chrome: { resourceUri: "", title: "" } }}
              />
            </BridgeProvider>
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  beforeEach(() => {
    actionGet.mockReset().mockResolvedValue({ id: "7", name: "Seven", _permissions: { canEdit: true } });
    duplicateMock.mockReset().mockResolvedValue({ id: "8", name: "Seven (copie)" });
  });

  it("offers create and duplicate only in write mode with edit rights", async () => {
    renderPanel({ writeMode: false });
    await flush();
    expect(container.querySelector(".bi-plus-square")).toBeNull();
    expect(container.querySelector(".bi-copy")).toBeNull();

    renderPanel({ writeMode: true });
    await flush();
    expect(container.querySelector(".bi-plus-square")).toBeTruthy();
    expect(container.querySelector(".bi-copy")).toBeTruthy();

    actionGet.mockResolvedValue({ id: "7", name: "Seven", _permissions: { canEdit: false } });
    act(() => root.unmount());
    root = createRoot(container);
    renderPanel({ writeMode: true });
    await flush();
    expect(container.querySelector(".bi-plus-square")).toBeNull();
    expect(container.querySelector(".bi-copy")).toBeNull();
  });

  it("duplicates through the API and opens the copy in the overview", async () => {
    const onOpenOverview = vi.fn();
    renderPanel({ writeMode: true, onOpenOverview });
    await flush();

    await act(async () => {
      container.querySelector(".bi-copy")!.closest("button")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(duplicateMock).toHaveBeenCalledWith("7");
    expect(onOpenOverview).toHaveBeenCalledWith("fake-action-entity", "8");
  });

  it("opens the create form in a dialog, scoped by config.detail.createScope", async () => {
    renderPanel({ writeMode: true });
    await flush();

    await act(async () => {
      container.querySelector(".bi-plus-square")!.closest("button")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const form = document.querySelector("[data-testid='sia-create-form']");
    expect(form?.textContent).toBe("scope 42");
  });

  it("shows the validation status button on a fiche that has a status", async () => {
    actionGet.mockResolvedValue({ id: "7", name: "Seven", _permissions: { canEdit: true } , validated: "COMPLETE" } as ActionEntity);
    renderPanel({ writeMode: true });
    await flush();

    expect(container.querySelector("button.status-button.complete")).toBeTruthy();
  });

  it("shows settings only when allowed, and opens them through the bridge with the entity's id", async () => {
    const openProjectSettings = vi.fn();
    renderPanel({ writeMode: false, bridge: { openProjectSettings } });
    await flush();
    expect(container.querySelector(".bi-gear")).toBeNull();

    actionGet.mockResolvedValue({ id: "7", name: "Seven", _permissions: { canEdit: false, canManageSettings: true } });
    act(() => root.unmount());
    root = createRoot(container);
    renderPanel({ writeMode: false, bridge: { openProjectSettings } });
    await flush();

    await act(async () => {
      container.querySelector(".bi-gear")!.closest("button")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(openProjectSettings).toHaveBeenCalledWith("7");
  });
});

// While the entity loads, the fiche keeps its frame: the header already shows what the opener knew
// (a list row's or a sibling's label), and everything below is a skeleton — no "Loading…" text,
// no empty box.
describe("EntityDetailPanel loading state", () => {
  it("shows the preview's label in the header and a skeleton body until the entity arrives", async () => {
    let resolve!: (entity: FakeEntity) => void;
    getMock.mockReturnValue(new Promise<FakeEntity>((r) => (resolve = r)));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" preview={{ label: "UE-12" }} />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector(".entity-detail-panel-preview-chip")?.textContent).toBe("UE-12");
    expect(container.querySelector(".sia-detail-skeleton")).toBeTruthy();
    expect(container.textContent).not.toContain("Loading");

    await act(async () => resolve({ id: "1", name: "First" }));
    await flush();

    expect(container.querySelector(".sia-detail-skeleton")).toBeNull();
    expect(container.querySelector('[data-testid="name"]')?.textContent).toBe("First");
  });

  it("falls back to a skeleton identifier when nothing is known yet", async () => {
    getMock.mockReturnValue(new Promise<FakeEntity>(() => {}));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector(".entity-detail-panel-preview-chip")).toBeNull();
    expect(container.querySelector(".entity-detail-panel-preview-header .p-skeleton")).toBeTruthy();
  });
});

// The titlebar's "Créer" opens the entity's create form in an overlay next to the button — no modal.
describe("EntityDetailPanel titlebar creation", () => {
  registerEntityType({
    ...fakeConfig,
    key: "fake-detail-entity-creatable",
    list: { ...fakeConfig.list, createForm: () => <div className="fake-create-form" /> },
  });

  it("opens the create form in an overlay anchored on the clicked button", async () => {
    getMock.mockResolvedValue({ id: "1", name: "First", _permissions: { canEdit: true } } as FakeEntity);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityDetailPanel
              entityType="fake-detail-entity-creatable"
              entityId="1"
              toolbar={{ chrome: { resourceUri: "/fake/1", title: "First", bookmarked: false }, organizationId: 7 }}
            />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    const createButton = container.querySelector('button[aria-label="Créer"]') as HTMLButtonElement;
    expect(createButton).toBeTruthy();
    await act(async () => {
      createButton.click();
      await new Promise((r) => setTimeout(r, 50));
    });

    expect(document.body.querySelector(".p-overlaypanel .fake-create-form")).toBeTruthy();
    expect(document.body.querySelector(".p-dialog")).toBeNull();
  });
});
