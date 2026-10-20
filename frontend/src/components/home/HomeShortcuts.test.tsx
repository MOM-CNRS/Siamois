import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { apiFetch } from "../../api/client";
import { configureBasePath } from "../../api/basePath";
import { registerEntityType } from "../../entities/registry";
import type { EntityTypeConfig } from "../../entities/types";
import { homeShortcutWidgets } from "./HomeShortcuts";
import { openSidebarDrawer } from "./sidebar";

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

describe("home history", () => {
  it("shows the title of the overview and reopens the page with it", async () => {
    mockedApiFetch.mockResolvedValue({
      data: [
        {
          id: 1,
          title: "US 9",
          icon: "bi bi-pencil-square",
          mainUri: "/action-unit/2",
          mainTitle: "Projet A",
          secondaryUri: "/recording-unit/9",
          secondaryTitle: "US 9",
        },
      ],
    });
    const ctx = { organizationId: 7, onNavigate: vi.fn(), onOpenOverview: vi.fn() };
    render(ctx);
    await flush();

    expect(String(mockedApiFetch.mock.calls[0][0])).toContain("/api/v1/history?organizationId=7&limit=20");
    const row = container.querySelector<HTMLButtonElement>(".sia-clickable-card")!;
    expect(row.textContent).toContain("US 9");
    expect(row.textContent).toContain("Projet A");

    act(() => row.click());
    expect(ctx.onNavigate).toHaveBeenCalledWith("project", "2");
    expect(ctx.onOpenOverview).toHaveBeenCalledWith("recordingUnit", "9");
  });

  it("opens a list entry without an overview", async () => {
    mockedApiFetch.mockResolvedValue({ data: [{ id: 1, title: "Projets", mainUri: "/action-unit", mainTitle: "Projets" }] });
    const ctx = { organizationId: 7, onNavigate: vi.fn(), onOpenOverview: vi.fn() };
    render(ctx);
    await flush();

    act(() => container.querySelector<HTMLElement>(".sia-clickable-card")!.click());

    expect(ctx.onNavigate).toHaveBeenCalledWith("project");
    expect(ctx.onOpenOverview).not.toHaveBeenCalled();
  });

  it("leaves the home page out and shows each card in the colour and icon of its type", async () => {
    mockedApiFetch.mockResolvedValue({
      data: [
        { id: 1, title: "Accueil", mainUri: "/home", mainTitle: "Accueil" },
        { id: 4, title: "Ailleurs", mainUri: "/nope/1", mainTitle: "Ailleurs" },
        { id: 2, title: "Projet A", mainUri: "/action-unit/2", mainTitle: "Projet A" },
        { id: 3, title: "US 9", mainUri: "/recording-unit/9", mainTitle: "US 9" },
      ],
    });
    render({ organizationId: 7 });
    await flush();

    const cards = container.querySelectorAll<HTMLElement>(".sia-clickable-card");
    expect(cards).toHaveLength(2);
    expect(cards[0].querySelector(".sia-welcome-card")?.className).toContain("sia-action-unit");
    expect(cards[0].querySelector("i")?.className).toBe("bi bi-arrow-down-square");
    expect(cards[1].querySelector(".sia-welcome-card")?.className).toContain("sia-recording-unit");
    expect(cards[1].querySelector("i")?.className).toBe("bi bi-pencil-square");
  });

  it("keeps a fiche opened beside a pane the server still knows as Home, and reopens just that fiche", async () => {
    mockedApiFetch.mockResolvedValue({
      data: [{ id: 1, title: "US 9", mainUri: "/home", mainTitle: "Accueil", secondaryUri: "/recording-unit/9", secondaryTitle: "US 9" }],
    });
    const ctx = { organizationId: 7, onNavigate: vi.fn(), onOpenOverview: vi.fn() };
    render(ctx);
    await flush();

    act(() => container.querySelector<HTMLElement>(".sia-clickable-card")!.click());

    expect(ctx.onNavigate).not.toHaveBeenCalled();
    expect(ctx.onOpenOverview).toHaveBeenCalledWith("recordingUnit", "9");
  });

  it("says when there is no recent page", async () => {
    mockedApiFetch.mockResolvedValue({ data: [] });
    render({ organizationId: 7 });
    await flush();

    expect(container.textContent).toContain("Aucune page récente");
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

  it("does nothing without a JSF sidebar", () => {
    expect(() => openSidebarDrawer("historique")).not.toThrow();
  });
});
