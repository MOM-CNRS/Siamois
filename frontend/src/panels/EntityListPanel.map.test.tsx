import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {registerEntityType} from "../entities/registry";
import type {CreateFormContext, FieldCatalog, ListParams, PagedResult} from "../entities/shared/types";
import type {EntityMapViewProps} from "./list/EntityMapView";
import {EntityListPanel, type EntityListPanelProps} from "./EntityListPanel";
import {WriteModeProvider} from "./pane/writeMode";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// The list's other ways to be shown — the map and the cards — as reached through the panel: which view is
// drawn, which geometry column the map places the rows by, how a row opens, and what a typed tab creates.

// Leaflet is not what is tested here: the map is a stand-in that records what the panel gave it.
const mapProps: EntityMapViewProps[] = [];
vi.mock("./list/EntityMapView", () => ({
  EntityMapView: (props: EntityMapViewProps) => {
    mapProps.push(props);
    return <div className="map-stub" data-geometry={props.geometry ?? ""} />;
  },
}));

interface Row {
  id: number;
  name: string;
}

const ENTITY = "map-panel-entity";
const PREFS_KEY = `siamois.list.org-.${ENTITY}.global`;
const listMock = vi.fn<(params: ListParams) => Promise<PagedResult<Row>>>();
const schemaLoadMock = vi.fn<() => Promise<FieldCatalog>>();
const prefills: CreateFormContext["prefill"][] = [];
const createContexts: CreateFormContext[] = [];
const last = <T,>(items: readonly T[]): T | undefined => items[items.length - 1];

const catalogWithGeometries: FieldCatalog = {
  fields: {
    "-1": { id: "-1", resourceType: "fields", label: "Nom", answerType: "TEXT", isSystemField: true },
    "-9": { id: "-9", resourceType: "fields", label: "Emprise fouillée", answerType: "GEOMETRY", isSystemField: false },
    "-5": { id: "-5", resourceType: "fields", label: "Géométrie", answerType: "GEOMETRY", isSystemField: true },
    "-7": { id: "-7", resourceType: "fields", label: "Type", answerType: "SELECT_ONE", isSystemField: true, valueBinding: "type" },
  },
  columns: [{ fieldId: "-1", columnId: "name", visible: true, order: 0 }],
} as unknown as FieldCatalog;

registerEntityType({
  key: ENTITY,
  labels: { singular: "M", plural: "Ms" },
  collectionPath: "map-panel-entities",
  icon: "bi bi-geo",
  api: { list: listMock, get: vi.fn() },
  list: {
    columns: [{ key: "name", header: "Name", render: (row: Row) => row.name, identifier: true }],
    schema: { load: schemaLoadMock },
    card: { subtitle: (row: Row) => row.name },
    mappable: true,
    defaultView: "map",
    createForm: (ctx: CreateFormContext) => {
      prefills.push(ctx.prefill);
      createContexts.push(ctx);
      return <div className="create-form-stub" />;
    },
  },
  detail: { tabs: [] },
  routes: { list: "/m", detail: (id: string | number) => `/m/${id}` },
} as never);

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

async function renderPanel(props: Partial<EntityListPanelProps> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={false}>
          <EntityListPanel entityType={ENTITY} {...props} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  await flush();
  await flush();
}

const lastMap = () => last(mapProps)!;
const mapStub = () => container.querySelector<HTMLElement>(".map-stub");
const gearTiles = (section: string) => {
  const group = [...document.body.querySelectorAll(".entity-list-panel-gear-panel [role=radiogroup]")].find(
    (g) => g.getAttribute("aria-label") === section,
  );
  return group ? [...group.querySelectorAll(".entity-list-panel-view-tile")] : [];
};
async function openGear() {
  await click(container.querySelector(".entity-list-panel-gear-button")!);
}
async function pickTile(section: string, label: string) {
  await openGear();
  await click(gearTiles(section).find((tile) => tile.textContent?.includes(label))!);
}
const savedPrefs = () => JSON.parse(window.localStorage.getItem(PREFS_KEY) ?? "{}");

beforeEach(() => {
  window.localStorage.clear();
  mapProps.length = 0;
  prefills.length = 0;
  createContexts.length = 0;
  listMock.mockReset().mockResolvedValue({ data: [{ id: 1, name: "Fosse 1" }], totalCount: 1, limit: 20, offset: 0 });
  schemaLoadMock.mockReset().mockResolvedValue(catalogWithGeometries);
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.innerHTML = "";
});

