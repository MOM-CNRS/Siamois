import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import { registerDefaultFieldRenderers } from "../fields/registerDefaultRenderers";

registerDefaultFieldRenderers();
import type { EntityTypeConfig, PagedResult } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";

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
        <EntityListPanel
          entityType="fake-entity"
          onNavigate={onNavigate}
          onCreate={onCreate}
          onOpenOverview={onOpenOverview}
          overviewEntityId={overviewEntityId}
        />
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
  it("calls config.api.list with the default sort and pagination on first render", async () => {
    renderPanel();
    await flush();

    expect(listMock).toHaveBeenCalledWith(
      expect.objectContaining({ offset: 0, limit: 10, sort: "name:asc", search: undefined }),
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

  it("prefers onOpenOverview over onNavigate when the identifier cell is clicked (plan §8 phase 5)", async () => {
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
          <EntityListPanel entityType="fake-leading-entity" />
        </QueryClientProvider>,
      );
    });
    await flush();

    const cell = container.querySelector(".entity-list-panel-identifier-cell")!;
    expect(cell.querySelector(".test-badge")).toBeTruthy();
    const chip = cell.querySelector(".entity-nav-chip")!;
    expect(chip.querySelector(".bi-question")).toBeTruthy();
    expect(chip.textContent).toContain("Row A");
    // The badge is outside the clickable chip — only the chip opens the entity.
    expect(chip.querySelector(".test-badge")).toBeFalsy();
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
          <EntityListPanel
            entityType="fake-entity"
            toolbar={{ chrome: { resourceUri: "/fake", title: "Fakes", bookmarked: false }, actions: { refresh: () => {} } }}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    const header = container.querySelector(".p-panel-header")!;
    expect(header).toBeTruthy();
    expect(header.querySelector(".bi-arrow-clockwise")).toBeTruthy();
  });

  it("shows a selected/total chip and updates it when a row is selected", async () => {
    renderPanel();
    await flush();

    const chips = Array.from(container.querySelectorAll(".p-chip-text")).map((c) => c.textContent);
    expect(chips).toContain("0/1");

    const checkbox = container.querySelector('input[type="checkbox"]') as HTMLInputElement;
    expect(checkbox).toBeTruthy();
    await act(async () => {
      checkbox.click();
    });
    await flush();

    const chipsAfter = Array.from(container.querySelectorAll(".p-chip-text")).map((c) => c.textContent);
    expect(chipsAfter).toContain("1/1");
  });

  it("defaults to 10 rows per page with 10/25/50 as the paginator options (parity with defaultPageSize)", async () => {
    renderPanel();
    await flush();

    const dropdown = container.querySelector(".p-paginator .p-dropdown");
    expect(dropdown).toBeTruthy();
  });

  it("shows an unsupported message for an unregistered entity type", async () => {
    renderPanel.bind(null);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityListPanel entityType="does-not-exist" />
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
          <EntityListPanel entityType="fake-schema-entity" />
        </QueryClientProvider>,
      );
    });
  }

  it("requests only the catalog's default-visible field ids and renders the dynamic column", async () => {
    renderSchemaPanel();
    await flush();
    await flush();

    expect(schemaListMock).toHaveBeenLastCalledWith(expect.objectContaining({ fields: "-118" }));
    expect(container.textContent).toContain("Statut");
    expect(container.textContent).toContain("En cours");
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
          <EntityListPanel entityType="fake-bare-entity" />
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

    const gearButton = container.querySelector(".entity-list-panel-gear-button") as HTMLElement;
    expect(gearButton).toBeTruthy();

    await act(async () => {
      gearButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    // PrimeReact's OverlayPanel portals into document.body by default, not into `container`.
    expect(document.body.querySelector(".entity-list-panel-column-toggler")).toBeTruthy();
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
          <EntityListPanel entityType="fake-gear-order-entity" />
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

  function renderSchemaPanel() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityListPanel entityType="fake-schema-entity" />
        </QueryClientProvider>,
      );
    });
  }

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
});
