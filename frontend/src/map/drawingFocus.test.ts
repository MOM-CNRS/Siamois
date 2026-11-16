import {describe, expect, it, vi} from "vitest";
import {type FocusLoaders, inseeCodeOf, type PlaceLike, resolveDrawingFocus, wgs84GeometryOf, widest} from "./drawingFocus";

const square = (size: number): GeoJSON.Polygon => ({ type: "Polygon", coordinates: [[[0, 0], [size, 0], [size, size], [0, 0]]] });

// A place of the INSEE source: its code is the answer of its `inseeCode` field.
const commune = (code: string): PlaceLike => ({
  fields: { "-7": { id: "-7", valueBinding: "inseeCode" } },
  answers: { "-7": code },
});

function loaders(places: Record<string, PlaceLike>, project: Awaited<ReturnType<FocusLoaders["project"]>> = {}, contours: Record<string, GeoJSON.Geometry> = {}): FocusLoaders {
  return {
    place: vi.fn(async (id: string) => places[id] ?? {}),
    project: vi.fn(async () => project),
    communeContour: vi.fn(async (code: string) => contours[code] ?? null),
  };
}

describe("resolveDrawingFocus", () => {
  it("frames the INSEE commune of the entity's place", async () => {
    const l = loaders({ "7": commune("69123") }, {}, { "69123": square(1) });
    expect(await resolveDrawingFocus({ placeIds: ["7"], projectId: "5" }, l)).toEqual(square(1));
    expect(l.project).not.toHaveBeenCalled();
  });

  it("falls back to the widest locatable place of the project when the entity's place is not an INSEE one", async () => {
    const l = loaders(
      { "7": {}, "20": commune("69123"), "21": commune("69000"), "22": {} },
      { mainLocation: { id: "20" }, spatialContext: [{ id: "21" }, { id: "22" }] },
      { "69123": square(1), "69000": square(3) },
    );
    expect(await resolveDrawingFocus({ placeIds: ["7"], projectId: "5" }, l)).toEqual(square(3));
  });

  it("uses a project place's own geometry when it is in WGS84", async () => {
    const l = loaders({ "20": { geom: { ...square(2), srid: 4326 } } }, { mainLocation: { id: "20" } });
    expect(await resolveDrawingFocus({ placeIds: [], projectId: "5" }, l)).toEqual(square(2));
  });

  it("leaves out a place the commune API fails for, and keeps looking", async () => {
    const l = loaders({ "7": commune("69123"), "20": { geom: { ...square(2), srid: 4326 } } }, { mainLocation: { id: "20" } });
    l.communeContour = vi.fn(async () => {
      throw new Error("geo.api.gouv.fr down");
    });
    expect(await resolveDrawingFocus({ placeIds: ["7"], projectId: "5" }, l)).toEqual(square(2));
  });

  it("frames nothing when no place can be located", async () => {
    const l = loaders({ "20": { geom: { ...square(2), srid: 2154 } } }, { mainLocation: { id: "20" } });
    expect(await resolveDrawingFocus({ placeIds: [], projectId: "5" }, l)).toBeNull();
    expect(await resolveDrawingFocus({ placeIds: [] }, l)).toBeNull();
  });
});

describe("inseeCodeOf", () => {
  it("reads the inseeCode answer, plain or in an envelope", () => {
    expect(inseeCodeOf(commune("69123"))).toBe("69123");
    expect(inseeCodeOf({ ...commune(""), answers: { "-7": { answerType: "TEXT", value: " 01001 " } } })).toBe("01001");
    expect(inseeCodeOf({})).toBeNull();
  });
});

describe("wgs84GeometryOf", () => {
  it("keeps a WGS84 geometry and leaves any other SRID out", () => {
    expect(wgs84GeometryOf({ geom: { ...square(1), srid: 4326 } })).toEqual(square(1));
    expect(wgs84GeometryOf({ geom: { ...square(1), srid: 2154 } })).toBeNull();
  });
});

describe("widest", () => {
  it("keeps the geometry of the largest extent", () => {
    expect(widest([square(1), square(4), square(2)])).toEqual(square(4));
    expect(widest([])).toBeNull();
  });
});