describe("EntityListPanel as a map", () => {
  it("opens on the map when the type starts there, instead of the table", async () => {
    await renderPanel();

    expect(mapStub()).not.toBeNull();
    expect(container.querySelector(".p-datatable")).toBeNull();
    expect(lastMap().entityType).toBe(ENTITY);
    expect(lastMap().params).toEqual(expect.objectContaining({ search: undefined }));
  });

  it("places the rows by the entity's own geometry by default", async () => {
    await renderPanel();

    expect(lastMap().geometry).toBe("-5");
  });

  it("places the rows by the geometry column remembered for this list", async () => {
    window.localStorage.setItem(PREFS_KEY, JSON.stringify({ v: 1, mapGeometry: "-9" }));

    await renderPanel();

    expect(lastMap().geometry).toBe("-9");
  });

  it("falls back to the entity's own geometry when the remembered column is gone", async () => {
    window.localStorage.setItem(PREFS_KEY, JSON.stringify({ v: 1, mapGeometry: "-404" }));

    await renderPanel();

    expect(lastMap().geometry).toBe("-5");
  });

  it("passes no geometry column when the catalog has none", async () => {
    schemaLoadMock.mockResolvedValue({ ...catalogWithGeometries, fields: { "-1": catalogWithGeometries.fields["-1"] } });

    await renderPanel();

    expect(lastMap().geometry).toBeUndefined();
    await openGear();
    expect(gearTiles("Géométrie affichée")).toHaveLength(0);
  });

  it("offers the geometry columns in the gear, the entity's own first, and remembers the one picked", async () => {
    await renderPanel();

    await openGear();
    expect(gearTiles("Géométrie affichée").map((tile) => tile.textContent)).toEqual(["Géométrie", "Emprise fouillée"]);

    await click(gearTiles("Géométrie affichée")[1]);

    expect(lastMap().geometry).toBe("-9");
    expect(savedPrefs().mapGeometry).toBe("-9");
  });

  it("highlights the fiche open in the overview and opens rows there", async () => {
    const onOpenOverview = vi.fn();
    const onNavigate = vi.fn();

    await renderPanel({ onOpenOverview, onNavigate, overviewEntityId: 7 });

    expect(lastMap().onOpenOverview).toBe(onOpenOverview);
    expect(lastMap().onNavigate).toBe(onNavigate);
    expect(lastMap().selectedId).toBe(7);
  });

  it("in focus mode, opens rows in the main pane and mirrors no overview", async () => {
    window.localStorage.setItem(PREFS_KEY, JSON.stringify({ v: 1, openMode: "focus" }));

    await renderPanel({ onOpenOverview: vi.fn(), onNavigate: vi.fn(), overviewEntityId: 7 });

    expect(lastMap().onOpenOverview).toBeUndefined();
    expect(lastMap().selectedId).toBeUndefined();
  });

  it("switches to focus mode from the gear", async () => {
    await renderPanel({ onOpenOverview: vi.fn(), onNavigate: vi.fn(), overviewEntityId: 7 });

    await openGear();
    const focusTile = gearTiles("Ouverture des pages").find((tile) => !tile.classList.contains("is-active"))!;
    await click(focusTile);

    expect(savedPrefs().openMode).toBe("focus");
    expect(lastMap().onOpenOverview).toBeUndefined();
  });

  it("leaves the map for the table from the gear, and hides the geometry choice there", async () => {
    await renderPanel();

    await pickTile("Disposition", "Liste");

    expect(mapStub()).toBeNull();
    expect(container.querySelector(".p-datatable")).not.toBeNull();
    await openGear();
    expect(gearTiles("Géométrie affichée")).toHaveLength(0);
  });

  describe("switching to the map", () => {
    // The server filters the geometries on having one (filterOp « presence »).
    const filterableGeometries = {
      ...catalogWithGeometries,
      fields: {
        ...catalogWithGeometries.fields,
        "-5": { ...catalogWithGeometries.fields["-5"], query: { sortable: false, filterOp: "presence" } },
        "-9": { ...catalogWithGeometries.fields["-9"], query: { sortable: false, filterOp: "presence" } },
      },
      // Table columns, as every geometry field is: the filter bar offers the catalog's columns.
      columns: [
        ...catalogWithGeometries.columns,
        { fieldId: "-5", columnId: "geom", visible: false, order: 1 },
        { fieldId: "-9", columnId: "footprint", visible: false, order: 2 },
      ],
    } as unknown as FieldCatalog;
    const chipValues = () => [...container.querySelectorAll(".filter-chip-value")].map((el) => el.textContent);

    it("filters the drawn geometry on « n'est pas vide »", async () => {
      schemaLoadMock.mockResolvedValue(filterableGeometries);
      await renderPanel();
      // Opening on the map is no switch: nothing is added.
      expect(lastMap().params.filters).toBeUndefined();

      await pickTile("Disposition", "Liste");
      await pickTile("Disposition", "Carte");

      expect(lastMap().params.filters).toEqual({ "-5": { op: "notEmpty" } });
      expect(chipValues()).toContain("n'est pas vide");
    });

    it("shows every row again once the filter is taken off, and adds it back only on the next switch", async () => {
      schemaLoadMock.mockResolvedValue(filterableGeometries);
      await renderPanel();
      await pickTile("Disposition", "Liste");
      await pickTile("Disposition", "Carte");

      await click(container.querySelector("[data-chip-key='-5'] .filter-chip-remove")!);

      expect(lastMap().params.filters).toBeUndefined();
      expect(mapStub()).not.toBeNull();
    });

    it("keeps a filter the user already set on that geometry", async () => {
      schemaLoadMock.mockResolvedValue(filterableGeometries);
      await renderPanel({ initialFilters: { "-5": { op: "empty" } } });
      await pickTile("Disposition", "Liste");
      await pickTile("Disposition", "Carte");

      expect(lastMap().params.filters).toEqual({ "-5": { op: "empty" } });
    });

    it("adds nothing on a geometry the server cannot filter", async () => {
      await renderPanel();
      await pickTile("Disposition", "Liste");
      await pickTile("Disposition", "Carte");

      expect(lastMap().params.filters).toBeUndefined();
    });
  });

  it("shows the rows as cards from the gear", async () => {
    await renderPanel();

    await pickTile("Disposition", "Vignettes");

    expect(mapStub()).toBeNull();
    expect(container.querySelector(".entity-card-grid")).not.toBeNull();
    expect(container.textContent).toContain("Fosse 1");
  });

  it("goes back to the first page when the view changes", async () => {
    await renderPanel();

    await pickTile("Disposition", "Liste");

    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ offset: 0 }));
  });
});

