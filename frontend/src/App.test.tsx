import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { App } from "./App";
import { registerEntityType } from "./entities/registry";
import { relationTab } from "./panels/relationTab";
import type { EntityTypeConfig } from "./entities/types";
import type { MountOptions } from "./mountOptions";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// App.tsx owns navigation itself now (plan §8 phase 8 follow-up): clicking a list row or a Home
// widget link must swap what's rendered and update the URL via history.pushState, never a real
// page navigation — that was the entire point of moving these panels to React in the first
// place. Registered against a throwaway fake entity, not Project, to keep this a test of App's
// own router rather than of any one entity's config.
interface FakeRow {
  id: string;
  name: string;
}

const listMock = vi.fn();
const getMock = vi.fn();

const fakeConfig: EntityTypeConfig<FakeRow, FakeRow> = {
  key: "fake-app-entity",
  labels: { singular: "Fake", plural: "Fakes" },
  collectionPath: "fake-app-entities",
    icon: "bi bi-question",
  api: { list: listMock, get: getMock },
  list: {
    columns: [{ key: "name", header: "Name", render: (row) => row.name, identifier: true }],
    searchable: false,
  },
  detail: { tabs: [{ key: "fiche", label: "Fiche", render: (entity) => <span>Detail of {entity.name}</span> }] },
  routes: { list: "/fake-app-entity", detail: (id) => `/fake-app-entity/${id}` },
  home: {
    widgets: (ctx) => [
      {
        key: "fake-home-widget",
        render: () => (
          <button type="button" onClick={() => ctx.onNavigate?.("unregistered-type", "42")}>
            Go to unregistered
          </button>
        ),
      },
      {
        key: "fake-home-widget-registered",
        render: () => (
          <button type="button" onClick={() => ctx.onNavigate?.("fake-app-entity", "1")}>
            Go to detail
          </button>
        ),
      },
    ],
  },
};

registerEntityType(fakeConfig);

// A second entity type embedded in fake-app-entity's own detail via a relationTab (mirrors
// Project's own "recording-units" tab, entities/project/config.tsx), used to exercise clicking a
// row of it while its PARENT is itself open in the overview pane — a click inside the overview
// must retarget the overview to this child, not silently no-op (App.tsx's overview-pane
// EntityDetailPanel previously received no onOpenOverview/onNavigate at all).
const childListMock = vi.fn();
const childGetMock = vi.fn();

const fakeChildConfig: EntityTypeConfig<FakeRow, FakeRow> = {
  key: "fake-child-entity",
  labels: { singular: "Child", plural: "Children" },
  collectionPath: "fake-child-entities",
  icon: "bi bi-question",
  api: { list: childListMock, get: childGetMock },
  list: {
    columns: [{ key: "name", header: "Name", render: (row) => row.name, identifier: true }],
    searchable: false,
  },
  detail: { tabs: [{ key: "fiche", label: "Fiche", render: (entity) => <span>Child detail of {entity.name}</span> }] },
  routes: { list: "/fake-child-entity", detail: (id) => `/fake-child-entity/${id}` },
};

registerEntityType(fakeChildConfig);

const fakeParentWithChildTabConfig: EntityTypeConfig<FakeRow, FakeRow> = {
  ...fakeConfig,
  key: "fake-parent-with-child-tab",
  detail: {
    tabs: [
      { key: "fiche", label: "Fiche", render: (entity) => <span>Detail of {entity.name}</span> },
      relationTab<FakeRow>({
        key: "children",
        label: "Children",
        target: "fake-child-entity",
        scopeEntityType: "fake-parent-with-child-tab",
      }),
    ],
  },
  routes: { list: "/fake-parent-with-child-tab", detail: (id) => `/fake-parent-with-child-tab/${id}` },
};

registerEntityType(fakeParentWithChildTabConfig);

function baseOptions(overrides: Partial<MountOptions> = {}): MountOptions {
  return {
    panelKind: "list",
    entityType: "fake-app-entity",
    basePath: "",
    csrf: { headerName: "X-CSRF", token: "t" },
    main: { resourceUri: "/fake-app-entity", title: "Fakes", bookmarked: false },
    actions: { duplicate: vi.fn(), create: vi.fn(), settings: vi.fn() },
    ...overrides,
  };
}

function b64(value: string): string {
  return btoa(value).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

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
  listMock.mockReset().mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 20, offset: 0 });
  getMock.mockReset().mockResolvedValue({ id: "1", name: "Row A" });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  window.history.replaceState(null, "", "/fake-app-entity");
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
});

