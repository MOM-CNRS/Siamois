import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig } from "../entities/shared/types";
import { publishContext, registerNavigation } from "../navigation/externalBus";
import { GlobalSearchBar } from "./GlobalSearchBar";
import { listProjects } from "../entities/project/api";
import { fetchGlobalSearch, type SearchSection } from "./searchApi";

vi.mock("./searchApi", () => ({ fetchGlobalSearch: vi.fn() }));
vi.mock("../entities/project/api", () => ({ listProjects: vi.fn() }));
const listProjectsMock = vi.mocked(listProjects);
const fetchMock = vi.mocked(fetchGlobalSearch);

function fake(key: string, plural: string): EntityTypeConfig<unknown, unknown> {
  return {
    key,
    labels: { singular: plural, plural },
    collectionPath: key,
    icon: "bi bi-x",
    api: { list: vi.fn(), get: vi.fn() },
    list: { columns: [] },
    detail: { tabs: [] },
    routes: { list: `/${key}`, detail: (id: string | number) => `/${key}/${id}` },
  } as unknown as EntityTypeConfig<unknown, unknown>;
}
registerEntityType(fake("recordingUnit", "Unités d'enregistrement"));
registerEntityType(fake("place", "Lieux"));

const sections: SearchSection[] = [
  { type: "recordingUnit", total: 12, items: [{ id: 1, label: "UE-1", subtitle: "P1", projectId: 9 }] },
  { type: "place", total: 1, items: [{ id: 2, label: "Abbaye" }] },
];

let container: HTMLDivElement;
let root: Root;
const navigate = vi.fn();
const openList = vi.fn();
let unregister: () => void;

// Several short acts, not one long one: React holds the updates queued inside an act until it ends,
// and this chain (debounce → query enabled → fetch → data) needs a render between each of its links.
async function settle(ms = 700) {
  for (let elapsed = 0; elapsed < ms; elapsed += 50) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
  }
}

function type(value: string) {
  const input = document.body.querySelector("input")!;
  act(() => {
    // React tracks the value setter: go through the prototype's, as a real keystroke does.
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, value);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

function render() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <GlobalSearchBar organizationId={7} />
      </QueryClientProvider>,
    );
  });
  // The topbar only shows a button: the search happens in the overlay it opens.
  act(() => (container.querySelector(".sia-search-trigger") as HTMLElement).click());
}

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockResolvedValue(sections);
  listProjectsMock.mockReset();
  listProjectsMock.mockResolvedValue({
    data: [{ id: "4", fullIdentifier: "FOUILLE-4", name: "Abbaye" }],
    totalCount: 1,
    limit: 5,
    offset: 0,
  } as never);
  navigate.mockReset();
  openList.mockReset();
  unregister = registerNavigation({ navigate, openOverview: vi.fn(), openList, restoreUrl: vi.fn() });
  publishContext({});
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  unregister();
});

