import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterEach, describe, expect, it, vi} from "vitest";
import {registerEntityType} from "../entities/registry";
import type {EntityTypeConfig} from "../entities/shared/types";
import {WriteModeProvider} from "../panels/pane/writeMode";
import {FieldEditCell} from "./FieldEditCell";
import type {FieldResource} from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./GeometryMapField", () => ({
  GeometryMapField: ({ wkt, entityId, editable, focusSource }: {
    wkt: string | null;
    entityId: string;
    editable: boolean;
    focusSource: { placeIds: string[]; projectId?: string };
  }) => (
    <span className="geometry-map-field-stub" data-focus={`${focusSource.placeIds.join(",")}|${focusSource.projectId}`}>
      {`${entityId}:${wkt}:${editable}`}
    </span>
  ),
}));

function config(key: string, mappable: boolean, drawable = false): EntityTypeConfig {
  return {
    key,
    labels: { singular: key, plural: key },
    collectionPath: key,
    icon: "bi bi-question",
    api: {
      list: async () => ({ data: [], totalCount: 0, limit: 10, offset: 0 }),
      get: async () => ({}),
      ...(drawable ? { patchGeometry: async () => ({}) } : {}),
    },
    list: { columns: [], searchable: false, mappable },
    detail: { tabs: [] },
    routes: { list: `/${key}`, detail: (id: string | number) => `/${key}/${id}` },
  } as unknown as EntityTypeConfig;
}

const geometryField: FieldResource = { id: "-40", resourceType: "fields", label: "Géométrie", answerType: "GEOMETRY", isSystemField: true, readOnly: true };
const WKT = "POLYGON ((0 0, 1 0, 1 1, 0 0))";

let root: Root | undefined;
let host: HTMLElement | undefined;

afterEach(() => {
  act(() => root?.unmount());
  host?.remove();
});

function renderCell(entityType: string, stored: unknown, writable = false) {
  host = document.createElement("div");
  document.body.appendChild(host);
  root = createRoot(host);
  act(() =>
    root!.render(
      <WriteModeProvider value={writable}>
        {/* readOnly as the fiche passes it (!canEdit || column read-only): the geometry column is editable. */}
        <FieldEditCell field={geometryField} row={{ id: "12" }} stored={stored} readOnly={!writable} entityType={entityType} onSave={async () => undefined} onSaved={() => {}} />
      </WriteModeProvider>,
    ),
  );
  return host;
}

function stubText(container: HTMLElement) {
  return container.querySelector(".geometry-map-field-stub")?.textContent;
}

describe("FieldEditCell on a geometry", () => {
  it("shows a placeable entity's geometry as the map chip, from the answer envelope the API sends", () => {
    registerEntityType(config("mappableThing", true));
    const answer = { answerType: "GEOMETRY", value: WKT };
    expect(renderCell("mappableThing", answer).querySelector(".geometry-map-field-stub")?.textContent).toBe(`12:${WKT}:false`);
  });

  it("shows the chip for a bare WKT too (an entity property)", () => {
    registerEntityType(config("mappableThing", true));
    expect(renderCell("mappableThing", WKT).querySelector(".geometry-map-field-stub")?.textContent).toBe(`12:${WKT}:false`);
  });

  it("keeps the plain value for an entity type without a map, or an empty geometry", () => {
    registerEntityType(config("plainThing", false));
    expect(renderCell("plainThing", WKT).querySelector(".geometry-map-field-stub")).toBeNull();
    act(() => root?.unmount());
    host?.remove();
    expect(renderCell("mappableThing", { answerType: "GEOMETRY", value: null }).querySelector(".geometry-map-field-stub")).toBeNull();
  });

  it("offers to draw the geometry of an entity that has none, in write mode, though the field itself is read-only as an answer", () => {
    registerEntityType(config("drawableThing", true, true));
    expect(stubText(renderCell("drawableThing", { answerType: "GEOMETRY", value: null }, true))).toBe("12:null:true");
  });

  it("keeps an empty geometry a dash outside write mode", () => {
    registerEntityType(config("drawableThing", true, true));
    expect(stubText(renderCell("drawableThing", { answerType: "GEOMETRY", value: null }, false))).toBeUndefined();
  });

  it("offers no drawing when the fiche makes the field read-only (no edit right, read-only column)", () => {
    registerEntityType(config("drawableThing", true, true));
    host = document.createElement("div");
    document.body.appendChild(host);
    root = createRoot(host);
    act(() =>
      root!.render(
        <WriteModeProvider value>
          <FieldEditCell
            field={geometryField}
            row={{ id: "12" }}
            stored={{ answerType: "GEOMETRY", value: WKT }}
            readOnly
            entityType="drawableThing"
            onSave={async () => undefined}
            onSaved={() => {}}
          />
        </WriteModeProvider>,
      ),
    );
    expect(stubText(host)).toBe(`12:${WKT}:false`);
  });

  it("hands the editor the fiche's place and project, to frame the map on", () => {
    registerEntityType(config("drawableThing", true, true));
    host = document.createElement("div");
    document.body.appendChild(host);
    root = createRoot(host);
    const row = {
      id: "12",
      projectId: "5",
      answers: { "-304": { answerType: "SELECT_ONE_SPATIAL_UNIT", value: { resourceType: "places", id: "7" } } },
    };
    act(() =>
      root!.render(
        <WriteModeProvider value>
          <FieldEditCell field={geometryField} row={row} stored={null} readOnly={false} entityType="drawableThing" onSave={async () => undefined} onSaved={() => {}} />
        </WriteModeProvider>,
      ),
    );
    expect(host.querySelector(".geometry-map-field-stub")?.getAttribute("data-focus")).toBe("7|5");
  });
});
