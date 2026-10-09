import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig, PagedResult } from "../entities/types";
import { relationTab } from "./relationTab";
import { WriteModeProvider } from "./writeMode";


interface FakeChildRow {
  id: string;
  name: string;
}

interface FakeParent {
  id: string | number;
}

const childListMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeChildRow>>>();

const childConfig: EntityTypeConfig<FakeChildRow, FakeChildRow> = {
  key: "fake-child-entity",
  labels: { singular: "Child", plural: "Children" },
  collectionPath: "fake-child-entities",
  icon: "bi bi-question",
  api: { list: childListMock, get: vi.fn() },
  list: { columns: [{ key: "name", header: "Name", render: (row) => row.name, identifier: true }], searchable: false },
  detail: { tabs: [] },
  routes: { list: "/fake-child", detail: (id) => `/fake-child/${id}` },
};
registerEntityType(childConfig);

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
  childListMock.mockReset();
  childListMock.mockResolvedValue({ data: [{ id: "9", name: "Child A" }], totalCount: 1, limit: 10, offset: 0 });
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

describe("relationTab", () => {
  const tab = relationTab<FakeParent>({
    key: "children",
    label: "Children",
    target: "fake-child-entity",
    scopeEntityType: "fake-parent-entity",
    badge: (entity) => (entity.id === "with-badge" ? 3 : undefined),
  });

  it("renders an embedded EntityListPanel scoped to the parent entity's id", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            {tab.render({ id: 5 }, { refetch: () => {}, organizationId: 42 })}
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(childListMock).toHaveBeenCalledWith(
      expect.objectContaining({
        organizationId: 42,
        scope: { entityType: "fake-parent-entity", id: 5, path: undefined },
      }),
    );
    // embedded: true means no <Panel>/PanelHeaderBar chrome around the list.
    expect(container.querySelector(".p-panel-header")).toBeNull();
    expect(container.textContent).toContain("Child A");
  });

  it("forwards onOpenOverview/onNavigate/overviewEntityId from DetailTabHelpers", async () => {
    const onOpenOverview = vi.fn();
    const onNavigate = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            {tab.render(
              { id: 5 },
              { refetch: () => {}, onOpenOverview, onNavigate, overviewEntityId: "9" },
            )}
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    const identifierChip = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    await act(async () => {
      identifierChip.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onOpenOverview).toHaveBeenCalledWith("fake-child-entity", "9", { label: "Child A", validationStatus: undefined });
  });

  it("exposes the tab's key/label/badge for EntityDetailPanel to render", () => {
    expect(tab.key).toBe("children");
    expect(tab.label).toBe("Children");
    expect(tab.badge?.({ id: "with-badge" })).toBe(3);
    expect(tab.badge?.({ id: "other" })).toBeUndefined();
  });
});

describe("relationTab project context and create", () => {
  const creatableChild: EntityTypeConfig<FakeChildRow, FakeChildRow> = {
    ...childConfig,
    key: "fake-creatable-child",
    list: { ...childConfig.list, createForm: () => <div>form</div> },
  };
  registerEntityType(creatableChild);

  function renderTab(creatable?: boolean) {
    const tab = relationTab<FakeParent & { projectId: string }>({
      key: "children",
      label: "Children",
      target: "fake-creatable-child",
      scopeEntityType: "fake-parent-entity",
      path: "children",
      projectId: (entity) => entity.projectId,
      creatable,
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            {tab.render({ id: 5, projectId: "6" }, { refetch: () => {}, organizationId: 42 })}
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  it("passes the parent's project along in the scope (scopeProjectId reads it)", async () => {
    renderTab();
    await flush();

    expect(childListMock).toHaveBeenCalledWith(
      expect.objectContaining({
        scope: { entityType: "fake-parent-entity", id: 5, path: "children", projectId: "6" },
      }),
    );
  });

  it("hides the list's Créer when creatable is false, keeps it otherwise", async () => {
    renderTab(false);
    await flush();
    expect(Array.from(container.querySelectorAll("button")).some((b) => b.textContent === "Créer")).toBe(false);

    renderTab(true);
    await flush();
    expect(Array.from(container.querySelectorAll("button")).some((b) => b.textContent === "Créer")).toBe(true);
  });

  it("adds the embedding place's toolbar control and row actions, run with a confirm/refresh/fail context", async () => {
    childListMock.mockResolvedValue({
      data: [{ id: "9", name: "Child A", _permissions: { canEdit: true } } as FakeChildRow],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    const run = vi.fn();
    const extraTab = relationTab<FakeParent>({
      key: "children",
      label: "Children",
      target: "fake-child-entity",
      scopeEntityType: "fake-parent-entity",
      toolbarExtra: (entity, helpers) => (
        <button type="button" data-testid="extra">{`extra ${entity.id} ${helpers.organizationId}`}</button>
      ),
      extraRowActions: () => [{ key: "unlink", icon: "bi bi-x", tooltip: "Retirer", run }],
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>{extraTab.render({ id: 5 }, { refetch: () => {}, organizationId: 42 })}</WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector('[data-testid="extra"]')?.textContent).toBe("extra 5 42");
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-action="unlink"]')!.click();
    });
    expect(run).toHaveBeenCalledWith(
      expect.objectContaining({ id: "9" }),
      expect.objectContaining({ confirm: expect.any(Function), refresh: expect.any(Function), fail: expect.any(Function) }),
    );
  });
});
