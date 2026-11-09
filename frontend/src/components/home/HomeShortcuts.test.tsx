import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {apiFetch} from "../../api/client";
import {configureBasePath} from "../../api/basePath";
import {registerEntityType} from "../../entities/registry";
import type {EntityTypeConfig} from "../../entities/shared/types";
import {homeShortcutWidgets} from "./HomeShortcuts";
import {openSidebarDrawer} from "./sidebar";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

const fake = (key: string, segment: string, icon: string) =>
  ({
    key,
    icon,
    routes: { list: `/${segment}`, detail: (id: string | number) => `/${segment}/${id}` },
  }) as unknown as EntityTypeConfig<unknown, unknown>;

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function render(ctx: Parameters<typeof homeShortcutWidgets>[0]) {
  const widgets = homeShortcutWidgets(ctx);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(<QueryClientProvider client={queryClient}>{widgets[0].render()}</QueryClientProvider>);
  });
}

beforeAll(() => {
  registerEntityType({ ...fake("project", "action-unit", "bi bi-arrow-down-square"), cardClassName: "sia-action-unit" } as never);
  registerEntityType({ ...fake("recordingUnit", "recording-unit", "bi bi-pencil-square"), cardClassName: "sia-recording-unit" } as never);
});

beforeEach(() => {
  mockedApiFetch.mockReset();
  configureBasePath("");
  document.body.innerHTML = "";
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
});

describe("pinned views", () => {
  it("lists the favorites that are a list or a view, in the colour and icon of their table, and opens them", async () => {
    mockedApiFetch.mockResolvedValue({
      data: [
        { id: 1, title: "UE – Couche", resourceUri: "/recording-unit?viewId=12" },
        { id: 2, title: "Projet A", resourceUri: "/action-unit/2" },
        { id: 3, title: "Projets", resourceUri: "/action-unit" },
        { id: 4, title: "Ailleurs", resourceUri: "/nope" },
      ],
    });
    const ctx = { organizationId: 7, onNavigate: vi.fn(), onOpenView: vi.fn() };
    render(ctx);
    await flush();

    expect(String(mockedApiFetch.mock.calls[0][0])).toContain("/api/v1/bookmarks?organizationId=7&limit=50");
    const cards = container.querySelectorAll<HTMLElement>(".sia-clickable-card");
    // A favorite fiche and a table the app does not know are not pinned views.
    expect(cards).toHaveLength(2);
    expect(cards[0].textContent).toContain("UE – Couche");
    expect(cards[0].querySelector(".sia-welcome-card")?.className).toContain("sia-recording-unit");
    expect(cards[0].querySelector("i")?.className).toBe("bi bi-pencil-square");

    act(() => cards[0].click());
    expect(ctx.onOpenView).toHaveBeenCalledWith("recordingUnit", 12);
    act(() => cards[1].click());
    expect(ctx.onNavigate).toHaveBeenCalledWith("project");
  });

  it("unpins a view without opening it", async () => {
    mockedApiFetch.mockResolvedValue({ data: [{ id: 1, title: "UE – Couche", resourceUri: "/recording-unit?viewId=12" }] });
    const ctx = { organizationId: 7, onNavigate: vi.fn(), onOpenView: vi.fn() };
    render(ctx);
    await flush();

    await act(async () => container.querySelector<HTMLElement>(".home-pinned-unpin")!.click());

    expect(String(mockedApiFetch.mock.calls.find(([, init]) => init?.method === "DELETE")![0])).toContain(
      "/api/v1/bookmarks?resourceUri=%2Frecording-unit%3FviewId%3D12&organizationId=7",
    );
    expect(ctx.onOpenView).not.toHaveBeenCalled();
  });

  it("says when nothing is pinned", async () => {
    mockedApiFetch.mockResolvedValue({ data: [] });
    render({ organizationId: 7 });
    await flush();

    expect(container.textContent).toContain("Aucune vue épinglée");
  });
});

describe("openSidebarDrawer", () => {
  function sidebar(classes: string) {
    const el = document.createElement("div");
    el.id = "subSidebarForm";
    el.className = classes;
    document.body.appendChild(el);
    const link = document.createElement("a");
    link.id = "navForm:historyLinkNav";
    const clicked = vi.fn();
    link.addEventListener("click", clicked);
    document.body.appendChild(link);
    return clicked;
  }

  it("clicks the sidebar entry when the drawer is closed or shows another one", () => {
    const closed = sidebar("mode-historique is-collapsed");
    openSidebarDrawer("historique");
    expect(closed).toHaveBeenCalledTimes(1);

    document.body.innerHTML = "";
    const other = sidebar("mode-bookmarks");
    openSidebarDrawer("historique");
    expect(other).toHaveBeenCalledTimes(1);
  });

  it("leaves a drawer that is already showing alone (the entry would close it)", () => {
    const clicked = sidebar("mode-historique");
    openSidebarDrawer("historique");
    expect(clicked).not.toHaveBeenCalled();
  });

  it("opens the views drawer through the views entry", () => {
    const el = document.createElement("div");
    el.id = "subSidebarForm";
    el.className = "mode-historique";
    document.body.appendChild(el);
    const link = document.createElement("a");
    link.id = "navForm:viewsLinkNav";
    const clicked = vi.fn();
    link.addEventListener("click", clicked);
    document.body.appendChild(link);

    openSidebarDrawer("views");

    expect(clicked).toHaveBeenCalledTimes(1);
  });

  it("opens the favorites drawer through the bookmarks entry", () => {
    const el = document.createElement("div");
    el.id = "subSidebarForm";
    el.className = "mode-historique";
    document.body.appendChild(el);
    const link = document.createElement("a");
    link.id = "navForm:bookmarksLinkNav";
    const clicked = vi.fn();
    link.addEventListener("click", clicked);
    document.body.appendChild(link);

    openSidebarDrawer("bookmarks");

    expect(clicked).toHaveBeenCalledTimes(1);
  });

  it("does nothing without a JSF sidebar", () => {
    expect(() => openSidebarDrawer("historique")).not.toThrow();
  });
});
