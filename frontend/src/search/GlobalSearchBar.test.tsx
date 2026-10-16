import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig } from "../entities/types";
import { publishContext, registerNavigation } from "../navigation/externalBus";
import { GlobalSearchBar } from "./GlobalSearchBar";
import { fetchGlobalSearch, type SearchSection } from "./searchApi";

vi.mock("./searchApi", () => ({ fetchGlobalSearch: vi.fn() }));
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
  const input = container.querySelector("input")!;
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
  act(() => {
    container.querySelector("input")!.focus();
    container.querySelector("input")!.dispatchEvent(new FocusEvent("focus", { bubbles: true }));
  });
}

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockResolvedValue(sections);
  navigate.mockReset();
  openList.mockReset();
  unregister = registerNavigation({ navigate, openOverview: vi.fn(), openList });
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
  it("groups the results by entity type with their totals", async () => {
    render();
    type("mur");
    await settle();
    const titles = [...container.querySelectorAll(".sia-search-section-title")].map((e) => e.textContent);
    expect(titles).toEqual(["Unités d'enregistrement12", "Lieux1"]);
    expect(fetchMock).toHaveBeenCalledWith(
      expect.objectContaining({ organizationId: 7, q: "mur", filters: [], projectId: undefined, limit: 5 }),
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
    act(() => (container.querySelector(".sia-search-row:not(.sia-search-all)") as HTMLElement).click());
    expect(navigate).toHaveBeenCalledWith("recordingUnit", 1);
  });

  it("opens the list of a type with the search, from « Voir les N … »", async () => {
    render();
    type("mur");
    await settle();
    act(() => (container.querySelector(".sia-search-all") as HTMLElement).click());
    expect(openList).toHaveBeenCalledWith("recordingUnit", { search: "mur", projectId: undefined });
  });

  it("restricts to the current project until the chip is taken off", async () => {
    publishContext({ organizationId: 7, projectId: "9", projectLabel: "Fouille" });
    render();
    type("mur");
    await settle();
    expect(fetchMock).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: "9" }), expect.anything());
    act(() => (container.querySelector(".sia-search-chip-toggle") as HTMLElement).click());
    await settle();
    expect(fetchMock).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: undefined }), expect.anything());
  });

  it("an explicit projet: keyword replaces the implicit project", async () => {
    publishContext({ organizationId: 7, projectId: "9", projectLabel: "Fouille" });
    render();
    type("projet:X mur");
    await settle();
    expect(container.querySelector(".sia-search-chip")).toBeNull();
    expect(fetchMock).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: undefined }), expect.anything());
  });

  it("Enter with nothing highlighted views all of the first section", async () => {
    render();
    type("mur");
    await settle();
    act(() => {
      container.querySelector("input")!.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    expect(openList).toHaveBeenCalledWith("recordingUnit", expect.objectContaining({ search: "mur" }));
  });

  it("arrow keys move through the rows, Enter opens the highlighted one", async () => {
    render();
    type("mur");
    await settle();
    const key = (k: string) =>
      act(() => {
        container.querySelector("input")!.dispatchEvent(new KeyboardEvent("keydown", { key: k, bubbles: true }));
      });
    key("ArrowDown");
    key("Enter");
    expect(navigate).toHaveBeenCalledWith("recordingUnit", 1);
  });
});
