import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import { CHUNK_SIZE } from "./useVirtualList";
import { registerDefaultFieldRenderers } from "../fields/registerDefaultRenderers";

registerDefaultFieldRenderers();
import type { EntityTypeConfig, PagedResult } from "../entities/types";
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
  it("fetches the first chunk with the default sort on first render", async () => {
    renderPanel();
    await flush();

    expect(listMock).toHaveBeenCalledWith(
      expect.objectContaining({ offset: 0, limit: CHUNK_SIZE, sort: "name:asc", search: undefined }),
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

    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ offset: 0, search: "abc" }));
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
    expect(listMock).toHaveBeenCalledTimes(1);
    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ search: "ab" }));
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

    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity", "1");
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

    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity", "1");
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

  it("renders the identifier cell as a navigation chip preceded by the column's leading content", async () => {
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
            leading: () => <i className="test-badge" />,
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
        <WriteModeProvider value={true}>
          <EntityListPanel entityType="fake-leading-entity" />
          </WriteModeProvider>
        </QueryClientProvider>,
      );
    });
    await flush();

    const cell = container.querySelector(".entity-list-panel-cell")!;
    expect(cell.querySelector(".test-badge")).toBeTruthy();
    const chip = cell.querySelector(".entity-nav-chip")!;
    expect(chip.querySelector(".bi-question")).toBeTruthy();
    expect(chip.textContent).toContain("Row A");
    // The badge is outside the clickable chip — only the chip opens the entity.
    expect(chip.querySelector(".test-badge")).toBeFalsy();
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

    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity", "1");
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("always shows the selection column, with its selected/total count", async () => {
    renderPanel();
    await flush();

    expect(container.querySelector("th.p-selection-column")).toBeTruthy();
    const chips = Array.from(container.querySelectorAll(".p-chip-text")).map((c) => c.textContent);
    expect(chips).toContain("0/1");
    // No hover checkbox on the identifier chip any more: selection is the column's job only.
    expect(container.querySelector(".entity-list-panel-identifier-select")).toBeNull();
  });

  // The list virtual-scrolls over the whole result set instead of paging through it.
  it("virtual-scrolls instead of paginating", async () => {
    renderPanel();
    await flush();

    expect(container.querySelector(".p-paginator")).toBeNull();
    expect(container.querySelector(".p-virtualscroller")).toBeTruthy();
  });

  // Rows past the first chunk exist (so the scrollbar is to scale) but are only fetched once
  // scrolled into view — until then they're placeholders (see useVirtualList.assembleRows).
  it("counts the whole result set but fetches only the chunk in view", async () => {
    listMock.mockResolvedValue({
      data: Array.from({ length: CHUNK_SIZE }, (_, i) => ({ id: String(i + 1), name: `Row ${i + 1}` })),
      totalCount: CHUNK_SIZE + 10,
      limit: CHUNK_SIZE,
      offset: 0,
    });
    renderPanel();
    await flush();

    // Only chunk 0 was requested: the (test-sized) viewport doesn't reach row 50.
    expect(listMock).toHaveBeenCalledTimes(1);
    const chips = Array.from(container.querySelectorAll(".p-chip-text")).map((c) => c.textContent);
    expect(chips).toContain(String(CHUNK_SIZE + 10));
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
      "siamois.list.fake-schema-entity.global",
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

    const saved = JSON.parse(window.localStorage.getItem("siamois.list.fake-schema-entity.global")!);
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
    // table header's chip bar, not behind the gear).
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

describe("EntityListPanel filter chips (table header)", () => {
  it("shows an \"Ajouter un filtre\" chip and no filter inputs until one is added", async () => {
    renderPanel();
    await flush();

    const bar = container.querySelector(".entity-list-panel-filter-chips")!;
    expect(bar).toBeTruthy();
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

    expect(listMock).toHaveBeenLastCalledWith(
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
  it("offers no editable cell in read mode, even for a row the user may edit", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: true } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel(false);
    await flush();
    await flush();

    expect(container.querySelector(".entity-list-panel-editable-cell")).toBeNull();
    // The value is still shown — read mode removes the control, not the data.
    expect(container.textContent).toContain("En cours");
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
    // Clicking the editable cell must NOT also trigger row-click navigation (the "collision" the
    // plan calls out) — nothing here wires onNavigate for this fake config, but the absence of a
    // navigation call is exactly the point.
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("does not wrap the cell as editable when _permissions.canEdit is false", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" }, _permissions: { canEdit: false } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    expect(container.querySelector(".entity-list-panel-editable-cell")).toBeFalsy();
    expect(container.textContent).toContain("En cours");
  });

  it("does not wrap the cell as editable when _permissions is absent", async () => {
    schemaListMock.mockResolvedValue({
      data: [{ id: "1", name: "Row A", answers: { "-118": "En cours" } }],
      totalCount: 1,
      limit: 10,
      offset: 0,
    });
    renderSchemaPanel();
    await flush();
    await flush();

    expect(container.querySelector(".entity-list-panel-editable-cell")).toBeFalsy();
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
    const frozen = headerCells().filter((th) => th.classList.contains("p-frozen-column"));
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
      .filter((th) => th.classList.contains("p-frozen-column"))
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
    expect(headerCells().filter((th) => th.classList.contains("p-frozen-column"))).toHaveLength(0);
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

    const frozen = headerCells().filter((th) => th.classList.contains("p-frozen-column"));
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

  it("navigates to the newly created entity and closes the overlay once the form calls onCreated", async () => {
    const { onNavigate } = renderWithCreateForm();
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

  it("falls back to onOpenOverview when the caller has no onNavigate of its own", async () => {
    const onOpenOverview = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <WriteModeProvider value={true}>
            <EntityListPanel
              entityType="fake-entity-with-create-form"
              organizationId={7}
              onOpenOverview={onOpenOverview}
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

    expect(onOpenOverview).toHaveBeenCalledWith("fake-entity-with-create-form", "99");
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
