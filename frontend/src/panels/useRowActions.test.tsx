import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { CreateFormContext, EntityTypeConfig, PagedResult } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";
import { WriteModeProvider } from "./writeMode";
import { listPrefsKey, saveListPrefs } from "./listPreferences";
import { onEntityDeleted } from "./entityDeleted";
import { ApiError } from "../api/client";

vi.mock("../api/bookmarks", () => ({
  createBookmark: vi.fn().mockResolvedValue(undefined),
  deleteBookmark: vi.fn().mockResolvedValue(undefined),
  getBookmarkStatus: vi.fn().mockResolvedValue(false),
}));
import { createBookmark, deleteBookmark } from "../api/bookmarks";


// The row actions column as the generic mechanism it is — a throwaway entity with a duplicate
// API, one entity-specific row action and a create form, not any real entity's config.

interface FakeRow {
  id: string;
  name: string;
  resourceUri?: string;
  bookmarked?: boolean;
  _permissions?: { canEdit: boolean; canDelete?: boolean };
}

const listMock = vi.fn<(params: unknown) => Promise<PagedResult<FakeRow>>>();
const duplicateMock = vi.fn<(id: string | number) => Promise<{ id: string | number }>>();
const deleteMock = vi.fn<(id: string | number) => Promise<void>>();
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
// The same entity type, deletable: apart so the shared action bar of the tests above stays what it was.
const deletableConfig: EntityTypeConfig<FakeRow, FakeRow> = {
  ...config,
  key: "row-delete-entity",
  collectionPath: "row-delete-entities",
  api: { list: listMock, get: vi.fn(), delete: deleteMock },
  list: { ...config.list, rowActions: [] },
};
registerEntityType(deletableConfig);

let container: HTMLDivElement;
let root: Root;

function row(overrides: Partial<FakeRow> = {}): FakeRow {
  return { id: "1", name: "Row A", resourceUri: "/chose/1", bookmarked: false, _permissions: { canEdit: true }, ...overrides };
}

// By default only the bookmark shows in a row; the tests of the actions themselves want them in the row, as a user
// who brought them back from the gear would have them (no action of the saved layout, so all are « added », inline).
async function render(
  rows: FakeRow[],
  options: { writeMode?: boolean; onOpenOverview?: (t: string, id: string | number) => void; entityType?: string; defaultLayout?: boolean } = {},
) {
  const prefsKey = listPrefsKey(options.entityType ?? "row-action-entity", undefined, 3);
  if (!options.defaultLayout && window.localStorage.getItem(prefsKey) == null) {
    saveListPrefs(prefsKey, { actionBar: { order: [], inline: [] } });
  }
  listMock.mockResolvedValue({ data: rows, totalCount: rows.length, limit: 20, offset: 0 });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={options.writeMode ?? true}>
          <EntityListPanel entityType={options.entityType ?? "row-action-entity"} organizationId={3} onOpenOverview={options.onOpenOverview} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  await flush();
}

