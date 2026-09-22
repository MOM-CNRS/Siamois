import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { App } from "./App";
import { registerEntityType } from "./entities/registry";
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

function baseOptions(overrides: Partial<MountOptions> = {}): MountOptions {
  return {
    panelKind: "list",
    entityType: "fake-app-entity",
    basePath: "",
    csrf: { headerName: "X-CSRF", token: "t" },
    main: { resourceUri: "/fake-app-entity", title: "Fakes", bookmarked: false },
    actions: { refresh: vi.fn(), duplicate: vi.fn(), create: vi.fn(), settings: vi.fn() },
    ...overrides,
  };
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

    expect(container.querySelector(".bi-arrow-clockwise")).toBeTruthy();

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelector(".bi-arrow-clockwise")).toBeTruthy();
  });

  it("hides the main toolbar entirely after a full client-side navigation (it was built for the original entity only)", async () => {
    act(() => {
      root.render(<App options={baseOptions({ panelKind: "home", entityType: "fake-app-entity" })} />);
    });
    await flush();

    expect(container.querySelector(".bi-arrow-clockwise")).toBeTruthy();
    expect(container.querySelector(".bi-bookmark")).toBeTruthy();

    const button = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Go to detail")!;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelector(".bi-arrow-clockwise")).toBeNull();
    expect(container.querySelector(".bi-bookmark")).toBeNull();
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
});
