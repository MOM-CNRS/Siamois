import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {apiFetch} from "../../api/client";
import {configureBasePath} from "../../api/basePath";
import {projectHomeWidgets} from "./homeWidgets";


vi.mock("../../api/client", () => ({
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

function renderWidget(index: 0 | 1, onNavigate = vi.fn()) {
  const widgets = projectHomeWidgets({ organizationId: 7, onNavigate });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(<QueryClientProvider client={queryClient}>{widgets[index].render()}</QueryClientProvider>);
  });
  return onNavigate;
}

beforeEach(() => {
  mockedApiFetch.mockReset();
  configureBasePath("/siamois");
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
  configureBasePath("");
});

describe("projectHomeWidgets", () => {
  it("requests projects sorted by creationTime desc, scoped to the organization", async () => {
    mockedApiFetch.mockResolvedValue({ data: [], meta: { total: 0, limit: 5, offset: 0 } });
    renderWidget(0);
    await flush();

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("sort=creationTime%3Adesc");
    expect(path).toContain("organizationId=7");
    expect(path).toContain("limit=5");
  });

  it("recent-projects widget shows an empty state when there are none", async () => {
    mockedApiFetch.mockResolvedValue({ data: [], meta: { total: 0, limit: 5, offset: 0 } });
    renderWidget(0);
    await flush();

    expect(container.textContent).toContain("Aucun projet");
  });

  it("recent-projects card is itself the link — no button, clicking the card opens the project", async () => {
    mockedApiFetch.mockResolvedValue({
      data: [{ resourceType: "projects", id: "9", name: "Fouille A", fullIdentifier: "FA-1", identifier: "FA-1" }],
      meta: { total: 1, limit: 5, offset: 0 },
    });
    const onNavigate = renderWidget(0);
    await flush();

    // The Panel's own collapse toggle is a button; the card itself must not contain one.
    const card = container.querySelector('[role="link"]')!;
    expect(card.querySelector("button")).toBeNull();
    expect(container.textContent).not.toContain("Ouvrir le projet");
    await act(async () => {
      card.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith("project", "9");
  });

  function answer(canCreateProjects: boolean) {
    mockedApiFetch.mockImplementation(async (path: string) =>
      path.endsWith("/permissions")
        ? { data: { canCreateProjects } }
        : { data: [], meta: { total: 0, limit: 5, offset: 0 } },
    );
  }

  const buttons = () => Array.from(container.querySelectorAll("button")).map((b) => b.textContent);

  it("has « Voir tous les projets » and, to who may create projects, « Créer un projet » in the section's header", async () => {
    answer(true);
    renderWidget(0);
    await flush();

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/permissions");
    expect(buttons()).toEqual(expect.arrayContaining(["Voir tous les projets", "Créer un projet"]));
  });

  it("« Voir tous les projets » opens the projects list", async () => {
    answer(false);
    const onNavigate = renderWidget(0);
    await flush();

    const button = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Voir tous les projets")!;
    await act(async () => button.click());

    expect(onNavigate).toHaveBeenCalledWith("project");
  });

  it("« Créer un projet » opens the creation overlay", async () => {
    answer(true);
    renderWidget(0);
    await flush();

    const button = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer un projet")!;
    await act(async () => button.click());
    await flush();

    expect(document.querySelector(".entity-list-panel-create-overlay")).not.toBeNull();
  });

  it("offers no « Créer un projet » to who may not create projects", async () => {
    answer(false);
    renderWidget(0);
    await flush();

    expect(buttons()).not.toContain("Créer un projet");
    expect(container.textContent).toContain("Aucun projet");
  });

  it("count card reads counts.projects from the organization counts endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { projects: 12, places: 0, recordingUnits: 0, finds: 0, phases: 0, containers: 0 } });
    renderWidget(1);
    await flush();

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/counts");
    expect(container.textContent).toContain("12");
  });

  it("count card has no button and asks the router for the React list on click (no page navigation)", async () => {
    mockedApiFetch.mockResolvedValue({ data: { projects: 12, places: 0, recordingUnits: 0, finds: 0, phases: 0, containers: 0 } });
    const onNavigate = renderWidget(1);
    await flush();

    expect(container.querySelector("button")).toBeNull();
    await act(async () => {
      container.querySelector('[role="link"]')!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith("project");
  });

  it("declares the count card first in homePanel.xhtml's order", () => {
    const card = projectHomeWidgets({ organizationId: 7 }).find((w) => w.kind === "card")!;
    expect(card.order).toBe(10);
  });
});