// Twice, each in its own act(): the list waits for the user's saved state (GET /view-states) before its
// first fetch, and what that read changes is committed when its act ends.
async function flush() {
  for (let round = 0; round < 2; round++) {
    await act(async () => {
      for (let i = 0; i < 5; i++) {
        await new Promise((resolve) => setTimeout(resolve, 0));
      }
    });
  }
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
  window.localStorage.clear();
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

  it("opens the structure overlay instead of copying at once when the entity has one", async () => {
    const structure = { root: { id: "1", label: "Row A" }, descendants: [], truncated: false };
    const duplication = {
      load: vi.fn().mockResolvedValue(structure),
      run: vi.fn().mockResolvedValue({ copies: [{ id: "42", label: "Row B" }], createdCount: 1 }),
      unit: "Chose",
      maxCopies: 50,
    };
    registerEntityType({ ...config, key: "row-action-entity", duplication } as EntityTypeConfig<unknown, unknown>);
    const onOpenOverview = vi.fn();
    try {
      await render([row()], { onOpenOverview });

      await click(actionButton("Dupliquer")!);

      expect(duplicateMock).not.toHaveBeenCalled();
      expect(duplication.load).toHaveBeenCalledWith("1");
      const submit = [...document.querySelectorAll<HTMLButtonElement>(".sia-create-form-footer button")].find((b) => b.type === "submit")!;
      await act(async () => submit.click());
      await flush();

      expect(duplication.run).toHaveBeenCalledWith("1", { copies: 1, descendantIds: [] });
      expect(onOpenOverview).toHaveBeenCalledWith("row-action-entity", "42");
      expect(container.textContent).toContain("Chose Row B créée");
    } finally {
      registerEntityType(config as EntityTypeConfig<unknown, unknown>);
    }
  });

  it("opens the create form in an overlay linked to the row, then opens what was created", async () => {
    const onOpenOverview = vi.fn();
    await render([row()], { onOpenOverview });

    await click(actionButton("Créer un enfant")!);

    // Every creation opens next to what started it, never in a modal.
    const submit = document.body.querySelector('[data-testid="row-create-submit"]')!;
    expect(submit.closest(".p-overlaypanel")).toBeTruthy();
    expect(document.body.querySelector(".p-dialog")).toBeNull();

    const ctx = createFormMock.mock.calls[createFormMock.mock.calls.length - 1][0];
    expect(ctx.prefill).toEqual({ parent: { id: "1", label: "Row A" } });
    expect(ctx.scope).toEqual({ entityType: "project", id: 5 });

    await click(document.body.querySelector('[data-testid="row-create-submit"]')!);
    expect(onOpenOverview).toHaveBeenCalledWith("row-action-entity", "77");
  });

  it("puts actions taken out of the row into a \"…\" menu, keeping the bookmark inline", async () => {
    duplicateMock.mockResolvedValue({ id: "42" });
    window.localStorage.setItem(
      "siamois.list.org3.row-action-entity.global",
      JSON.stringify({ v: 1, actionBar: { order: ["new-child", "duplicate"], inline: ["new-child"] } }),
    );
    await render([row()]);

    expect(actionButton("Ajouter aux favoris")).not.toBeNull();
    expect(actionButton("Dupliquer")).toBeNull();
    const inline = Array.from(container.querySelectorAll(".entity-list-panel-row-actions button")).map((b) =>
      b.getAttribute("aria-label"),
    );
    expect(inline).toEqual(["Ajouter aux favoris", "Créer un enfant", "Plus d'actions"]);

    await click(actionButton("Plus d'actions")!);
    const entry = Array.from(document.body.querySelectorAll(".entity-list-panel-row-actions-menu .p-menuitem-link")).find((a) =>
      a.textContent?.includes("Dupliquer"),
    )!;
    expect(entry).toBeTruthy();
    await click(entry);

    expect(duplicateMock).toHaveBeenCalledWith("1");
  });

  it("follows the saved order and shows a newly added action inline", async () => {
    window.localStorage.setItem(
      "siamois.list.org3.row-action-entity.global",
      JSON.stringify({ v: 1, actionBar: { order: ["new-child", "gone"], inline: ["new-child", "gone"] } }),
    );
    await render([row()]);

    const inline = Array.from(container.querySelectorAll(".entity-list-panel-row-actions button")).map((b) =>
      b.getAttribute("aria-label"),
    );
    // "gone" no longer exists; "duplicate" wasn't in the saved layout, so it's appended inline.
    expect(inline).toEqual(["Ajouter aux favoris", "Créer un enfant", "Dupliquer"]);
    expect(actionButton("Plus d'actions")).toBeNull();
  });

  it("shows only the bookmark in a row by default, every other action put away in the menu", async () => {
    await render([row()], { defaultLayout: true });

    expect(container.querySelector('.entity-list-panel-row-actions button[aria-label="Dupliquer"]')).toBeNull();
    expect(actionButton("Dupliquer")).toBeNull();
    expect(container.querySelector(".entity-list-panel-row-actions .bi-bookmark")).not.toBeNull();
    expect(actionButton("Plus d'actions")).not.toBeNull();
  });

  it("offers the action bar settings from the gear, even without a column catalog", async () => {
    await render([row()]);

    await click(container.querySelector(".entity-list-panel-gear-button")!);
    const entry = Array.from(document.body.querySelectorAll(".entity-list-panel-gear-menu .p-menuitem-link")).find((a) =>
      a.textContent?.includes("Barre d'actions"),
    )!;
    expect(entry).toBeTruthy();
    await click(entry);

    const settings = document.body.querySelector(".entity-list-panel-action-bar-settings")!;
    // The bookmark is listed, locked on; the others can be switched into the menu.
    expect(settings.querySelector(".is-locked")!.textContent).toContain("Favori");
    const duplicateItem = settings.querySelector('[data-id="duplicate"]')!;
    await click(duplicateItem.querySelector(".p-inputswitch input")!);

    expect(actionButton("Dupliquer")).toBeNull();
    expect(actionButton("Plus d'actions")).not.toBeNull();
    const saved = JSON.parse(window.localStorage.getItem("siamois.list.org3.row-action-entity.global")!);
    expect(saved.actionBar).toEqual({ order: ["new-child", "duplicate"], inline: ["new-child"] });
  });

  it("shows the bin only on a row the caller may delete, whatever the edit right", async () => {
    await render([row({ id: "1" }), row({ id: "2", name: "Row B", _permissions: { canEdit: false, canDelete: true } })], { entityType: "row-delete-entity" });

    expect(container.querySelectorAll('.entity-list-panel-row-actions button[aria-label="Supprimer"]').length).toBe(1);
    // The row that can only be deleted carries no editing action.
    const rows = container.querySelectorAll("tbody tr");
    expect(rows[1].querySelectorAll(".entity-list-panel-row-actions button").length).toBe(2);
    expect(rows[1].querySelector('button[aria-label="Supprimer"]')).not.toBeNull();
  });

  it("hides the bin outside write mode", async () => {
    await render([row({ _permissions: { canEdit: true, canDelete: true } })], { writeMode: false, entityType: "row-delete-entity" });

    expect(actionButton("Supprimer")).toBeNull();
  });

  it("deletes after the confirmation, refreshes the list and announces the deletion", async () => {
    deleteMock.mockResolvedValue(undefined);
    const announced = vi.fn();
    const stop = onEntityDeleted(announced);
    await render([row({ _permissions: { canEdit: true, canDelete: true } })], { entityType: "row-delete-entity" });

    await click(actionButton("Supprimer")!);
    expect(deleteMock).not.toHaveBeenCalled();
    const confirm = [...document.querySelectorAll<HTMLButtonElement>(".delete-entity-dialog button")]
      .find((b) => b.textContent?.includes("Supprimer définitivement"))!;
    await click(confirm);

    expect(deleteMock).toHaveBeenCalledWith("1");
    expect(announced).toHaveBeenCalledWith("row-delete-entity", "1");
    stop();
  });

  it("keeps the dialog open with the server's refusal when the entity still has relationships", async () => {
    deleteMock.mockRejectedValue(new ApiError(409, "Impossible de supprimer : lié à des documents. Retirez d'abord ces relations."));
    const announced = vi.fn();
    const stop = onEntityDeleted(announced);
    await render([row({ _permissions: { canEdit: true, canDelete: true } })], { entityType: "row-delete-entity" });

    await click(actionButton("Supprimer")!);
    await click([...document.querySelectorAll<HTMLButtonElement>(".delete-entity-dialog button")]
      .find((b) => b.textContent?.includes("Supprimer définitivement"))!);

    expect(document.querySelector(".delete-entity-dialog-error")?.textContent).toContain("Retirez d'abord");
    expect(announced).not.toHaveBeenCalled();
    stop();
  });

  it("reports any other failure in the dialog", async () => {
    deleteMock.mockRejectedValue(new ApiError(500, "boom"));
    await render([row({ _permissions: { canEdit: true, canDelete: true } })], { entityType: "row-delete-entity" });

    await click(actionButton("Supprimer")!);
    await click([...document.querySelectorAll<HTMLButtonElement>(".delete-entity-dialog button")]
      .find((b) => b.textContent?.includes("Supprimer définitivement"))!);

    expect(document.querySelector(".delete-entity-dialog-error")).not.toBeNull();
  });
});