describe("EntityListPanel creating from a typed tab", () => {
  async function openCreate(props: Partial<EntityListPanelProps>) {
    window.localStorage.setItem(`siamois.list.org-.${ENTITY}.project-tab`, JSON.stringify({ v: 1, viewMode: "list" }));
    await renderPanel({ embedded: true, scope: { entityType: "project", id: 5 }, ...props });
    const create = [...container.querySelectorAll("button")].find((b) => b.textContent?.includes("Créer"))!;
    await click(create);
  }

  it("creates entities of the one type the tab is filtered on", async () => {
    await openCreate({ fixedFilters: { type: { op: "in", v: ["42"] } }, createPrefill: { project: { id: 5, label: "P" } } as never });

    expect(last(prefills)).toEqual({ project: { id: 5, label: "P" }, typeId: "42" });
  });

  it("recognizes the type field by its binding in the catalog", async () => {
    await openCreate({ fixedFilters: { "-7": { op: "in", v: ["8"] } } });

    expect(last(prefills)).toEqual({ typeId: "8" });
  });

  it("chooses no type when the tab is filtered on several", async () => {
    await openCreate({ fixedFilters: { type: { op: "in", v: ["42", "43"] } } });

    expect(last(prefills)).toBeUndefined();
  });

  it("keeps the caller's prefill untouched without a fixed type", async () => {
    const createPrefill = { project: { id: 5, label: "P" } } as never;

    await openCreate({ createPrefill });

    expect(last(prefills)).toBe(createPrefill);
  });
});

describe("EntityListPanel edge cases", () => {
  async function openCreate(props: Partial<EntityListPanelProps>) {
    window.localStorage.setItem(PREFS_KEY, JSON.stringify({ v: 1, viewMode: "list" }));
    await renderPanel(props);
    await click([...container.querySelectorAll("button")].find((b) => b.textContent?.includes("Créer"))!);
  }

  it("opens a created entity's fiche in the overview", async () => {
    const onOpenOverview = vi.fn();
    await openCreate({ onOpenOverview, onNavigate: vi.fn() });

    act(() => last(createContexts)!.onCreated("9"));

    expect(onOpenOverview).toHaveBeenCalledWith(ENTITY, "9", { tab: "fiche" });
  });

  it("falls back to navigating to a created entity without an overview", async () => {
    const onNavigate = vi.fn();
    await openCreate({ onNavigate });

    act(() => last(createContexts)!.onCreated("9"));

    expect(onNavigate).toHaveBeenCalledWith(ENTITY, "9", { tab: "fiche" });
  });

  it("does not fail when a created entity has nowhere to open", async () => {
    await openCreate({});

    expect(() => act(() => last(createContexts)!.onCreated("9"))).not.toThrow();
  });

  it("applies a seed without a search as an empty search", async () => {
    window.localStorage.setItem(PREFS_KEY, JSON.stringify({ v: 1, viewMode: "list" }));

    await renderPanel({ seed: { nonce: 1 } as never });

    expect(last(listMock.mock.calls)![0].search ?? "").toBe("");
  });
});

