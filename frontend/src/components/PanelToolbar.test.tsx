import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { apiFetch } from "../api/client";
import { PanelToolbar } from "./PanelToolbar";
import type { OverviewActions, PanelChrome } from "../mountOptions";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

function render(chrome: PanelChrome, organizationId?: number, actions?: OverviewActions) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <PanelToolbar chrome={chrome} organizationId={organizationId} actions={actions} />
      </QueryClientProvider>,
    );
  });
}

beforeEach(() => {
  mockedApiFetch.mockReset();
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

function button(icon: string): HTMLElement {
  return container.querySelector(`.${icon.split(" ").join(".")}`)!.closest("button")!;
}

describe("PanelToolbar", () => {
  it("only renders action buttons that were actually provided", () => {
    render({ resourceUri: "/action-unit/1", title: "Fouille A", bookmarked: false }, 7, { refresh: vi.fn() });

    expect(container.querySelector(".bi-arrow-clockwise")).toBeTruthy();
    expect(container.querySelector(".bi-plus-square")).toBeNull();
    expect(container.querySelector(".bi-copy")).toBeNull();
    expect(container.querySelector(".bi-gear")).toBeNull();
    expect(container.querySelector(".bi-chevron-double-right")).toBeNull();
    expect(container.querySelector(".bi-arrows-angle-expand")).toBeNull();
  });

  it("renders closeOverview/fullscreen only for the overview toolbar", () => {
    render({ resourceUri: "/action-unit/1", title: "Fouille A", bookmarked: false }, 7, {
      closeOverview: vi.fn(),
      fullscreen: vi.fn(),
    });

    expect(container.querySelector(".bi-chevron-double-right")).toBeTruthy();
    expect(container.querySelector(".bi-arrows-angle-expand")).toBeTruthy();
  });

  it("calls the provided action callbacks", () => {
    const refresh = vi.fn();
    const closeOverview = vi.fn();
    render({ resourceUri: "/action-unit/1", title: "Fouille A", bookmarked: false }, 7, {
      refresh,
      closeOverview,
    });

    act(() => {
      button("bi-arrow-clockwise").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(refresh).toHaveBeenCalledTimes(1);

    act(() => {
      button("bi-chevron-double-right").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(closeOverview).toHaveBeenCalledTimes(1);
  });

  it("creates a bookmark via REST and flips the icon when not yet bookmarked", async () => {
    mockedApiFetch.mockResolvedValue(undefined);
    render({ resourceUri: "/action-unit/1", title: "Fouille A", bookmarked: false }, 7);

    expect(container.querySelector(".bi-bookmark")).toBeTruthy();
    expect(container.querySelector(".bi-bookmark-fill")).toBeNull();

    const bookmarkButton = container.querySelector(".bi-bookmark")!.closest("button")!;
    await act(async () => {
      bookmarkButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bookmarks", {
      method: "POST",
      body: { resourceUri: "/action-unit/1", titleCode: "Fouille A", organizationId: 7 },
    });
    expect(container.querySelector(".bi-bookmark-fill")).toBeTruthy();
  });

  it("deletes a bookmark via REST when already bookmarked", async () => {
    mockedApiFetch.mockResolvedValue(undefined);
    render({ resourceUri: "/action-unit/1", title: "Fouille A", bookmarked: true }, 7);

    const bookmarkButton = container.querySelector(".bi-bookmark-fill")!.closest("button")!;
    await act(async () => {
      bookmarkButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedApiFetch).toHaveBeenCalledWith(
      "/api/v1/bookmarks?resourceUri=%2Faction-unit%2F1&organizationId=7",
      { method: "DELETE" },
    );
    expect(container.querySelector(".bi-bookmark")).toBeTruthy();
  });

  it("disables the bookmark button when organizationId is unknown", () => {
    render({ resourceUri: "/action-unit/1", title: "Fouille A", bookmarked: false }, undefined);

    const bookmarkButton = container.querySelector(".bi-bookmark")!.closest("button") as HTMLButtonElement;
    expect(bookmarkButton.disabled).toBe(true);
  });
});
