import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig } from "../entities/types";
import { EntityDetailPanel } from "./EntityDetailPanel";

// Generic-component test (plan §8 phase 6), registered against a throwaway fake entity type —
// entities/project/config.tsx and FicheTab.tsx already have their own coverage. This exercises
// the mechanism EntityDetailPanel itself is responsible for: rendering config.detail.tabs and
// handing each one a working `refetch` helper (plan §8 phase 6 — added so a tab that mutates the
// entity, like Project's fiche, can ask the panel's own query to reload).

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

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
            <button onClick={() => helpers.refetch()}>refresh</button>
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

    const button = container.querySelector("button") as HTMLElement;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(getMock).toHaveBeenCalledTimes(2);
  });

  it("renders its toolbar inside its own header, not as a separate strip (plan §7/§8 follow-up)", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel
            entityType="fake-detail-entity"
            entityId="1"
            toolbar={{ chrome: { resourceUri: "/fake/1", title: "First", bookmarked: false }, actions: { refresh: () => {} } }}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    const header = container.querySelector(".p-panel-header")!;
    expect(header).toBeTruthy();
    expect(header.querySelector(".bi-arrow-clockwise")).toBeTruthy();
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
