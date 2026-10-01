import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { PagedResult } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";
import { SEARCH_DEBOUNCE_MS } from "./list/ListSearchBox";
import { WriteModeProvider } from "./writeMode";

// The list's own guarantees that are easy to lose in a refactor: what a keystroke re-renders, how many
// requests a column change makes, and the PrimeReact behaviours the table works around.

interface Row {
  id: string;
  name: string;
  answers?: Record<string, unknown>;
}

const listMock = vi.fn<(params: unknown) => Promise<PagedResult<Row>>>();
const loadMock = vi.fn();
const nameCellRender = vi.fn((row: Row) => row.name);

registerEntityType({
  key: "workaround-entity",
  labels: { singular: "W", plural: "Ws" },
  collectionPath: "workaround-entities",
  icon: "bi bi-question",
  api: { list: listMock, get: vi.fn() },
  list: {
    columns: [
      { key: "name", header: "Name", render: nameCellRender, identifier: true },
      { key: "after", header: "Après", render: () => "y" },
    ],
    schema: { load: loadMock },
    searchable: true,
  },
  detail: { tabs: [] },
  routes: { list: "/w", detail: (id) => `/w/${id}` },
});

const catalog = {
  fields: {
    "-1": { id: "-1", resourceType: "fields", label: "Un", answerType: "TEXT", isSystemField: false },
    "-2": { id: "-2", resourceType: "fields", label: "Deux", answerType: "TEXT", isSystemField: false },
    "-3": { id: "-3", resourceType: "fields", label: "Trois", answerType: "TEXT", isSystemField: false },
  },
  columns: [
    { fieldId: "-1", visible: true, order: 0 },
    { fieldId: "-2", visible: false, order: 1 },
    { fieldId: "-3", visible: false, order: 2 },
  ],
};

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function click(el: Element) {
  await act(async () => {
    el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await flush();
}

function renderPanel() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={true}>
          <EntityListPanel entityType="workaround-entity" />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
}

beforeEach(() => {
  window.localStorage.clear();
  listMock.mockReset();
  listMock.mockImplementation(async (params) => {
    const fields = ((params as { fields?: string }).fields ?? "").split(",").filter(Boolean);
    const answers = Object.fromEntries(fields.map((f) => [f, `v${f}`]));
    return { data: [{ id: "1", name: "Row A", answers }], totalCount: 1, limit: 20, offset: 0 };
  });
  loadMock.mockReset();
  loadMock.mockResolvedValue(catalog);
  nameCellRender.mockClear();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("EntityListPanel: the search box", () => {
  it("re-renders only itself while typing — the table's cells are not rendered again", async () => {
    renderPanel();
    await flush();
    await flush();
    const input = container.querySelector<HTMLInputElement>(".entity-list-panel-toolbar input")!;
    const before = nameCellRender.mock.calls.length;

    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
    for (const typed of ["a", "ab", "abc"]) {
      await act(async () => {
        setter.call(input, typed);
        input.dispatchEvent(new Event("input", { bubbles: true }));
      });
    }

    expect(input.value).toBe("abc");
    expect(nameCellRender.mock.calls.length).toBe(before);
    // The one request comes once typing pauses, carrying the whole word.
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, SEARCH_DEBOUNCE_MS + 50));
    });
    await flush();
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ search: "abc", offset: 0 }));
    // One request for the whole word, none for its prefixes.
    expect(listMock.mock.calls.filter(([p]) => (p as { search?: string }).search)).toHaveLength(1);
  });
});

describe("EntityListPanel: column supplements", () => {
  async function openColumnChooser() {
    await click(container.querySelector(".entity-list-panel-gear-button")!);
    const entry = Array.from(document.body.querySelectorAll(".entity-list-panel-gear-menu .p-menuitem-link")).find((a) =>
      a.textContent?.includes("Colonnes"),
    )!;
    await click(entry);
  }

  function hiddenSwitch(label: string) {
    const item = Array.from(
      document.body.querySelectorAll('.entity-list-panel-column-toggler [data-section="hidden"] .visibility-chooser-item'),
    ).find((li) => li.textContent?.includes(label))!;
    return item.querySelector(".p-inputswitch input")!;
  }

  it("asks for all the columns shown since the page was loaded in a single request", async () => {
    renderPanel();
    await flush();
    await flush();
    expect(listMock).toHaveBeenCalledTimes(1);

    await openColumnChooser();
    await click(hiddenSwitch("Deux"));
    expect(listMock).toHaveBeenCalledTimes(2);
    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ fields: "-2" }));

    await click(hiddenSwitch("Trois"));
    // One more request carrying both — not one request per column.
    expect(listMock).toHaveBeenCalledTimes(3);
    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ fields: "-2,-3" }));
    expect(container.textContent).toContain("v-2");
    expect(container.textContent).toContain("v-3");
    // The first column's value, fetched with the page, is still there.
    expect(container.textContent).toContain("v-1");
  });
});

describe("EntityListPanel: PrimeReact workarounds", () => {
  it("swallows a drag dropped on a frozen header, so the frozen block stays closed", async () => {
    renderPanel();
    await flush();
    await flush();
    const headers = Array.from(container.querySelectorAll<HTMLElement>(".p-datatable-thead > tr > th"));
    const frozen = headers.find((th) => th.classList.contains("entity-list-panel-frozen") && th.textContent?.includes("Name"))!;
    const scrolling = headers.find((th) => th.textContent?.includes("Après"))!;
    const reached = vi.fn();
    document.body.addEventListener("drop", reached);
    document.body.addEventListener("dragover", reached);

    try {
      await act(async () => {
        frozen.dispatchEvent(new Event("dragover", { bubbles: true }));
        frozen.dispatchEvent(new Event("drop", { bubbles: true }));
      });
      expect(reached).not.toHaveBeenCalled();

      await act(async () => {
        scrolling.dispatchEvent(new Event("dragover", { bubbles: true }));
      });
      expect(reached).toHaveBeenCalledTimes(1);
    } finally {
      document.body.removeEventListener("drop", reached);
      document.body.removeEventListener("dragover", reached);
    }
  });

  it("keeps the chooser's order as the table's after PrimeReact was told about another one", async () => {
    window.localStorage.setItem(
      "siamois.list.org-.workaround-entity.global",
      JSON.stringify({ v: 1, visibleColumns: ["-2", "-1"] }),
    );
    renderPanel();
    await flush();
    await flush();

    const labels = () =>
      Array.from(container.querySelectorAll(".entity-list-panel-column-header")).map((h) => h.textContent);
    expect(labels().indexOf("Deux")).toBeLessThan(labels().indexOf("Un"));
  });
});
