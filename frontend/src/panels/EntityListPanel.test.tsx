import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import { DEFAULT_LIMIT } from "./tableState";
import { registerDefaultFieldRenderers } from "../fields/registerDefaultRenderers";

registerDefaultFieldRenderers();
import type { EntityTypeConfig, PagedResult } from "../entities/types";
import { recallListContext } from "./listContext";
import { EntityListPanel } from "./EntityListPanel";
import { WriteModeProvider } from "./writeMode";
import { searchCreatableProjects } from "../entities/project/api";

vi.mock("../entities/project/api", () => ({ searchCreatableProjects: vi.fn() }));
const mockedSearchCreatableProjects = vi.mocked(searchCreatableProjects);

// Some PrimeReact internals (ripple, resize listeners) schedule state updates outside any act()
// call this file makes; this flag is React 18's own escape hatch for that noise and doesn't
// affect what the assertions below actually verify.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Exercises EntityListPanel as the generic component it is (plan §3/§8 phase 5) — registered
// against a throwaway fake entity type, not Project, so this stays a test of the mechanism
// (params built from search/sort/pagination state, row click → onNavigate) rather than of
// entities/project/config.tsx, which already has its own coverage (api.test.ts, columns.test.tsx).

interface FakeRow {
  id: string;
  name: string;
}

const listMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();

const fakeConfig: EntityTypeConfig<FakeRow, FakeRow> = {
  key: "fake-entity",
  labels: { singular: "Fake", plural: "Fakes" },
  collectionPath: "fake-entities",
  icon: "bi bi-question",
  api: {
    list: listMock,
    get: vi.fn(),
  },
  list: {
    columns: [
      { key: "name", header: "Name", render: (row) => row.name, sortable: true, filterable: true, identifier: true },
    ],
    defaultSort: "name:asc",
    searchable: true,
  },
  detail: { tabs: [] },
  routes: { list: "/fake", detail: (id) => `/fake/${id}` },
};

registerEntityType(fakeConfig);

// The list's page requests — not the one-row count behind the titlebar's unfiltered total, which a
// search or a filter also triggers.
function pageCalls(): Record<string, unknown>[] {
  return listMock.mock.calls.map(([p]) => p as Record<string, unknown>).filter((p) => p.limit !== 1);
}

function lastPageCall(): Record<string, unknown> | undefined {
  const calls = pageCalls();
  return calls[calls.length - 1];
}

// A second, separate fake entity for the createForm overlay tests below — its own key so it
// doesn't interfere with fakeConfig's own onCreate-bridge tests, exercising EntityListPanel's own
// mechanism (an entity supplying list.createForm gets an overlay instead of the onCreate bridge),
// not any real entity's form content.
const fakeConfigWithCreateForm: EntityTypeConfig<FakeRow, FakeRow> = {
  ...fakeConfig,
  key: "fake-entity-with-create-form",
  list: {
    ...fakeConfig.list,
    createForm: (ctx) => (
      <button type="button" data-testid="fake-create-form-submit" onClick={() => ctx.onCreated("99")}>
        Simuler création
      </button>
    ),
  },
};

registerEntityType(fakeConfigWithCreateForm);

let container: HTMLDivElement;
let root: Root;

function renderPanel(
  onNavigate?: (entityType: string, id: string | number) => void,
  onCreate?: () => void,
  onOpenOverview?: (entityType: string, id: string | number) => void,
  overviewEntityId?: string | number,
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={true}>
        <EntityListPanel
          entityType="fake-entity"
          onNavigate={onNavigate}
          onCreate={onCreate}
          onOpenOverview={onOpenOverview}
          overviewEntityId={overviewEntityId}
        />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

// Search is debounced (EntityListPanel's SEARCH_DEBOUNCE_MS); flush() alone never waits that long.
async function flushDebounce() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 350));
  });
}

beforeEach(() => {
  // Column/action-bar arrangements persist in localStorage (listPreferences.ts); every test starts
  // from the catalog defaults.
  window.localStorage.clear();
  listMock.mockReset();
  listMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 20, offset: 0 });
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