describe("App client-side navigation", () => {
  // Row click opens the entity in the right-hand overview pane, matching JSF's own row-click
  // target (plan §8 phase 5) — it no longer replaces the main pane. Full client-side navigation
  // (`navigate`) still exists for other callers (Home widgets, an explicit "open full"); see the
  // toolbar-hiding test below for that path.
  it("opens the identifier's entity in the overview pane on click, without navigating the main pane away", async () => {
    act(() => {
      root.render(<App options={baseOptions()} />);
    });
    await flush();

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    expect(identifierCell).toBeTruthy();

    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    // The list is still on screen — the main pane never navigated away...
    expect(container.querySelector("tbody tr")).toBeTruthy();
    // ...and the overview pane rendered the same entity's detail alongside it.
    expect(container.textContent).toContain("Detail of Row A");
    expect(getMock).toHaveBeenCalledWith("1");
    // URL reflects the overview via the `s` param, mirroring FlowBean.redirectToFocus/
    // showSideview's own `/focus/<main>?s=<overview>` scheme.
    expect(window.location.search).toContain("s=");
  });

  it("keeps the main toolbar visible once the overview opens, unlike a full navigate", async () => {
    act(() => {
      root.render(<App options={baseOptions()} />);
    });
    await flush();

    expect(container.querySelector(".bi-copy")).toBeTruthy();

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelector(".bi-copy")).toBeTruthy();
  });

  it("hides the main toolbar entirely after a full client-side navigation (it was built for the original entity only)", async () => {
    act(() => {
      root.render(<App options={baseOptions({ panelKind: "home", entityType: "fake-app-entity" })} />);
    });
    await flush();

    expect(container.querySelector(".bi-copy")).toBeTruthy();
    expect(container.querySelector(".bi-bookmark")).toBeTruthy();

    const button = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Go to detail")!;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelector(".bi-copy")).toBeNull();
    expect(container.querySelector(".bi-bookmark")).toBeNull();
  });

  // F5 replays the address bar, so every pushed URL must be the /focus/<main>[?s=<overview>] form
  // FocusViewBean decodes — a bare entity route (/phase/5, /container/2) had no server route, and a
  // stale main token brought back the page the user left.
  it("pushes a /focus URL encoding the new main view after a client-side navigation", async () => {
    act(() => {
      root.render(<App options={baseOptions({ panelKind: "home", entityType: "fake-app-entity", main: { resourceUri: "/welcome", title: "Home", bookmarked: false } })} />);
    });
    await flush();

    const button = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Go to detail")!;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(window.location.pathname).toBe(`/focus/${b64("/fake-app-entity/1")}`);
    expect(window.location.search).toBe("");
  });

  it("encodes the CURRENT main view, not the originally mounted one, when an overview opens after navigating", async () => {
    act(() => {
      root.render(
        <App options={baseOptions({ panelKind: "detail", entityId: "1", main: { resourceUri: "/fake-app-entity/1", title: "Row A", bookmarked: false } })} />,
      );
    });
    await flush();

    // Breadcrumb "Tous les fakes" -> client-side navigate to the list.
    const crumb = Array.from(container.querySelectorAll(".p-breadcrumb.panel-bc .p-menuitem-link")).find((el) =>
      el.textContent?.includes("Tous les"),
    ) as HTMLElement;
    await act(async () => {
      crumb.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    expect(window.location.pathname).toBe(`/focus/${b64("/fake-app-entity")}`);

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(window.location.pathname).toBe(`/focus/${b64("/fake-app-entity")}`);
    expect(new URLSearchParams(window.location.search).get("s")).toBe(b64("/fake-app-entity/1"));
  });

  it("falls back to a real page navigation for an entity type that isn't registered", async () => {
    act(() => {
      root.render(<App options={baseOptions({ panelKind: "home", entityType: "fake-app-entity" })} />);
    });
    await flush();

    const originalLocation = window.location;
    Object.defineProperty(window, "location", { value: { href: "" }, writable: true });

    const button = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Go to unregistered")!;
    expect(button).toBeTruthy();
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(window.location.href).toBe("/unregistered-type/42");
    Object.defineProperty(window, "location", { value: originalLocation, writable: true });
  });

  it("closes the overview, clears the s= URL param and calls the bridged closeOverview action", async () => {
    const closeOverview = vi.fn();
    act(() => {
      root.render(<App options={baseOptions({ overviewActions: { closeOverview } })} />);
    });
    await flush();

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    expect(window.location.search).toContain("s=");

    const closeButton = container.querySelector(".bi-chevron-double-right") as HTMLElement;
    expect(closeButton).toBeTruthy();
    await act(async () => {
      closeButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(closeOverview).toHaveBeenCalled();
    expect(container.textContent).not.toContain("Detail of Row A");
    expect(window.location.search).not.toContain("s=");
  });

  // The bug this covers: App's overview-pane EntityDetailPanel used to receive no
  // onOpenOverview/onNavigate at all, so a row click inside a tab rendered THERE (a relationTab,
  // e.g. a project's own UE list) silently did nothing — the click only worked from the main
  // pane's own list. It must retarget the overview in place, even though the click originates
  // from within the overview itself.
  it("retargets the overview pane when a row is clicked inside a tab rendered in the overview itself", async () => {
    childListMock.mockReset().mockResolvedValue({
      data: [{ id: "9", name: "Child row" }],
      totalCount: 1,
      limit: 20,
      offset: 0,
    });
    childGetMock.mockReset().mockResolvedValue({ id: "9", name: "Child row" });

    act(() => {
      root.render(
        <App
          options={baseOptions({
            overviewEntityType: "fake-parent-with-child-tab",
            overviewEntityId: "1",
          })}
        />,
      );
    });
    await flush();
    expect(container.textContent).toContain("Detail of Row A");

    // Scoped to the overview pane — the main pane (still the plain fake-app-entity list) has its
    // own ".entity-list-panel-identifier-link" too, and a bare container-wide query would find
    // that one first.
    const overviewPane = container.querySelector(".panel-splitter-panel-r") as HTMLElement;
    expect(overviewPane).toBeTruthy();

    const childrenTab = Array.from(overviewPane.querySelectorAll(".p-tabview-nav li a")).find((el) =>
      el.textContent?.includes("Children"),
    ) as HTMLElement;
    expect(childrenTab).toBeTruthy();
    await act(async () => {
      childrenTab.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const childIdentifierCell = overviewPane.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    expect(childIdentifierCell).toBeTruthy();
    await act(async () => {
      childIdentifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    await flush();

    // The overview now shows the CHILD, not the parent that was there before — retargeted in
    // place, never a navigation of the main pane (which is still the plain fake-app-entity list).
    expect(childGetMock).toHaveBeenCalledWith("9");
    expect(container.textContent).toContain("Child detail of Child row");
    expect(container.querySelector("tbody tr")).toBeTruthy();
  });
});

// Focus mode: the overview entity is promoted to the main pane (the overview's "Ouvrir en mode
// focus" button) and closeFocus puts it back — both client-side, no bean call on the way in
// (FlowBean still holds "main = previous main, parentOrOverview = promoted", i.e. the way back).
describe("App focus mode", () => {
  async function click(el: Element | null | undefined) {
    expect(el).toBeTruthy();
    await act(async () => {
      el!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
  }

  const mainPane = () => container.querySelector(".panel-splitter-panel-l") as HTMLElement;

  it("promotes the overview entity to the main pane, with a back= URL and no bean call", async () => {
    const setOverview = vi.fn();
    const overviewDuplicate = vi.fn();
    act(() => {
      root.render(
        <App
          options={baseOptions({
            overviewEntityType: "fake-app-entity",
            overviewEntityId: "1",
            actions: { duplicate: vi.fn(), setOverview },
            overviewActions: { duplicate: overviewDuplicate, closeOverview: vi.fn() },
          })}
        />,
      );
    });
    await flush();

    await click(container.querySelector(".panel-splitter-panel-r .bi-arrows-angle-expand"));

    expect(container.querySelector(".panel-splitter-panel-r")).toBeNull();
    expect(container.querySelector("tbody tr")).toBeNull();
    expect(mainPane().textContent).toContain("Detail of Row A");
    expect(window.location.pathname).toBe(`/focus/${b64("/fake-app-entity/1")}`);
    expect(window.location.search).toContain(
      `back=${b64(`/focus/${b64("/fake-app-entity")}?s=${b64("/fake-app-entity/1")}`)}`,
    );
    expect(window.location.search).not.toContain("s=");
    expect(setOverview).not.toHaveBeenCalled();

    // The bean's overview actions are bound to parentOrOverview — the promoted entity — so they
    // act for the new main.
    await click(mainPane().querySelector(".bi-copy"));
    expect(overviewDuplicate).toHaveBeenCalled();
    expect(mainPane().querySelector(".bi-arrows-angle-contract")).toBeTruthy();
  });

  it("closeFocus restores the previous main, its toolbar and the overview", async () => {
    const setOverview = vi.fn();
    const mainDuplicate = vi.fn();
    act(() => {
      root.render(
        <App
          options={baseOptions({
            overviewEntityType: "fake-app-entity",
            overviewEntityId: "1",
            actions: { duplicate: mainDuplicate, setOverview },
            overviewActions: { closeOverview: vi.fn() },
          })}
        />,
      );
    });
    await flush();

    await click(container.querySelector(".panel-splitter-panel-r .bi-arrows-angle-expand"));
    await click(mainPane().querySelector(".bi-arrows-angle-contract"));

    expect(mainPane().querySelector("tbody tr")).toBeTruthy();
    expect(container.querySelector(".panel-splitter-panel-r")?.textContent).toContain("Detail of Row A");
    expect(window.location.pathname).toBe(`/focus/${b64("/fake-app-entity")}`);
    expect(window.location.search).toBe(`?s=${b64("/fake-app-entity/1")}`);
    // Bean never left that state — nothing to resync.
    expect(setOverview).not.toHaveBeenCalled();
    expect(mainPane().querySelector(".bi-arrows-angle-contract")).toBeNull();

    await click(mainPane().querySelector(".bi-copy"));
    expect(mainDuplicate).toHaveBeenCalled();
  });

  it("nests: focus, open an overview, focus again, then unwinds both levels and resyncs the bean", async () => {
    childListMock.mockReset().mockResolvedValue({ data: [{ id: "9", name: "Child row" }], totalCount: 1, limit: 20, offset: 0 });
    childGetMock.mockReset().mockResolvedValue({ id: "9", name: "Child row" });
    const setOverview = vi.fn();
    const overviewDuplicate = vi.fn();
    act(() => {
      root.render(
        <App
          options={baseOptions({
            overviewEntityType: "fake-parent-with-child-tab",
            overviewEntityId: "1",
            actions: { setOverview },
            overviewActions: { duplicate: overviewDuplicate, closeOverview: vi.fn() },
          })}
        />,
      );
    });
    await flush();

    await click(container.querySelector(".panel-splitter-panel-r .bi-arrows-angle-expand"));

    // Open a child of the promoted entity in the overview, from the main pane's own relation tab.
    const childrenTab = Array.from(mainPane().querySelectorAll(".p-tabview-nav li a")).find((el) =>
      el.textContent?.includes("Children"),
    );
    await click(childrenTab);
    await click(mainPane().querySelector(".entity-list-panel-identifier-link"));
    await flush();
    expect(setOverview).toHaveBeenLastCalledWith("fake-child-entity", "9");
    act(() => {
      window.dispatchEvent(new CustomEvent("siamois-set-overview-done", { detail: {} }));
    });
    // parentOrOverview is the child now — the bridged actions no longer act for the main entity.
    expect(mainPane().querySelector(".bi-copy")).toBeNull();
    // The back= still travels with the URL while the promoted entity stays on screen.
    expect(window.location.search).toContain("back=");

    await click(container.querySelector(".panel-splitter-panel-r .bi-arrows-angle-expand"));
    expect(mainPane().textContent).toContain("Child detail of Child row");

    await click(mainPane().querySelector(".bi-arrows-angle-contract"));
    expect(mainPane().textContent).toContain("Detail of Row A");
    expect(container.querySelector(".panel-splitter-panel-r")?.textContent).toContain("Child detail of Child row");

    await click(mainPane().querySelector(".bi-arrows-angle-contract"));
    expect(mainPane().querySelector("tbody tr")).toBeTruthy();
    expect(container.querySelector(".panel-splitter-panel-r")?.textContent).toContain("Detail of Row A");
    expect(window.location.search).toBe(`?s=${b64("/fake-parent-with-child-tab/1")}`);
    // The bean was moved to the child while in focus mode — put back on the restored overview.
    expect(setOverview).toHaveBeenLastCalledWith("fake-parent-with-child-tab", "1");
  });

  it("falls back to a real navigation to goBackUrl when the page itself was loaded in focus mode", async () => {
    act(() => {
      root.render(
        <App
          options={baseOptions({ panelKind: "detail", entityId: "1", goBackUrl: "/focus/previous?s=xyz" })}
        />,
      );
    });
    await flush();

    const originalLocation = window.location;
    Object.defineProperty(window, "location", { value: { href: "" }, writable: true });
    await click(mainPane().querySelector(".bi-arrows-angle-contract"));
    expect(window.location.href).toBe("/focus/previous?s=xyz");
    Object.defineProperty(window, "location", { value: originalLocation, writable: true });
  });

  it("has no closeFocus button outside focus mode", async () => {
    act(() => {
      root.render(<App options={baseOptions()} />);
    });
    await flush();
    expect(container.querySelector(".bi-arrows-angle-contract")).toBeNull();
  });
});
