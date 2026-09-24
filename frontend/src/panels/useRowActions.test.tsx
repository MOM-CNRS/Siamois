import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { CreateFormContext, EntityTypeConfig, PagedResult } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";
import { WriteModeProvider } from "./writeMode";

vi.mock("../api/bookmarks", () => ({
  createBookmark: vi.fn().mockResolvedValue(undefined),
  deleteBookmark: vi.fn().mockResolvedValue(undefined),
  getBookmarkStatus: vi.fn().mockResolvedValue(false),
}));
import { createBookmark, deleteBookmark } from "../api/bookmarks";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// The row actions column as the generic mechanism it is — a throwaway entity with a duplicate
// API, one entity-specific row action and a create form, not any real entity's config.

interface FakeRow {
  id: string;
  name: string;
  resourceUri?: string;
  bookmarked?: boolean;
  _permissions?: { canEdit: boolean };
}

const listMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();
const duplicateMock = vi.fn<(id: string | number) => Promise<{ id: string | number }>>();
const createFormMock = vi.fn<(ctx: CreateFormContext) => void>();

const config: EntityTypeConfig<FakeRow, FakeRow> = {
  key: "row-action-entity",
  labels: { singular: "Chose", plural: "Choses" },
  collectionPath: "row-action-entities",
  icon: "bi bi-question",
  api: { list: listMock, get: vi.fn(), duplicate: duplicateMock },
  list: {
    columns: [{ key: "name", header: "Name", render: (row) => row.name, identifier: true }],
    searchable: false,
    createForm: (ctx) => {
      createFormMock(ctx);
      return (
        <button type="button" data-testid="row-create-submit" onClick={() => ctx.onCreated("77")}>
          Créer
        </button>
      );
    },
    rowActions: [
      {
        key: "new-child",
        icon: "bi bi-node-plus-fill",
        tooltip: "Créer un enfant",
        run: (row, ctx) =>
          ctx.openCreate("row-action-entity", {
            scope: { entityType: "project", id: 5 },
            prefill: { parent: { id: row.id, label: row.name } },
          }),
      },
    ],
  },
  detail: { tabs: [], chrome: (row) => ({ resourceUri: row.resourceUri ?? "", title: row.name }) },
  routes: { list: "/choses", detail: (id) => `/chose/${id}` },
};
registerEntityType(config);

let container: HTMLDivElement;
let root: Root;

function row(overrides: Partial<FakeRow> = {}): FakeRow {
  return { id: "1", name: "Row A", resourceUri: "/chose/1", bookmarked: false, _permissions: { canEdit: true }, ...overrides };
}

async function render(rows: FakeRow[], options: { writeMode?: boolean; onOpenOverview?: (t: string, id: string | number) => void } = {}) {
  listMock.mockResolvedValue({ data: rows, totalCount: rows.length, limit: 20, offset: 0 });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={options.writeMode ?? true}>
          <EntityListPanel entityType="row-action-entity" organizationId={3} onOpenOverview={options.onOpenOverview} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  await flush();
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

function actionButton(label: string): HTMLButtonElement | null {
  return container.querySelector(`.entity-list-panel-row-actions button[aria-label="${label}"]`);
}

async function click(el: Element) {
  await act(async () => {
    (el as HTMLElement).click();
  });
  await flush();
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

describe("list row actions", () => {
  it("bookmarks a row through the REST bookmarks, titled from the entity's own chrome", async () => {
    await render([row()]);

    await click(actionButton("Ajouter aux favoris")!);

    expect(createBookmark).toHaveBeenCalledWith({ resourceUri: "/chose/1", titleCode: "Row A", organizationId: 3 });
  });

  it("removes the bookmark of an already bookmarked row", async () => {
    await render([row({ bookmarked: true })]);

    await click(actionButton("Retirer des favoris")!);

    expect(deleteBookmark).toHaveBeenCalledWith("/chose/1", 3);
  });

  it("shows the bookmark to anyone, but the editing actions only in write mode on an editable row", async () => {
    await render([row({ _permissions: { canEdit: false } })]);
    expect(actionButton("Ajouter aux favoris")).not.toBeNull();
    expect(actionButton("Dupliquer")).toBeNull();
    expect(actionButton("Créer un enfant")).toBeNull();

    act(() => root.unmount());
    root = createRoot(container);
    await render([row()], { writeMode: false });
    expect(actionButton("Dupliquer")).toBeNull();
  });

  it("duplicates the row and opens the copy in the overview", async () => {
    duplicateMock.mockResolvedValue({ id: "42" });
    const onOpenOverview = vi.fn();
    await render([row()], { onOpenOverview });

    await click(actionButton("Dupliquer")!);

    expect(duplicateMock).toHaveBeenCalledWith("1");
    expect(onOpenOverview).toHaveBeenCalledWith("row-action-entity", "42");
  });

  it("opens the create dialog linked to the row, then opens what was created", async () => {
    const onOpenOverview = vi.fn();
    await render([row()], { onOpenOverview });

    await click(actionButton("Créer un enfant")!);

    const ctx = createFormMock.mock.calls[createFormMock.mock.calls.length - 1][0];
    expect(ctx.prefill).toEqual({ parent: { id: "1", label: "Row A" } });
    expect(ctx.scope).toEqual({ entityType: "project", id: 5 });

    await click(document.body.querySelector('[data-testid="row-create-submit"]')!);
    expect(onOpenOverview).toHaveBeenCalledWith("row-action-entity", "77");
  });
});