describe("GlobalSearchBar", () => {
  it("is a button in the topbar that opens an overlay with the field focused", () => {
    render();
    expect(container.querySelector("input")).toBeNull();
    expect(document.body.querySelector(".sia-search-backdrop")).not.toBeNull();
    expect(document.activeElement).toBe(document.body.querySelector("input"));
  });

  it("offers the home page's recent projects while nothing is typed, and opens the chosen one", async () => {
    render();
    await settle(200);
    const title = document.body.querySelector(".sia-search-section-title")!.textContent;
    expect(title).toBe("Projets récents");
    expect(document.body.querySelector(".sia-search-row")!.textContent).toBe("FOUILLE-4Abbaye");
    act(() => (document.body.querySelector(".sia-search-row") as HTMLElement).click());
    expect(navigate).toHaveBeenCalledWith("project", 4);
    expect(document.body.textContent).not.toContain("Mots-clés");
  });

  it("closes on Escape or a click outside the panel, and keeps what was typed for the next opening", async () => {
    render();
    type("mur");
    act(() => {
      document.body.querySelector("input")!.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });
    expect(document.body.querySelector(".sia-search-backdrop")).toBeNull();
    act(() => (container.querySelector(".sia-search-trigger") as HTMLElement).click());
    expect(document.body.querySelector("input")!.value).toBe("mur");
    act(() => {
      document.body.querySelector(".sia-search-backdrop")!.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    expect(document.body.querySelector(".sia-search-backdrop")).toBeNull();
  });

  it("opens with the / key outside a text field", () => {
    render();
    act(() => {
      document.body.querySelector("input")!.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });
    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "/", bubbles: true }));
    });
    expect(document.body.querySelector(".sia-search-backdrop")).not.toBeNull();
  });

  it("groups the results by entity type with their totals", async () => {
    render();
    type("mur");
    await settle();
    const titles = [...document.body.querySelectorAll(".sia-search-section-title")].map((e) => e.textContent);
    expect(titles).toEqual(["Unités d'enregistrement", "Lieux"]);
    expect(fetchMock).toHaveBeenCalledWith(
      expect.objectContaining({ organizationId: 7, q: "mur", filters: [], limit: 5 }),
      expect.anything(),
    );
  });

  it("sends keyword filters apart from the free text", async () => {
    render();
    type("projet:ABC mur");
    await settle();
    expect(fetchMock).toHaveBeenCalledWith(
      expect.objectContaining({ q: "mur", filters: [{ key: "projet", value: "ABC" }] }),
      expect.anything(),
    );
  });

  it("opens the fiche of a clicked item in the main pane", async () => {
    render();
    type("ue");
    await settle();
    act(() => (document.body.querySelector(".sia-search-row:not(.sia-search-all)") as HTMLElement).click());
    expect(navigate).toHaveBeenCalledWith("recordingUnit", 1);
  });

  it("opens the list of a type with the search, from « Voir les N … »", async () => {
    render();
    type("mur");
    await settle();
    act(() => (document.body.querySelector(".sia-search-all") as HTMLElement).click());
    expect(openList).toHaveBeenCalledWith("recordingUnit", { search: "mur", projectId: undefined });
  });

  it("offers the current project as a projet: keyword in the input, which then filters the search", async () => {
    publishContext({ organizationId: 7, projectId: "9", projectLabel: "Mon nom" });
    render();
    await settle(100);
    expect(document.body.querySelector("input")!.value).toBe('projet:"Mon nom" ');
    type('projet:"Mon nom" mur');
    await settle();
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "mur", filters: [{ key: "projet", value: "Mon nom" }] }),
      expect.anything(),
    );
  });

  it("swaps the offered keyword when the project changes, and drops it when there is none", async () => {
    publishContext({ organizationId: 7, projectId: "9", projectLabel: "A" });
    render();
    await settle(100);
    act(() => publishContext({ organizationId: 7, projectId: "10", projectLabel: "B" }));
    await settle(100);
    expect(document.body.querySelector("input")!.value).toBe("projet:B ");
    act(() => publishContext({ organizationId: 7 }));
    await settle(100);
    expect(document.body.querySelector("input")!.value).toBe("");
  });

  it("does not search on the offered keyword alone", async () => {
    publishContext({ organizationId: 7, projectId: "9", projectLabel: "A" });
    render();
    await settle();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("restricts « Voir tout » to the current project when the keyword names it", async () => {
    publishContext({ organizationId: 7, projectId: "9", projectLabel: "A" });
    render();
    await settle(100);
    type("projet:A mur");
    await settle();
    act(() => (document.body.querySelector(".sia-search-all") as HTMLElement).click());
    expect(openList).toHaveBeenCalledWith("recordingUnit", { search: "mur", projectId: "9" });
  });

  it("suggests projects while a projet: keyword is typed, and a click completes it", async () => {
    fetchMock.mockImplementation(async (params) =>
      params.types?.includes("project")
        ? [{ type: "project", total: 1, items: [{ id: 9, label: "FOUILLE-1", subtitle: "Fouille de l'abbaye" }] }]
        : sections,
    );
    render();
    type("mur projet:abb");
    await settle();
    expect(fetchMock).toHaveBeenCalledWith(expect.objectContaining({ q: "abb", types: ["project"] }), expect.anything());
    // The keyword being typed is not a filter yet, and the results of the words wait for it.
    expect(fetchMock).not.toHaveBeenCalledWith(expect.objectContaining({ q: "mur" }), expect.anything());
    expect(document.body.querySelector(".sia-search-autocomplete")!.textContent).toBe("Autocomplétion");
    act(() => (document.body.querySelector(".sia-search-row") as HTMLElement).click());
    expect(document.body.querySelector("input")!.value).toBe("mur projet:FOUILLE-1 ");
    await settle();
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "mur", filters: [{ key: "projet", value: "FOUILLE-1" }] }),
      expect.anything(),
    );
  });

  it("Enter on the suggestions picks the first one", async () => {
    fetchMock.mockResolvedValue([{ type: "project", total: 1, items: [{ id: 9, label: "P 1", subtitle: "x" }] }]);
    render();
    type("projet:p");
    await settle();
    act(() => {
      document.body.querySelector("input")!.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    expect(document.body.querySelector("input")!.value).toBe('projet:"P 1" ');
  });

  it("Enter with nothing highlighted views all of the first section", async () => {
    render();
    type("mur");
    await settle();
    act(() => {
      document.body.querySelector("input")!.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    expect(openList).toHaveBeenCalledWith("recordingUnit", expect.objectContaining({ search: "mur" }));
  });

  it("arrow keys move through the rows, Enter opens the highlighted one", async () => {
    render();
    type("mur");
    await settle();
    const key = (k: string) =>
      act(() => {
        document.body.querySelector("input")!.dispatchEvent(new KeyboardEvent("keydown", { key: k, bubbles: true }));
      });
    key("ArrowDown");
    key("Enter");
    expect(navigate).toHaveBeenCalledWith("recordingUnit", 1);
  });
});