describe("EntityListPanel", () => {
  it("fetches the first page with the default sort on first render", async () => {
    renderPanel();
    await flush();

    expect(listMock).toHaveBeenCalledWith(
      expect.objectContaining({ offset: 0, limit: DEFAULT_LIMIT, sort: "name:asc", search: undefined }),
    );
    expect(container.textContent).toContain("Row A");
  });

  it("resets the offset and passes the typed value when searching", async () => {
    renderPanel();
    await flush();

    const input = container.querySelector("input") as HTMLInputElement;
    expect(input).toBeTruthy();

    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "abc");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    // The query itself doesn't fire until the debounce settles — a bare flush() would still see
    // the previous, un-searched call.
    await flushDebounce();
    await flush();

    expect(lastPageCall()).toEqual(expect.objectContaining({ offset: 0, search: "abc" }));
  });

  it("does not fire a new query on every keystroke while still typing (debounced search)", async () => {
    renderPanel();
    await flush();
    listMock.mockClear();

    const input = container.querySelector("input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "a");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 100));
    });
    await act(async () => {
      nativeSetter.call(input, "ab");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });

    // Still well within the debounce window from the second keystroke.
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 100));
    });
    expect(listMock).not.toHaveBeenCalled();

    await flushDebounce();
    await flush();
    expect(pageCalls()).toHaveLength(1);
    expect(lastPageCall()).toEqual(expect.objectContaining({ search: "ab" }));
  });

  it("calls onNavigate with the row's entity type and id when the identifier cell is clicked and no onOpenOverview is supplied", async () => {
    const onNavigate = vi.fn();
    renderPanel(onNavigate);
    await flush();

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    expect(identifierCell).toBeTruthy();

    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith("fake-entity", "1");
  });

  it("opens the overview (onOpenOverview), not the full fiche, when the identifier chip is clicked", async () => {
    const onNavigate = vi.fn();
    const onOpenOverview = vi.fn();
    renderPanel(onNavigate, undefined, onOpenOverview);
    await flush();

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity", "1", expect.objectContaining({ label: expect.any(String) }));
    expect(onNavigate).not.toHaveBeenCalled();
    // No separate "open in overview" button any more: the chip is the one target.
    expect(container.querySelector(".entity-list-panel-identifier-open")).toBeNull();
  });

  it("falls back to the overview when the identifier chip is clicked and no onNavigate is supplied", async () => {
    const onOpenOverview = vi.fn();
    renderPanel(undefined, undefined, onOpenOverview);
    await flush();

    const identifierCell = container.querySelector(".entity-list-panel-identifier-link") as HTMLElement;
    await act(async () => {
      identifierCell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity", "1", expect.objectContaining({ label: expect.any(String) }));
  });

  it("does not navigate or open the overview when a non-identifier part of the row is clicked", async () => {
    const onNavigate = vi.fn();
    const onOpenOverview = vi.fn();
    renderPanel(onNavigate, undefined, onOpenOverview);
    await flush();

    const row = container.querySelector("tbody tr") as HTMLElement;
    expect(row).toBeTruthy();

    await act(async () => {
      row.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).not.toHaveBeenCalled();
    expect(onOpenOverview).not.toHaveBeenCalled();
  });

  it("highlights the row matching overviewEntityId with the overview-open class", async () => {
    renderPanel(undefined, undefined, undefined, "1");
    await flush();

    const row = container.querySelector("tbody tr") as HTMLElement;
    expect(row.className).toContain("overview-open");
  });

  it("renders the identifier cell as a navigation chip preceded by the row's validation state", async () => {
    const leadingConfig: EntityTypeConfig<FakeRow, FakeRow> = {
      ...fakeConfig,
      key: "fake-leading-entity",
      list: {
        ...fakeConfig.list,
        columns: [
          {
            key: "name",
            header: "Name",
            identifier: true,
            render: (row) => row.name,
          },
        ],
      },
      routes: { list: "/fake-leading", detail: (id) => `/fake-leading/${id}` },
    };
    registerEntityType(leadingConfig);

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={false}>
          <EntityListPanel entityType="fake-leading-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    const cell = container.querySelector(".entity-list-panel-cell")!;
    expect(cell.querySelector(".validation-status-badge")).toBeTruthy();
    const chip = cell.querySelector(".entity-nav-chip")!;
    expect(chip.querySelector(".bi-question")).toBeTruthy();
    expect(chip.textContent).toContain("Row A");
    // The badge is outside the clickable chip — only the chip opens the entity.
    expect(chip.querySelector(".validation-status-badge")).toBeFalsy();
  });

  it("renders the table in PrimeReact's small size, like the JSF p:dataTable", async () => {
    renderPanel();
    await flush();

    expect(container.querySelector(".p-datatable.p-datatable-sm")).toBeTruthy();
  });

  it("wraps every column header so it stays on one line, keeping the full label as a tooltip", async () => {
    renderPanel();
    await flush();

    const header = container.querySelector(".entity-list-panel-column-header") as HTMLElement;
    expect(header).toBeTruthy();
    expect(header.getAttribute("title")).toBe("Name");
  });

  it("renders the header icon, plural label and a count chip (actionUnitListPanelHeader.xhtml)", async () => {
    renderPanel();
    await flush();

    const header = container.querySelector(".entity-list-panel-header")!;
    expect(header.querySelector(".bi-question")).toBeTruthy();
    expect(header.textContent).toContain("Fakes");
    expect(header.textContent).toContain("1");
  });

  // The titlebar counts the whole collection; the toolbar's selected/total chip the filtered result.
  it("keeps the unfiltered total in the titlebar while a search narrows the list", async () => {
    listMock.mockImplementation(async (params) => {
      const p = params as { search?: string; limit: number };
      const total = p.search ? 3 : 40;
      return { data: [{ id: "1", name: "Alpha" }], totalCount: total, limit: p.limit, offset: 0 };
    });
    renderPanel();
    await flush();
    const titleCount = () => container.querySelector(".entity-list-panel-header .p-chip")?.textContent;
    const toolbarCount = () => container.querySelector(".entity-list-panel-selection-count")?.textContent;
    expect(titleCount()).toBe("40");
    expect(toolbarCount()).toContain("0/40");

    const input = container.querySelector("input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "al");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await flushDebounce();
    await flush();

    expect(titleCount()).toBe("40");
    expect(toolbarCount()).toContain("0/3");
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ offset: 0, limit: 1 }));
    expect(listMock).not.toHaveBeenCalledWith(expect.objectContaining({ limit: 1, search: "al" }));
  });

  it("gives the count chip JSF's <entity>-count-chip class when the entity declares a panelClass", async () => {
    registerEntityType({ ...fakeConfig, key: "fake-themed-list-entity", panelClass: "fake-thing-panel" });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityListPanel entityType="fake-themed-list-entity" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector(".entity-list-panel-header .p-chip.fake-thing-count-chip")).toBeTruthy();
  });

  it("only renders the create button when onCreate is provided (list's own toolbar create button)", async () => {
    renderPanel(undefined, undefined);
    await flush();
    expect(Array.from(container.querySelectorAll("button")).some((b) => b.textContent === "Créer")).toBe(false);

    const onCreate = vi.fn();
    renderPanel(undefined, onCreate);
    await flush();
    const createButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer")!;
    expect(createButton).toBeTruthy();

    await act(async () => {
      createButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it("renders its toolbar inside its own header, not as a separate strip (plan §7/§8 follow-up)", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={true}>
          <EntityListPanel
            entityType="fake-entity"
            toolbar={{ chrome: { resourceUri: "/fake", title: "Fakes", bookmarked: false }, actions: { duplicate: () => {} } }}
          />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    const header = container.querySelector(".p-panel-header")!;
    expect(header).toBeTruthy();
    expect(header.querySelector(".bi-copy")).toBeTruthy();
  });

  it("always shows the selection column, and the selected/filtered-total count in the toolbar", async () => {
    renderPanel();
    await flush();

    expect(container.querySelector("th.p-selection-column")).toBeTruthy();
    // In the toolbar, not in the selection column's header (which it widened).
    const count = container.querySelector(".entity-list-panel-toolbar .entity-list-panel-selection-count");
    expect(count?.textContent).toContain("0/1");
    expect(container.querySelector("th.p-selection-column .p-chip")).toBeNull();

    const rowBox = container.querySelector("td.p-selection-column .p-checkbox-box, td.p-selection-column input") as HTMLElement;
    await act(async () => {
      rowBox.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    expect(container.querySelector(".entity-list-panel-selection-count")?.textContent).toContain("1/1");
    // No hover checkbox on the identifier chip any more: selection is the column's job only.
    expect(container.querySelector(".entity-list-panel-identifier-select")).toBeNull();
  });

  it("paginates 25 rows per page by default, with 25/50/100/200 to choose from", async () => {
    renderPanel();
    await flush();

    expect(container.querySelector(".p-paginator")).toBeTruthy();
    expect(container.querySelector(".p-virtualscroller")).toBeNull();
    const dropdown = container.querySelector(".p-paginator .p-dropdown");
    expect(dropdown?.textContent).toContain("25");
  });

  it("fetches the page the user moves to, and counts the whole result set", async () => {
    listMock.mockImplementation((params: unknown) => {
      const p = params as { offset: number; limit: number };
      return Promise.resolve({
        data: Array.from({ length: p.limit }, (_, i) => ({ id: String(p.offset + i + 1), name: `Row ${p.offset + i + 1}` })),
        totalCount: DEFAULT_LIMIT * 3,
        limit: p.limit,
        offset: p.offset,
      });
    });
    renderPanel();
    await flush();

    const chips = Array.from(container.querySelectorAll(".p-chip-text")).map((c) => c.textContent);
    expect(chips).toContain(String(DEFAULT_LIMIT * 3));

    const next = container.querySelector(".p-paginator-next") as HTMLElement;
    await act(async () => {
      next.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ offset: DEFAULT_LIMIT, limit: DEFAULT_LIMIT }));
    expect(container.textContent).toContain(`Row ${DEFAULT_LIMIT + 1}`);
  });

  it("shows an unsupported message for an unregistered entity type", async () => {
    renderPanel.bind(null);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={true}>
          <EntityListPanel entityType="does-not-exist" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.textContent).toContain("does-not-exist");
  });
});

// A second, schema-bearing fake entity type — kept in its own describe block so the mechanism
// (dynamic columns from config.list.schema, the column toggler, the `fields=` param) is tested
// independently of the plain-config suite above.
interface FakeFormRow {
  id: string;
  name: string;
  answers?: Record<string, unknown>;
  _permissions?: { canEdit: boolean };
}

const schemaListMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeFormRow>>>();
const schemaLoadMock = vi.fn();
const patchAnswersMock = vi.fn();

const fakeSchemaConfig: EntityTypeConfig<FakeFormRow, FakeFormRow> = {
  key: "fake-schema-entity",
  labels: { singular: "FakeS", plural: "FakeSs" },
  collectionPath: "fake-schema-entities",
  icon: "bi bi-question",
  api: {
    list: schemaListMock,
    get: vi.fn(),
    patchAnswers: patchAnswersMock,
  },
  list: {
    columns: [{ key: "name", header: "Name", render: (row) => row.name, sortable: true }],
    schema: { load: schemaLoadMock },
    defaultSort: "name:asc",
    searchable: false,
  },
  detail: { tabs: [] },
  routes: { list: "/fake-schema", detail: (id) => `/fake-schema/${id}` },
};

registerEntityType(fakeSchemaConfig);

describe("EntityListPanel with a field catalog (config.list.schema)", () => {
  beforeEach(() => {
    schemaListMock.mockReset();
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    schemaLoadMock.mockReset();
    schemaLoadMock.mockResolvedValue({
      fields: { "-118": { id: "-118", resourceType: "fields", label: "Statut", answerType: "TEXT", isSystemField: false } },
      columns: [{ fieldId: "-118", visible: true, order: 0 }],
    });
  });

  function renderSchemaPanel() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={true}>
          <EntityListPanel entityType="fake-schema-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  async function click(el: Element) {
    await act(async () => {
      el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
  }

  // Gear → its "Colonnes…" entry → the chooser overlay.
  async function openColumnChooser() {
    await click(container.querySelector(".entity-list-panel-gear-button")!);
    const entry = Array.from(document.body.querySelectorAll(".entity-list-panel-gear-menu .p-menuitem-link")).find((a) =>
      a.textContent?.includes("Colonnes"),
    )!;
    expect(entry).toBeTruthy();
    await click(entry);
  }

  function chooserSwitch(fieldLabel: string, section: "visible" | "hidden"): Element {
    const item = Array.from(
      document.body.querySelectorAll(`.entity-list-panel-column-toggler [data-section="${section}"] .visibility-chooser-item`),
    ).find((li) => li.textContent?.includes(fieldLabel))!;
    expect(item).toBeTruthy();
    return item.querySelector(".p-inputswitch input")!;
  }

  const twoFieldCatalog = {
    fields: {
      "-118": { id: "-118", resourceType: "fields", label: "Statut", answerType: "TEXT", isSystemField: false },
      "-119": { id: "-119", resourceType: "fields", label: "Commentaire", answerType: "TEXT", isSystemField: false },
    },
    columns: [
      { fieldId: "-118", visible: true, order: 0 },
      { fieldId: "-119", visible: false, order: 1 },
    ],
  };

  it("waits for the catalog and fetches the first chunk once, with its columns", async () => {
    renderSchemaPanel();
    await flush();
    await flush();

    expect(schemaListMock).toHaveBeenCalledTimes(1);
    expect(schemaListMock).toHaveBeenCalledWith(expect.objectContaining({ fields: "-118", offset: 0 }));
  });

  it("showing a column fetches only that column for the loaded rows and merges it in", async () => {
    schemaLoadMock.mockResolvedValue(twoFieldCatalog);
    schemaListMock.mockImplementation(async (params) => {
      const fields = (params as { fields?: string }).fields;
      const answers: Record<string, unknown> = {};
      if (fields?.split(",").includes("-118")) answers["-118"] = "En cours";
      if (fields?.split(",").includes("-119")) answers["-119"] = "Note A";
      return { data: [{ id: "1", name: "Row A", answers }], totalCount: 1, limit: 50, offset: 0 };
    });
    renderSchemaPanel();
    await flush();
    await flush();
    expect(schemaListMock).toHaveBeenCalledTimes(1);

    await openColumnChooser();
    await click(chooserSwitch("Commentaire", "hidden"));
    await flush();

    // One more request, for the new column alone — not the whole row set again.
    expect(schemaListMock).toHaveBeenCalledTimes(2);
    expect(schemaListMock).toHaveBeenLastCalledWith(expect.objectContaining({ fields: "-119", offset: 0 }));
    // The existing column's value is still there, and the new one merged in beside it.
    expect(container.textContent).toContain("En cours");
    expect(container.textContent).toContain("Note A");
    expect(container.querySelector("th")?.parentElement?.textContent).toContain("Commentaire");
  });

  it("hiding a column fetches nothing", async () => {
    renderSchemaPanel();
    await flush();
    await flush();
    const before = schemaListMock.mock.calls.length;

    await openColumnChooser();
    await click(chooserSwitch("Statut", "visible"));
    await flush();

    expect(schemaListMock).toHaveBeenCalledTimes(before);
    expect(container.textContent).not.toContain("En cours");
  });

  it("filters both chooser sections with the search box", async () => {
    schemaLoadMock.mockResolvedValue(twoFieldCatalog);
    renderSchemaPanel();
    await flush();
    await flush();
    await openColumnChooser();

    const input = document.body.querySelector(".entity-list-panel-column-toggler input") as HTMLInputElement;
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
      setter.call(input, "comm");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await flush();

    const chooser = document.body.querySelector(".entity-list-panel-column-toggler")!;
    expect(chooser.querySelector('[data-section="visible"] ul')!.textContent).not.toContain("Statut");
    expect(chooser.querySelector('[data-section="hidden"] ul')!.textContent).toContain("Commentaire");
  });

  it("restores a saved column arrangement, ignoring columns the catalog no longer has", async () => {
    schemaLoadMock.mockResolvedValue(twoFieldCatalog);
    window.localStorage.setItem(
      "siamois.list.org-.fake-schema-entity.global",
      JSON.stringify({ v: 1, visibleColumns: ["-119", "gone", "-118"] }),
    );
    renderSchemaPanel();
    await flush();
    await flush();

    expect(schemaListMock).toHaveBeenCalledTimes(1);
    expect(schemaListMock).toHaveBeenCalledWith(expect.objectContaining({ fields: "-118,-119" }));
    const headers = Array.from(container.querySelectorAll(".entity-list-panel-column-header")).map((h) => h.textContent);
    expect(headers.indexOf("Commentaire")).toBeLessThan(headers.indexOf("Statut"));
  });

  it("saves the arrangement when a column is toggled", async () => {
    schemaLoadMock.mockResolvedValue(twoFieldCatalog);
    renderSchemaPanel();
    await flush();
    await flush();
    await openColumnChooser();
    await click(chooserSwitch("Commentaire", "hidden"));

    const saved = JSON.parse(window.localStorage.getItem("siamois.list.org-.fake-schema-entity.global")!);
    expect(saved.visibleColumns).toEqual(["-118", "-119"]);
  });

  it("requests only the catalog's default-visible field ids and renders the dynamic column", async () => {
    renderSchemaPanel();
    await flush();
    await flush();

    expect(schemaListMock).toHaveBeenLastCalledWith(expect.objectContaining({ fields: "-118" }));
    expect(container.textContent).toContain("Statut");
    expect(container.textContent).toContain("En cours");
  });

  it("collapses a multi-valued dynamic cell to its first label plus a +N counter", async () => {
    schemaLoadMock.mockResolvedValue({
      fields: {
        "-120": {
          id: "-120",
          resourceType: "fields",
          label: "Périodes",
          answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE",
          isSystemField: false,
        },
      },
      columns: [{ fieldId: "-120", visible: true, order: 0 }],
    });
    schemaListMock.mockResolvedValue({
      data: [
        {
          id: "1",
          name: "Row A",
          answers: {
            "-120": [
              { resourceId: "1", resourceType: "concepts", label: "Néolithique" },
              { resourceId: "2", resourceType: "concepts", label: "Âge du Fer" },
            ],
          },
        },
      ],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    const cell = container.querySelector(".cell-multi")!;
    expect(cell).toBeTruthy();
    expect(cell.querySelector(".cell-multi-first")!.textContent).toBe("Néolithique");
    expect(cell.querySelector(".cell-multi-more")!.textContent).toBe("+1");
    // The second label only exists as the counter's tooltip, not as visible text that would push
    // the cell onto a second line.
    expect(cell.querySelector(".cell-multi-more")!.getAttribute("title")).toBe("Néolithique, Âge du Fer");
  });

  it("does not request a fields param for a config without a schema", async () => {
    renderPanel();
    await flush();

    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ fields: undefined }));
    // The gear is the column show/hide control and nothing else now, so a config with no schema
    // has no gear at all — even though its "name" column is filterable (filtering lives in the
    // toolbar's chip bar, not behind the gear).
    expect(container.querySelector(".entity-list-panel-gear-button")).toBeFalsy();
  });

  it("shows no gear button for a config with no schema", async () => {
    const bareListMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();
    bareListMock.mockResolvedValue({ data: [], totalCount: 0, limit: 10, offset: 0 });
    registerEntityType({
      key: "fake-bare-entity",
      labels: { singular: "Bare", plural: "Bares" },
      collectionPath: "fake-bare-entities",
      icon: "bi bi-question",
      api: { list: bareListMock, get: vi.fn() },
      list: { columns: [{ key: "name", header: "Name", render: (row) => row.name }], searchable: false },
      detail: { tabs: [] },
      routes: { list: "/fake-bare", detail: (id) => `/fake-bare/${id}` },
    });

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={true}>
          <EntityListPanel entityType="fake-bare-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector(".entity-list-panel-gear-button")).toBeFalsy();
  });

  it("shows the gear button and a column toggler for a config with a schema", async () => {
    renderSchemaPanel();
    await flush();
    await flush();

    await openColumnChooser();

    // PrimeReact's OverlayPanel portals into document.body by default, not into `container`.
    const chooser = document.body.querySelector(".entity-list-panel-column-toggler")!;
    expect(chooser).toBeTruthy();
    // Two sections and a search box — not a multiselect.
    expect(chooser.querySelector('[data-section="visible"]')!.textContent).toContain("Statut");
    expect(chooser.querySelector('[data-section="hidden"]')).toBeTruthy();
    expect(chooser.querySelector("input")).toBeTruthy();
    // The gear offers column visibility and nothing else — no filter enable/disable switch.
    expect(document.body.querySelector(".entity-list-panel-filter-toggle")).toBeFalsy();
  });

  it("puts the gear before the search box in the toolbar", async () => {
    // fakeSchemaConfig isn't searchable; a schema-bearing, searchable config is what this checks.
    const bothListMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeFormRow>>>();
    bothListMock.mockResolvedValue({ data: [], totalCount: 0, limit: 10, offset: 0 });
    registerEntityType({
      key: "fake-gear-order-entity",
      labels: { singular: "Geared", plural: "Geareds" },
      collectionPath: "fake-gear-order-entities",
      icon: "bi bi-question",
      api: { list: bothListMock, get: vi.fn() },
      list: {
        columns: [{ key: "name", header: "Name", render: (row: FakeFormRow) => row.name }],
        schema: { load: schemaLoadMock },
        searchable: true,
      },
      detail: { tabs: [] },
      routes: { list: "/fake-gear-order", detail: (id) => `/fake-gear-order/${id}` },
    });

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={true}>
          <EntityListPanel entityType="fake-gear-order-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();
    await flush();

    const start = container.querySelector(".entity-list-panel-toolbar .p-toolbar-group-start")!;
    const gear = start.querySelector(".entity-list-panel-gear-button")!;
    const search = start.querySelector("input")!;
    expect(gear.compareDocumentPosition(search) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});

describe("EntityListPanel filter chips (toolbar)", () => {
  it("shows an \"Ajouter un filtre\" chip and no filter inputs until one is added", async () => {
    renderPanel();
    await flush();

    const bar = container.querySelector(".entity-list-panel-filter-chips")!;
    expect(bar).toBeTruthy();
    // In the toolbar's start group (after the search box), not in the table's own header.
    expect(bar.closest(".entity-list-panel-toolbar .p-toolbar-group-start")).toBeTruthy();
    expect(container.querySelector(".p-datatable-header")).toBeNull();
    expect(bar.textContent).toContain("Ajouter un filtre");
    // No enable/disable switch anywhere, and no standalone filter row.
    expect(container.querySelector(".entity-list-panel-filters")).toBeFalsy();
    expect(bar.querySelector(".filter-chip-body")).toBeFalsy();
  });

  it("adds a chip for the picked column and applies its value to the list query", async () => {
    renderPanel();
    await flush();
    listMock.mockClear();

    const addChip = container.querySelector(".filter-chip-add") as HTMLElement;
    await act(async () => {
      addChip.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    // Both overlays portal into document.body.
    const pickerItem = Array.from(document.body.querySelectorAll(".filter-picker-item")).find(
      (b) => b.textContent === "Name",
    ) as HTMLElement;
    expect(pickerItem).toBeTruthy();
    await act(async () => {
      pickerItem.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const filterInput = document.body.querySelector(".entity-list-panel-filter-editor input") as HTMLInputElement;
    expect(filterInput).toBeTruthy();

    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(filterInput, "abc");
      filterInput.dispatchEvent(new Event("input", { bubbles: true }));
      // ContainsFilter commits on blur or Enter — Enter is the reliable one to dispatch here,
      // since native "blur" doesn't bubble and jsdom won't deliver it the way a real focus change
      // would.
      filterInput.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();

    expect(lastPageCall()).toEqual(
      expect.objectContaining({ filters: { name: { op: "contains", v: "abc" } } }),
    );
    // The chip now carries the applied value, so the active filter is readable without opening it.
    const chip = container.querySelector(".filter-chip-body")!;
    expect(chip.textContent).toContain("Name");
    expect(chip.textContent).toContain("abc");
  });

  it("drops the filter from the query when its chip is removed", async () => {
    renderPanel();
    await flush();

    const addChip = container.querySelector(".filter-chip-add") as HTMLElement;
    await act(async () => {
      addChip.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    const pickerItem = document.body.querySelector(".filter-picker-item") as HTMLElement;
    await act(async () => {
      pickerItem.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const filterInput = document.body.querySelector(".entity-list-panel-filter-editor input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(filterInput, "abc");
      filterInput.dispatchEvent(new Event("input", { bubbles: true }));
      filterInput.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();
    listMock.mockClear();

    const remove = container.querySelector(".filter-chip-remove") as HTMLElement;
    await act(async () => {
      remove.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ filters: undefined }));
    expect(container.querySelector(".filter-chip-body")).toBeFalsy();
  });
});

// One gesture for every field cell: a click opens the overlay — an editor, or the value alone
// when it can't be edited. Clicks the (first) field cell and says which one opened.
async function openedOverlayMode(): Promise<"editor" | "readonly" | "none"> {
  const cell = container.querySelector(".entity-list-panel-editable-cell") as HTMLElement | null;
  if (!cell) return "none";
  await act(async () => {
    cell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await flush();
  const overlay = document.body.querySelector(".cell-edit-overlay");
  if (!overlay) return "none";
  return overlay.querySelector(".cell-edit-overlay-readonly") ? "readonly" : "editor";
}

describe("EntityListPanel click-to-edit (plan phase 4b)", () => {
  beforeEach(() => {
    schemaListMock.mockReset();
    schemaLoadMock.mockReset();
    patchAnswersMock.mockReset();
    schemaLoadMock.mockResolvedValue({
      fields: { "-118": { id: "-118", resourceType: "fields", label: "Statut", answerType: "TEXT", isSystemField: false } },
      columns: [{ fieldId: "-118", visible: true, order: 0 }],
    });
    patchAnswersMock.mockResolvedValue({});
  });

  function renderSchemaPanel(writeMode = true) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <EntityListPanel entityType="fake-schema-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  // entityDataTable.xhtml gates an editable cell on isRendered(col, "writeMode", item): the app's
  // global read/write switch AND the row's own permission, the same two-part rule the fiche and the
  // panel header follow.
  it("opens the value read-only in read mode, even for a row the user may edit", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: true } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel(false);
    await flush();
    await flush();

    // The value is still shown — read mode removes the editor, not the data nor the overlay.
    expect(container.textContent).toContain("En cours");
    expect(await openedOverlayMode()).toBe("readonly");
    expect(document.body.querySelector(".cell-edit-overlay input")).toBeNull();
  });

  it("anchors the editor on the whole cell, not on the clicked text span", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: true } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    const cell = container.querySelector(".entity-list-panel-editable-cell") as HTMLElement;
    const td = cell.closest("td") as HTMLElement;
    // jsdom gives everything a zero rect, so the two have to be told apart explicitly.
    cell.getBoundingClientRect = () => ({ top: 50, left: 60, width: 30, height: 16 }) as DOMRect;
    td.getBoundingClientRect = () => ({ top: 44, left: 8, width: 240, height: 28 }) as DOMRect;

    await act(async () => {
      cell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const overlay = document.body.querySelector(".cell-edit-overlay") as HTMLElement;
    expect(overlay.style.top).toBe("44px");
    expect(overlay.style.left).toBe("8px");
  });

  it("opens the edit overlay on a dynamic cell click when the row's _permissions.canEdit is true", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: true } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    const onNavigate = vi.fn();
    renderSchemaPanel();
    await flush();
    await flush();

    const cell = container.querySelector(".entity-list-panel-editable-cell") as HTMLElement;
    expect(cell).toBeTruthy();
    await act(async () => {
      cell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    // PrimeReact's OverlayPanel portals into document.body.
    expect(document.body.querySelector(".cell-edit-overlay")).toBeTruthy();
    expect(document.body.querySelector(".cell-edit-overlay-readonly")).toBeNull();
    // Clicking the editable cell must NOT also trigger row-click navigation (the "collision" the
    // plan calls out) — nothing here wires onNavigate for this fake config, but the absence of a
    // navigation call is exactly the point.
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("opens the value read-only when _permissions.canEdit is false", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: false } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    expect(container.textContent).toContain("En cours");
    expect(await openedOverlayMode()).toBe("readonly");
  });

  it("opens the value read-only when _permissions is absent", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    expect(await openedOverlayMode()).toBe("readonly");
  });

  it("saves through config.api.patchAnswers and refetches the list", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: true } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    const cell = container.querySelector(".entity-list-panel-editable-cell") as HTMLElement;
    await act(async () => {
      cell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const input = document.body.querySelector(".cell-edit-overlay input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Terminé");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });

    // No Save button: the edit commits on Enter (or on focus leaving the editor).
    expect(Array.from(document.body.querySelectorAll("button")).some((b) => b.textContent === "Enregistrer")).toBe(false);
    schemaListMock.mockClear();
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();
    await flush();

    expect(patchAnswersMock).toHaveBeenCalledWith("1", { "-118": { value: "Terminé" } });
    // Cache invalidation triggers a refetch of the list.
    expect(schemaListMock).toHaveBeenCalled();
  });

  // Rows being (re)fetched in the background get a visible signal — the same thin progress bar the
  // JSF panels use — without it replacing the rows already on screen.
  it("animates the progress bar only while rows are being fetched in the background", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: true } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    const bar = () => container.querySelector(".entity-list-panel-progressbar") as HTMLElement;
    expect(bar()).toBeTruthy();
    expect(bar().classList.contains("is-active")).toBe(false);

    const cell = container.querySelector(".entity-list-panel-editable-cell") as HTMLElement;
    await act(async () => {
      cell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();
    const input = document.body.querySelector(".cell-edit-overlay input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Terminé");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });

    // The refetch the save triggers hangs until released, so its in-flight state is observable.
    let release!: () => void;
    schemaListMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = () =>
            resolve({ data: [{ id: "1", name: "Row A", answers: {}, _permissions: { canEdit: true } }], totalCount: 1, limit: 10, offset: 0 });
        }),
    );
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();

    expect(bar().classList.contains("is-active")).toBe(true);
    // The rows already loaded stay on screen meanwhile.
    expect(container.textContent).toContain("Row A");

    await act(async () => release());
    await flush();
    expect(bar().classList.contains("is-active")).toBe(false);
  });
});

describe("EntityListPanel scrolling: sticky header and frozen columns", () => {
  function renderFor(entityType: string) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType={entityType} />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  function headerCells(): HTMLElement[] {
    return Array.from(container.querySelectorAll(".p-datatable-thead > tr > th"));
  }

  it("makes the table body the scrolling box, so the header stays put", async () => {
    listMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 10, offset: 0 });
    renderPanel();
    await flush();

    // scrollable is what turns the thead into a position:sticky header inside .p-datatable-wrapper
    // — without it the page scrolls and the header leaves with it.
    expect(container.querySelector(".p-datatable-scrollable")).toBeTruthy();
    expect(container.querySelector(".p-datatable-wrapper")).toBeTruthy();
  });

  it("freezes the selection box and the identifier column", async () => {
    listMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 10, offset: 0 });
    renderPanel();
    await flush();

    // fakeConfig: one column, `name`, marked identifier — so selection + name + the row actions
    // right after it, and that is all.
    const frozen = headerCells().filter((th) => th.classList.contains("entity-list-panel-frozen"));
    expect(frozen).toHaveLength(3);
    expect(frozen[0].classList.contains("p-selection-column")).toBe(true);
    expect(frozen[1].textContent).toContain("Name");
    expect(frozen[2].classList.contains("entity-list-panel-row-actions-cell")).toBe(true);
  });

  it("freezes every column up to AND including the identifier, not just the identifier", async () => {
    const mock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();
    mock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 10, offset: 0 });
    registerEntityType({
      key: "fake-late-identifier-entity",
      collectionPath: "fake-late-identifier-entities",
      labels: { singular: "Late", plural: "Lates" },
      icon: "bi bi-question",
      api: { list: mock, get: vi.fn() },
      list: {
        columns: [
          { key: "status", header: "Statut", render: () => "x" },
          { key: "name", header: "Name", render: (row) => row.name, identifier: true },
          { key: "after", header: "Après", render: () => "y" },
        ],
        searchable: false,
      },
      detail: { tabs: [] },
      routes: { list: "/fake-late", detail: (id) => `/fake-late/${id}` },
    });
    renderFor("fake-late-identifier-entity");
    await flush();

    const frozenLabels = headerCells()
      .filter((th) => th.classList.contains("entity-list-panel-frozen"))
      .map((th) => th.textContent);
    // Selection + Statut + Name + row actions; "Après" scrolls away.
    expect(frozenLabels).toHaveLength(4);
    expect(frozenLabels[1]).toContain("Statut");
    expect(frozenLabels[2]).toContain("Name");
    expect(frozenLabels.some((l) => l?.includes("Après"))).toBe(false);
  });

  it("freezes nothing at all when the entity declares no identifier column", async () => {
    const mock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();
    mock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 10, offset: 0 });
    registerEntityType({
      key: "fake-no-identifier-entity",
      collectionPath: "fake-no-identifier-entities",
      labels: { singular: "NoId", plural: "NoIds" },
      icon: "bi bi-question",
      api: { list: mock, get: vi.fn() },
      list: { columns: [{ key: "name", header: "Name", render: (row) => row.name }], searchable: false },
      detail: { tabs: [] },
      routes: { list: "/fake-noid", detail: (id) => `/fake-noid/${id}` },
    });
    renderFor("fake-no-identifier-entity");
    await flush();

    // Not even the selection box: freezing it alone would pin an empty 3rem strip for no reason.
    expect(headerCells().filter((th) => th.classList.contains("entity-list-panel-frozen"))).toHaveLength(0);
  });

  it("freezes the identifier column only, leaving dynamic catalog columns scrollable", async () => {
    schemaListMock.mockReset();
    schemaLoadMock.mockReset();
    schemaLoadMock.mockResolvedValue({
      fields: { "-118": { id: "-118", resourceType: "fields", label: "Statut", answerType: "TEXT", isSystemField: false } },
      columns: [{ fieldId: "-118", visible: true, order: 0 }],
    });
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    // fakeSchemaConfig's `name` column is NOT marked identifier, so nothing is frozen here — the
    // dynamic columns must never be swept into the frozen half by accident.
    renderFor("fake-schema-entity");
    await flush();
    await flush();

    const frozen = headerCells().filter((th) => th.classList.contains("entity-list-panel-frozen"));
    expect(frozen).toHaveLength(0);
    expect(headerCells().some((th) => th.textContent?.includes("Statut"))).toBe(true);
  });
});

