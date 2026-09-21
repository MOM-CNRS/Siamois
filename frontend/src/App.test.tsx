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
    icon: "bi bi-question",
  api: { list: listMock, get: getMock },
  list: {
    columns: [{ key: "name", header: "Name", render: (row) => row.name }],
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
  it("switches from list to detail on row click without a page navigation", async () => {
    const originalHref = window.location.href;
    act(() => {
      root.render(<App options={baseOptions()} />);
    });
    await flush();

    const row = container.querySelector("tbody tr") as HTMLElement;
    expect(row).toBeTruthy();

    await act(async () => {
      row.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    // Content actually switched to the detail panel...
    expect(container.textContent).toContain("Detail of Row A");
    // ...and the URL changed via history, not an actual browser navigation.
    expect(window.location.pathname).toBe("/fake-app-entity/1");
    expect(window.location.href).not.toBe(originalHref);
    expect(getMock).toHaveBeenCalledWith("1");
  });

  it("hides the main toolbar entirely after navigating away (it was built for the original entity only)", async () => {
    act(() => {
      root.render(<App options={baseOptions()} />);
    });
    await flush();

    expect(container.querySelector(".bi-arrow-clockwise")).toBeTruthy();
    expect(container.querySelector(".bi-bookmark")).toBeTruthy();

    const row = container.querySelector("tbody tr") as HTMLElement;
    await act(async () => {
      row.dispatchEvent(new MouseEvent("click", { bubbles: true }));
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
});
