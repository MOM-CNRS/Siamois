import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import type {BulkApi} from "../../../entities/shared/bulkApi";
import type {EntityTypeConfig, FieldCatalog} from "../../../entities/shared/types";
import {registerDefaultFieldRenderers} from "../../../fields/registerDefaultRenderers";
import {notifySpy, taskToastSpy} from "../../../test/notifySpy";
import type {RowRecord} from "../useEntityListData";
import {BulkActionBar} from "./BulkActionBar";
import type {ListSelection} from "./useListSelection";

const bulkDelete = vi.fn<BulkApi["delete"]>();
const bulk: BulkApi = { delete: bulkDelete, patch: vi.fn() };

const config = {
  key: "bulk-entity",
  labels: { singular: "UE", plural: "UE" },
  collectionPath: "recording-units",
  icon: "bi bi-square",
  api: { list: vi.fn(), get: vi.fn(), delete: vi.fn(), patchAnswers: vi.fn(), bulk },
  list: { columns: [], searchable: false },
  detail: { tabs: [] },
  routes: { list: "/ue", detail: (id: string | number) => `/ue/${id}` },
} as unknown as EntityTypeConfig<unknown, unknown>;

const rows: RowRecord[] = [
  { id: "1", projectId: 5, _permissions: { canEdit: true, canDelete: true } },
  { id: "2", projectId: 5, _permissions: { canEdit: true, canDelete: true } },
  { id: "3", projectId: 5, _permissions: { canEdit: false, canDelete: false } },
];

let container: HTMLDivElement;
let root: Root;

/**
 * A selection of these rows: ticked one by one, or (`all`) the whole result set, read back by `resolve`.
 */
function selectionOf(selected: RowRecord[], all = false): ListSelection {
  return {
    all,
    count: selected.length,
    rows: all ? [] : selected,
    resolve: vi.fn().mockResolvedValue(selected),
    setRows: vi.fn(),
    setAll: vi.fn(),
    clear: vi.fn(),
  };
}

/**
 * Renders the bar over this selection.
 */
function render(
  selection: ListSelection,
  entityConfig: EntityTypeConfig<unknown, unknown> = config,
  catalog?: FieldCatalog,
) {
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <BulkActionBar entityType="recordingUnit" config={entityConfig} organizationId={3} selection={selection}
          catalog={catalog} />
      </QueryClientProvider>,
    );
  });
}

/**
 * The button labelled `label`, wherever it is drawn (the dialogs are portalled to the body), or null.
 */
function findButton(label: string): HTMLButtonElement | null {
  return [...document.querySelectorAll("button")].find((b) => b.textContent?.trim() === label) ?? null;
}

/**
 * The button labelled `label`; fails when there is none.
 */
function button(label: string): HTMLButtonElement {
  const found = findButton(label);
  if (!found) throw new Error(`No button « ${label} »`);
  return found;
}

/**
 * Clicks and lets the promises it started settle.
 */
async function click(el: HTMLElement) {
  await act(async () => {
    el.click();
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeAll(() => registerDefaultFieldRenderers());

beforeEach(() => {
  vi.clearAllMocks();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("BulkActionBar", () => {
  it("stays hidden while nothing is selected", () => {
    render(selectionOf([]));

    expect(container.querySelector(".bulk-action-bar")).toBeNull();
  });

  it("stays hidden for an entity type with no bulk endpoints", () => {
    render(selectionOf(rows), { ...config, api: { ...config.api, bulk: undefined } });

    expect(container.querySelector(".bulk-action-bar")).toBeNull();
  });

  it("counts the selection, the rows not shown included", () => {
    render({ ...selectionOf(rows, true), count: 250 });

    expect(container.querySelector(".bulk-action-bar-count")?.textContent).toBe("250 sélectionnées");
  });

  it("has no separate documents button", () => {
    render(selectionOf(rows));

    expect(findButton("Documents")).toBeNull();
  });

  it("lists the documents among the relations of « Modifier » for a type documents link to", async () => {
    const withDocuments = { ...config, api: { ...config.api, bulk: { ...bulk, linkDocument: vi.fn() } } };
    render(selectionOf(rows), withDocuments);

    await click(button("Modifier"));

    const relations = document.querySelector('[role="group"][aria-label="Relations"]');
    expect(relations?.textContent).toContain("Documents");
  });

  it("deletes the deletable rows in one bulk request and drops them from the selection", async () => {
    bulkDelete.mockResolvedValue({ succeeded: [1], failed: [{ id: 2, status: 409, message: "Relations" }] });
    const selection = selectionOf(rows);
    render(selection);

    await click(button("Supprimer"));
    await click(button("Supprimer définitivement"));

    expect(bulkDelete).toHaveBeenCalledWith(["1", "2"], expect.any(Function));
    expect(notifySpy.task).toHaveBeenCalledWith("Suppression en cours");
    expect(taskToastSpy.close).toHaveBeenCalled();
    expect(selection.setRows).toHaveBeenCalledWith([rows[1], rows[2]]);
    expect(notifySpy.warn).toHaveBeenCalledWith("1 réussi(s), 1 en échec. Relations");
  });

  it("acts on the whole result set under « select all », read back before the action", async () => {
    bulkDelete.mockResolvedValue({ succeeded: [1, 2], failed: [] });
    const selection = selectionOf(rows, true);
    render(selection);

    await click(button("Supprimer"));
    await click(button("Supprimer définitivement"));

    expect(selection.resolve).toHaveBeenCalled();
    expect(bulkDelete).toHaveBeenCalledWith(["1", "2"], expect.any(Function));
    expect(selection.setRows).not.toHaveBeenCalled();
  });

  it("greys a project-bound relation over several projects, and warns why when it is clicked", async () => {
    const catalog: FieldCatalog = {
      fields: {
        parents: { id: "parents", resourceType: "field", label: "UE parentes", answerType: "SELECT_MULTIPLE_RECORDING_UNIT", isSystemField: false },
      },
      columns: [],
    };
    const twoProjects = [rows[0], { id: "4", projectId: 6, _permissions: { canEdit: true, canDelete: true } }];
    render(selectionOf(twoProjects), config, catalog);

    await click(button("Modifier"));
    await click(button("UE parentes"));

    expect(button("UE parentes").getAttribute("aria-disabled")).toBe("true");
    expect(notifySpy.warn).toHaveBeenCalledWith(expect.stringContaining("plusieurs projets"));
    expect(document.querySelector(".bulk-dialog")).toBeNull();
  });

  it("says no row is editable rather than blaming the projects", async () => {
    const catalog: FieldCatalog = {
      fields: {
        parents: { id: "parents", resourceType: "field", label: "UE parentes", answerType: "SELECT_MULTIPLE_RECORDING_UNIT", isSystemField: false },
      },
      columns: [],
    };
    render(selectionOf([rows[2]]), config, catalog);

    await click(button("Modifier"));
    await click(button("UE parentes"));

    expect(notifySpy.warn).toHaveBeenCalledWith("Aucun des éléments sélectionnés n'est modifiable par vous.");
  });
});