describe("EntityListPanel embedded mode (plan: generic related-list tab)", () => {
  const embeddedListMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();

  const embeddedConfig: EntityTypeConfig<FakeRow, FakeRow> = {
    key: "fake-embedded-entity",
    labels: { singular: "Embedded", plural: "Embeddeds" },
    collectionPath: "fake-embedded-entities",
    icon: "bi bi-question",
    api: { list: embeddedListMock, get: vi.fn() },
    list: {
      columns: [{ key: "name", header: "Name", render: (row) => row.name, identifier: true }],
      searchable: true,
    },
    detail: { tabs: [] },
    routes: { list: "/fake-embedded", detail: (id) => `/fake-embedded/${id}` },
  };
  registerEntityType(embeddedConfig);

  function renderEmbedded(scope?: { entityType: string; id: string | number }) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType="fake-embedded-entity" embedded scope={scope} />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  beforeEach(() => {
    embeddedListMock.mockReset();
    embeddedListMock.mockResolvedValue({ data: [{ id: "1", name: "Row A" }], totalCount: 1, limit: 10, offset: 0 });
  });

  it("renders no <Panel>/PanelHeaderBar chrome — no plural label, no count chip", async () => {
    renderEmbedded();
    await flush();

    expect(container.querySelector(".p-panel-header")).toBeNull();
    expect(container.textContent).not.toContain("Embeddeds");
    // The toolbar (search) and the table itself are still there.
    expect(container.querySelector(".entity-list-panel-toolbar")).not.toBeNull();
    expect(container.textContent).toContain("Row A");
  });

  it("passes the scope through to config.api.list", async () => {
    renderEmbedded({ entityType: "project", id: 5 });
    await flush();

    expect(embeddedListMock).toHaveBeenCalledWith(
      expect.objectContaining({ scope: { entityType: "project", id: 5 } }),
    );
  });

  it("omits scope from the params when not scoped", async () => {
    renderEmbedded();
    await flush();

    expect(embeddedListMock).toHaveBeenCalledWith(expect.objectContaining({ scope: undefined }));
  });
});

