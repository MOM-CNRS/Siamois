import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { RelationsList, RelationsListControls, RelationsListEnd } from "./RelationsList";
import type { RelationListModel } from "./useRelationList";
import type { RelationRow } from "./types";

const row = (kind: RelationRow["kind"], id: string, qualifier?: RelationRow["unit"]["qualifier"]): RelationRow => ({
  kind,
  unit: { resourceId: id, resourceType: "recording-units", label: `US ${id}`, qualifier },
});

function model(over: Partial<RelationListModel> = {}): RelationListModel {
  return {
    search: "",
    filters: {},
    sort: "kind:asc",
    offset: 0,
    limit: 25,
    rows: [row("parents", "1"), row("anterior", "2", { concept: { resourceId: "7", label: "coupe" }, uncertain: true })],
    total: 2,
    isLoading: false,
    isFetching: false,
    error: null,
    setSearch: vi.fn(),
    setFilters: vi.fn(),
    setSort: vi.fn(),
    setPage: vi.fn(),
    ...over,
  };
}

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("RelationsList", () => {
  it("shows each relation with its kind, its unit and its type", () => {
    act(() => root.render(<RelationsList list={model()} onOpen={vi.fn()} />));

    const text = container.textContent ?? "";
    expect(text).toContain("Type de relation");
    expect(text).toContain("Parent");
    expect(text).toContain("US 1");
    expect(text).toContain("Antérieur");
    expect(text).toContain("coupe");
    expect(container.querySelector(".sia-relation-uncertain")).not.toBeNull();
  });

  it("opens a unit from its chip", () => {
    const onOpen = vi.fn();
    act(() => root.render(<RelationsList list={model()} onOpen={onOpen} />));

    act(() => {
      (container.querySelectorAll(".ref-chip-link")[1] as HTMLElement).click();
    });

    expect(onOpen).toHaveBeenCalledWith("2", "US 2");
  });

  it("offers no action to a reader", () => {
    act(() => root.render(<RelationsList list={model()} onOpen={vi.fn()} />));

    expect(container.querySelector("button[aria-label='Supprimer le lien']")).toBeNull();
  });

  it("lets a writer remove any relation, but edit only a stratigraphic one", () => {
    const editor = { edit: vi.fn(), remove: vi.fn() };
    act(() => root.render(<RelationsList list={model()} onOpen={vi.fn()} editor={editor} />));

    expect(container.querySelectorAll("button[aria-label='Supprimer le lien']")).toHaveLength(2);
    const edits = container.querySelectorAll("button[aria-label='Modifier le lien']");
    expect(edits).toHaveLength(1);

    act(() => (edits[0] as HTMLElement).click());
    expect(editor.edit).toHaveBeenCalledWith("anterior", expect.objectContaining({ resourceId: "2" }), expect.anything());
    act(() => (container.querySelectorAll("button[aria-label='Supprimer le lien']")[0] as HTMLElement).click());
    expect(editor.remove).toHaveBeenCalledWith("parents", expect.objectContaining({ resourceId: "1" }), expect.anything());
  });

  it("shows placeholder rows while the first page loads, and says so when there is nothing", () => {
    act(() => root.render(<RelationsList list={model({ isLoading: true, rows: [] })} onOpen={vi.fn()} />));
    expect(container.querySelectorAll(".entity-list-panel-skeleton-row").length).toBeGreaterThan(0);

    act(() => root.render(<RelationsList list={model({ rows: [], total: 0 })} onOpen={vi.fn()} />));
    expect(container.textContent).toContain("Aucune relation.");
  });

  it("sorts on a header, then goes back to the first page", () => {
    const list = model();
    act(() => root.render(<RelationsList list={list} onOpen={vi.fn()} />));

    act(() => (container.querySelectorAll("th.p-sortable-column")[1] as HTMLElement).click());

    expect(list.setSort).toHaveBeenCalledWith("label:asc");
  });
});

describe("the list's toolbar controls", () => {
  it("count what matches and add by kind, for a writer only", () => {
    const editor = { add: vi.fn() };
    act(() => root.render(<RelationsListEnd list={model({ total: 12 })} editor={editor} />));
    expect(container.textContent).toContain("12");
    expect(container.textContent).toContain("Ajouter");

    act(() => root.render(<RelationsListEnd list={model({ total: 12 })} />));
    expect(container.textContent).not.toContain("Ajouter");
  });

  it("search by identifier and filter on the kind of relation", () => {
    act(() => root.render(<RelationsListControls list={model({ filters: { kind: { op: "in", v: ["anterior"] } } })} />));

    expect(container.querySelector("input[placeholder='Rechercher']")).not.toBeNull();
    expect(container.querySelector(".filter-chip-label")?.textContent).toBe("Type de relation");
    expect(container.querySelector(".filter-chip-value")?.textContent).toContain("Antérieur");
  });
});
