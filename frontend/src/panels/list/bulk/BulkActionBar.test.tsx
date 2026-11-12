import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {bulkDelete} from "../../../entities/shared/bulkApi";
import type {EntityTypeConfig} from "../../../entities/shared/types";
import {notifySpy} from "../../../test/notifySpy";
import type {RowRecord} from "../useEntityListData";
import {BulkActionBar} from "./BulkActionBar";

vi.mock("../../../entities/shared/bulkApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../entities/shared/bulkApi")>()),
  bulkDelete: vi.fn(),
  bulkPatch: vi.fn(),
  bulkLinkDocument: vi.fn(),
}));
const mockedBulkDelete = vi.mocked(bulkDelete);

const config = {
  key: "bulk-entity",
  labels: { singular: "UE", plural: "UE" },
  collectionPath: "recording-units",
  icon: "bi bi-square",
  api: { list: vi.fn(), get: vi.fn(), delete: vi.fn() },
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
const onSelectionChange = vi.fn();

/**
 * Renders the bar over these selected rows.
 */
function render(selected: RowRecord[]) {
  const queryClient = new QueryClient();
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <BulkActionBar entityType="recordingUnit" config={config} organizationId={3} selectedRows={selected}
          onSelectionChange={onSelectionChange} />
      </QueryClientProvider>,
    );
  });
}

/**
 * The button labelled `label`, wherever it is drawn (the dialogs are portalled to the body).
 */
function button(label: string): HTMLButtonElement {
  const found = [...document.querySelectorAll("button")].find((b) => b.textContent?.trim() === label);
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
    render([]);

    expect(container.querySelector(".bulk-action-bar")).toBeNull();
  });

  it("counts the selection", () => {
    render(rows);

    expect(container.querySelector(".bulk-action-bar-count")?.textContent).toBe("3 sélectionnées");
  });

  it("deletes the deletable rows in one bulk request and drops them from the selection", async () => {
    mockedBulkDelete.mockResolvedValue({ succeeded: [1], failed: [{ id: 2, status: 409, message: "Relations" }] });
    render(rows);

    await click(button("Supprimer"));
    await click(button("Supprimer définitivement"));

    expect(mockedBulkDelete).toHaveBeenCalledWith("recording-units", ["1", "2"]);
    expect(onSelectionChange).toHaveBeenCalledWith([rows[1], rows[2]]);
    expect(notifySpy.warn).toHaveBeenCalledWith("1 réussi(s), 1 en échec. Relations");
  });
});