describe("EntityListPanel create overlay (config.list.createForm)", () => {
  function renderWithCreateForm(onNavigate = vi.fn(), onOpenOverview = vi.fn()) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel
              entityType="fake-entity-with-create-form"
              organizationId={7}
              onNavigate={onNavigate}
              onOpenOverview={onOpenOverview}
            />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    return { onNavigate, onOpenOverview };
  }

  it("opens the entity's own createForm in an overlay instead of bridging to onCreate", async () => {
    renderWithCreateForm();
    await flush();

    const createButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer")!;
    expect(document.body.querySelector('[data-testid="fake-create-form-submit"]')).toBeNull();

    await act(async () => {
      createButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(document.body.querySelector('[data-testid="fake-create-form-submit"]')).toBeTruthy();
  });

  it("opens the newly created entity in the overview (the list stays) once the form calls onCreated", async () => {
    const { onNavigate, onOpenOverview } = renderWithCreateForm();
    await flush();

    const createButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer")!;
    await act(async () => {
      createButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await act(async () => {
      document.body.querySelector<HTMLButtonElement>('[data-testid="fake-create-form-submit"]')!.click();
    });

    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity-with-create-form", "99");
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("falls back to onNavigate when the caller has no overview pane", async () => {
    const onNavigate = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel
              entityType="fake-entity-with-create-form"
              organizationId={7}
              onNavigate={onNavigate}
            />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    const createButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer")!;
    await act(async () => {
      createButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await act(async () => {
      document.body.querySelector<HTMLButtonElement>('[data-testid="fake-create-form-submit"]')!.click();
    });

    expect(onNavigate).toHaveBeenCalledWith("fake-entity-with-create-form", "99");
  });
});

// Organization-wide lists (unscoped) vs a project's own relation tab (scoped): a parent column
// only on the former, and a create form that needs a project, picked in the form on the former.
const fakeScopedCreateConfig: EntityTypeConfig<FakeRow & { parent?: string }, FakeRow> = {
  ...(fakeConfig as EntityTypeConfig<FakeRow & { parent?: string }, FakeRow>),
  key: "fake-entity-needing-scope",
  list: {
    columns: [
      { key: "name", header: "Name", render: (row) => row.name, identifier: true },
      { key: "parent", header: "Parent column", render: (row) => row.parent ?? "", unscopedOnly: true },
    ],
    searchable: false,
    createForm: () => <span data-testid="scoped-create-form">form</span>,
    createProjectKind: "recordingUnit",
  },
};

registerEntityType(fakeScopedCreateConfig);

describe("EntityListPanel scope-dependent columns and creation", () => {
  function renderScoped(scope?: { entityType: string; id: string | number }) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType="fake-entity-needing-scope" organizationId={7} scope={scope} />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  function createButton(): HTMLButtonElement {
    return Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer") as HTMLButtonElement;
  }

  beforeEach(() => {
    listMock.mockReset();
    listMock.mockResolvedValue({ data: [{ id: "1", name: "Row" }], totalCount: 1, limit: 10, offset: 0 });
  });

  it("shows an unscopedOnly column on the organization-wide list", async () => {
    renderScoped();
    await flush();

    expect(container.textContent).toContain("Parent column");
  });

  it("drops an unscopedOnly column inside a scoped relation tab", async () => {
    renderScoped({ entityType: "project", id: 5 });
    await flush();

    expect(container.textContent).not.toContain("Parent column");
  });

  it("disables Créer on the unscoped list when the caller may create in no project", async () => {
    mockedSearchCreatableProjects.mockResolvedValue({ data: [], totalCount: 0, limit: 1, offset: 0 });
    renderScoped();
    await flush();

    expect(mockedSearchCreatableProjects).toHaveBeenCalledWith(7, "recordingUnit", undefined, 1);
    expect(createButton().disabled).toBe(true);
    await act(async () => {
      createButton().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(document.body.querySelector('[data-testid="scoped-create-form"]')).toBeNull();
  });

  it("enables Créer on the unscoped list once there is a project to create in (the form picks it)", async () => {
    mockedSearchCreatableProjects.mockResolvedValue({ data: [], totalCount: 3, limit: 1, offset: 0 });
    renderScoped();
    await flush();

    expect(createButton().disabled).toBe(false);
    await act(async () => {
      createButton().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(document.body.querySelector('[data-testid="scoped-create-form"]')).toBeTruthy();
  });

  it("keeps Créer active inside a scope, without looking up projects", async () => {
    mockedSearchCreatableProjects.mockReset();
    renderScoped({ entityType: "project", id: 5 });
    await flush();

    expect(mockedSearchCreatableProjects).not.toHaveBeenCalled();
    expect(createButton().disabled).toBe(false);
    await act(async () => {
      createButton().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(document.body.querySelector('[data-testid="scoped-create-form"]')).toBeTruthy();
  });
});

// A column linking to another entity (the organization-wide lists' "Projet" column).
const fakeLinkConfig: EntityTypeConfig<FakeRow & { parentId?: string }, FakeRow> = {
  ...(fakeConfig as EntityTypeConfig<FakeRow & { parentId?: string }, FakeRow>),
  key: "fake-entity-with-link",
  list: {
    columns: [
      { key: "name", header: "Name", render: (row) => row.name, identifier: true },
      {
        key: "parent",
        header: "Parent",
        render: (row) => `Parent ${row.parentId}`,
        link: (row) => (row.parentId ? { entityType: "fake-entity", id: row.parentId } : null),
      },
    ],
    searchable: false,
  },
};

registerEntityType(fakeLinkConfig);

describe("EntityListPanel linked columns", () => {
  function renderLinked(onOpenOverview?: (t: string, id: string | number) => void, onNavigate?: (t: string, id?: string | number) => void) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType="fake-entity-with-link" organizationId={7} onOpenOverview={onOpenOverview} onNavigate={onNavigate} />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  function linkChip(): HTMLElement | undefined {
    return Array.from(container.querySelectorAll<HTMLElement>(".entity-nav-chip")).find((el) =>
      el.textContent?.includes("Parent 5"),
    );
  }

  beforeEach(() => {
    listMock.mockReset();
    listMock.mockResolvedValue({
      data: [
        { id: "1", name: "Linked", parentId: "5" } as FakeRow,
        { id: "2", name: "Unlinked" },
      ],
      totalCount: 2,
      limit: 10,
      offset: 0,
    });
  });

  it("renders the linked cell as a chip with the target type's own icon", async () => {
    renderLinked(vi.fn());
    await flush();

    expect(linkChip()).toBeTruthy();
    expect(linkChip()!.querySelector("i")!.className).toBe("bi bi-question");
  });

  it("opens the linked entity's overview, not the row's own", async () => {
    const onOpenOverview = vi.fn();
    renderLinked(onOpenOverview);
    await flush();

    await act(async () => {
      linkChip()!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onOpenOverview).toHaveBeenCalledTimes(1);
    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity", "5");
  });

  it("falls back to onNavigate when there is no overview pane", async () => {
    const onNavigate = vi.fn();
    renderLinked(undefined, onNavigate);
    await flush();

    await act(async () => {
      linkChip()!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith("fake-entity", "5");
  });

  it("renders no chip for a row with nothing to link to", async () => {
    renderLinked(vi.fn());
    await flush();

    const chips = Array.from(container.querySelectorAll(".entity-nav-chip")).map((el) => el.textContent);
    expect(chips).not.toContain("Parent undefined");
  });
});

// A pinned column that shows a catalog field (ColumnDef.fieldId) sorts by that field's id, and the
// field isn't offered as a second column.
const pinnedFieldListMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeFormRow & { kind?: string }>>>();
const pinnedFieldLoadMock = vi.fn();

registerEntityType({
  key: "fake-pinned-field-entity",
  labels: { singular: "FakeP", plural: "FakePs" },
  collectionPath: "fake-pinned-field-entities",
  icon: "bi bi-question",
  api: { list: pinnedFieldListMock, get: vi.fn() },
  list: {
    columns: [
      { key: "name", header: "Name", render: (row: FakeFormRow) => row.name, sortable: true },
      // Not sortable by its own key on the server: only its field id is.
      { key: "kind", fieldId: "-7", header: "Kind", render: (row: FakeFormRow & { kind?: string }) => row.kind ?? "" },
    ],
    schema: { load: pinnedFieldLoadMock },
    defaultSort: "name:asc",
    searchable: false,
  },
  detail: { tabs: [] },
  routes: { list: "/fake-pinned", detail: (id: string | number) => `/fake-pinned/${id}` },
} as unknown as EntityTypeConfig<FakeFormRow, FakeFormRow>);

describe("EntityListPanel pinned columns backed by a catalog field", () => {
  const catalogWith = (sortable: boolean) => ({
    fields: {
      "-7": {
        id: "-7", resourceType: "fields", label: "Kind", answerType: "TEXT", isSystemField: true,
        query: { sortable, filterOp: "contains" },
      },
      "-8": { id: "-8", resourceType: "fields", label: "Other", answerType: "TEXT", isSystemField: true },
    },
    columns: [
      { fieldId: "-7", columnId: "-7", visible: true, order: 0 },
      { fieldId: "-8", columnId: "-8", visible: true, order: 1 },
    ],
  });

  function renderPinned() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType="fake-pinned-field-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  function header(label: string): HTMLElement | undefined {
    return Array.from(container.querySelectorAll<HTMLElement>("th")).find((th) => th.textContent?.trim() === label);
  }

  beforeEach(() => {
    window.sessionStorage.clear();
    window.localStorage.clear();
    pinnedFieldListMock.mockReset();
    pinnedFieldListMock.mockResolvedValue({ data: [{ id: "1", name: "Row A", kind: "K1" }], totalCount: 1, limit: 25, offset: 0 });
    pinnedFieldLoadMock.mockReset();
  });

  it("sorts by the field's id when the catalog says the field is sortable", async () => {
    pinnedFieldLoadMock.mockResolvedValue(catalogWith(true));
    renderPinned();
    await flush();

    expect(header("Kind")?.classList.contains("p-sortable-column")).toBe(true);
    await act(async () => {
      header("Kind")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(pinnedFieldListMock).toHaveBeenLastCalledWith(expect.objectContaining({ sort: "-7:asc" }));
  });

  it("stays unsortable when the catalog says the field is not", async () => {
    pinnedFieldLoadMock.mockResolvedValue(catalogWith(false));
    renderPinned();
    await flush();

    expect(header("Kind")).toBeDefined();
    expect(header("Kind")?.classList.contains("p-sortable-column")).toBe(false);
  });

  it("does not show the field a second time as a dynamic column", async () => {
    pinnedFieldLoadMock.mockResolvedValue(catalogWith(true));
    renderPinned();
    await flush();

    const headers = Array.from(container.querySelectorAll("th")).map((th) => th.textContent?.trim());
    expect(headers.filter((h) => h === "Kind")).toHaveLength(1);
    expect(headers).toContain("Other");
    // Nor requested: the projection only asks for the dynamic columns actually shown.
    expect(pinnedFieldListMock).toHaveBeenCalledWith(expect.objectContaining({ fields: "-8" }));
  });
});

// The fiche's previous/next arrows walk the list it was opened from (panels/listContext.ts).
describe("EntityListPanel remembers where a row was opened from", () => {
  function renderList(props: Record<string, unknown> = {}) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType="fake-entity-with-link" organizationId={7} onOpenOverview={vi.fn()} {...props} />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  function identifierChip(label: string): HTMLElement {
    return Array.from(container.querySelectorAll<HTMLElement>(".entity-list-panel-row-identifier")).find(
      (el) => el.textContent?.includes(label),
    )!;
  }

  beforeEach(() => {
    window.sessionStorage.clear();
    listMock.mockReset();
    listMock.mockResolvedValue({
      data: [
        { id: "1", name: "Linked" } as FakeRow,
        { id: "2", name: "Unlinked" },
      ],
      totalCount: 2,
      limit: 10,
      offset: 0,
    });
  });

  it("stores the request and the clicked row's position", async () => {
    renderList();
    await flush();
    await act(async () => {
      identifierChip("Unlinked").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(recallListContext("fake-entity-with-link", "2")).toMatchObject({ index: 1, params: { organizationId: 7 } });
    expect(recallListContext("fake-entity-with-link", "1")).toBeUndefined();
  });

  it("keeps the scope of a relation tab, so the walk stays inside it", async () => {
    renderList({ scope: { entityType: "project", id: 5 } });
    await flush();
    await act(async () => {
      identifierChip("Linked").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(recallListContext("fake-entity-with-link", "1")).toMatchObject({
      index: 0,
      params: { scope: { entityType: "project", id: 5 } },
    });
  });
});

// A read-only field (the row's project, a generated identifier) is shown but never edited, and the
// project column is shown by default only where rows come from several projects.
describe("EntityListPanel read-only and project columns", () => {
  const projectField = {
    id: "-305",
    resourceType: "fields",
    label: "Projet",
    answerType: "SELECT_ONE_ACTION_UNIT",
    isSystemField: true,
    valueBinding: "actionUnit",
    readOnly: true,
  };

  beforeEach(() => {
    try {
      window.localStorage.clear();
    } catch {
      // no storage: nothing saved to clear
    }
    schemaListMock.mockReset();
    schemaLoadMock.mockReset();
    schemaLoadMock.mockResolvedValue({
      fields: { "-305": projectField },
      columns: [{ fieldId: "-305", visible: false, order: 0 }],
    });
    schemaListMock.mockResolvedValue({
      data: [
        {
          id: "1",
          name: "Row A",
          answers: { "-305": { resourceId: "7", resourceType: "action-units", label: "OA-7" } },
          _permissions: { canEdit: true },
        },
      ],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
  });

  function render(scope?: { entityType: string; id: string | number }) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType="fake-schema-entity" scope={scope} />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  it("shows the project on an organization-wide list, as a cell that can't be edited", async () => {
    render();
    await flush();
    await flush();

    expect(container.textContent).toContain("OA-7");
    expect(await openedOverlayMode()).toBe("readonly");
  });

  it("hides the project by default inside a project's own tab", async () => {
    render({ entityType: "project", id: "7" });
    await flush();
    await flush();

    expect(container.textContent).not.toContain("OA-7");
  });
});

// The catalog's field rules (FieldResource.rules) on a list, which has no layout: dependencies are
// requested with the rows, and a cell the rules disable is greyed / flagged per row.
describe("EntityListPanel with catalog rules (FieldResource.rules)", () => {
  const NATURE = "-307";
  const EROSION = "-311";
  const OPENING = "-303";
  const CLOSING = "-318";
  const erosionConcept = { vocabularyExtId: "th252", conceptExtId: "4287639", conceptId: "77" };

  const ruleCatalog = {
    fields: {
      [NATURE]: { id: NATURE, resourceType: "fields", label: "Nature", answerType: "SELECT_ONE_FROM_FIELD_CODE", isSystemField: false, fieldCode: "SIARU.GEOMORPHO" },
      [EROSION]: {
        id: EROSION,
        resourceType: "fields",
        label: "Forme",
        answerType: "TEXT",
        isSystemField: false,
        rules: { enabledWhen: { fieldId: NATURE, op: "EQ", values: [erosionConcept] } },
      },
      [OPENING]: { id: OPENING, resourceType: "fields", label: "Ouverture", answerType: "DATETIME", isSystemField: false },
      [CLOSING]: {
        id: CLOSING,
        resourceType: "fields",
        label: "Fermeture",
        answerType: "DATETIME",
        isSystemField: false,
        rules: { constraints: [{ op: "GTE", fieldId: OPENING }] },
      },
    },
    // Only the erosion and closing columns are on screen: what they depend on is not.
    columns: [
      { fieldId: NATURE, columnId: NATURE, visible: false, order: 0 },
      { fieldId: EROSION, columnId: EROSION, visible: true, order: 1 },
      { fieldId: OPENING, columnId: OPENING, visible: false, order: 2 },
      { fieldId: CLOSING, columnId: CLOSING, visible: true, order: 3 },
    ],
  };

  beforeEach(() => {
    schemaListMock.mockReset();
    schemaLoadMock.mockReset();
    schemaLoadMock.mockResolvedValue(ruleCatalog);
  });

  function renderRules() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel entityType="fake-schema-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
  }

  function rowWith(answers: Record<string, unknown>) {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers, _permissions: { canEdit: true } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
  }

  function cellOf(text: string): HTMLElement | null {
    return (
      (Array.from(container.querySelectorAll(".entity-list-panel-editable-cell, .entity-list-panel-cell-value")).find((el) =>
        el.textContent?.includes(text),
      ) as HTMLElement | undefined) ?? null
    );
  }

  it("requests the fields the visible cells' rules read, though their columns are hidden", async () => {
    rowWith({});
    renderRules();
    await flush();
    await flush();

    const fields = (schemaListMock.mock.calls[0][0] as { fields: string }).fields.split(",").sort();
    // The nature (erosion's enabledWhen) and the opening date (bounds the closing date) ride along.
    expect(fields).toEqual([CLOSING, EROSION, NATURE, OPENING].sort());
  });

  it("greys a cell whose rule is false for that row, and flags the value it still holds", async () => {
    rowWith({ [NATURE]: { resourceId: "78", resourceType: "concepts" }, [EROSION]: "en V" });
    renderRules();
    await flush();
    await flush();

    const cell = cellOf("en V")!;
    expect(cell.className).toContain("entity-list-panel-cell-disabled");
    expect(cell.className).toContain("entity-list-panel-cell-incoherent");
    expect(cell.querySelector(".entity-list-panel-cell-warning")).not.toBeNull();
  });

  it("leaves the cell alone when the rule holds for the row", async () => {
    rowWith({ [NATURE]: { resourceId: "77", resourceType: "concepts" }, [EROSION]: "en V" });
    renderRules();
    await flush();
    await flush();

    const cell = cellOf("en V")!;
    expect(cell.className).not.toContain("entity-list-panel-cell-disabled");
    expect(cell.querySelector(".entity-list-panel-cell-warning")).toBeNull();
  });

  it("flags a closing date before the opening one, which is a hidden column", async () => {
    rowWith({ [NATURE]: { resourceId: "77", resourceType: "concepts" }, [OPENING]: "2024-05-10", [CLOSING]: "2024-05-01" });
    renderRules();
    await flush();
    await flush();

    const cell = cellOf("2024-05-01")!;
    expect(cell.querySelector(".entity-list-panel-cell-warning")).not.toBeNull();
    expect(cell.getAttribute("title")).toContain("« Ouverture »");
  });

  it("opens a disabled cell read-only, with « Vider » offered for the value the rules made incoherent", async () => {
    rowWith({ [NATURE]: { resourceId: "78", resourceType: "concepts" }, [EROSION]: "en V" });
    renderRules();
    await flush();
    await flush();

    await act(async () => {
      cellOf("en V")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(document.body.querySelector(".cell-edit-overlay-readonly")).toBeTruthy();
    expect(document.body.querySelector(".cell-edit-overlay-incoherent")?.textContent).toContain("ne s'applique plus");
    expect(document.body.querySelector(".cell-edit-overlay-clear")).toBeTruthy();
  });
});
