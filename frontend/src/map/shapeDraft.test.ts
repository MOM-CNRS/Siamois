import {describe, expect, it} from "vitest";
import {addVertex, canCloseRing, canSave, closeRing, draftToGeometry, emptyDraft, isEmpty, type LngLat, type ShapeDraft, undo} from "./shapeDraft";

const clicks = (draft: ShapeDraft, ...vertices: LngLat[]) => vertices.reduce(addVertex, draft);
const TRIANGLE: LngLat[] = [[0, 0], [1, 0], [1, 1]];
const OTHER: LngLat[] = [[5, 5], [6, 5], [6, 6]];

describe("shapeDraft in polygon mode", () => {
  it("makes one polygon, its ring closed on its first vertex", () => {
    const draft = clicks(emptyDraft("polygon"), ...TRIANGLE);
    expect(canSave(draft)).toBe(true);
    expect(draftToGeometry(draft)).toEqual({ type: "Polygon", coordinates: [[[0, 0], [1, 0], [1, 1], [0, 0]]] });
  });

  it("makes a multipolygon of the polygons closed one after the other", () => {
    const draft = clicks(closeRing(clicks(emptyDraft("polygon"), ...TRIANGLE)), ...OTHER);
    expect(draftToGeometry(draft)).toEqual({
      type: "MultiPolygon",
      coordinates: [[[[0, 0], [1, 0], [1, 1], [0, 0]]], [[[5, 5], [6, 5], [6, 6], [5, 5]]]],
    });
  });

  it("closes a ring only once it is a shape", () => {
    const two = clicks(emptyDraft("polygon"), [0, 0], [1, 0]);
    expect(canCloseRing(two)).toBe(false);
    expect(closeRing(two)).toBe(two);
  });

  it("can't save while a ring is half-drawn, nor with nothing drawn", () => {
    const halfDrawn = clicks(closeRing(clicks(emptyDraft("polygon"), ...TRIANGLE)), [5, 5]);
    expect(canSave(halfDrawn)).toBe(false);
    expect(canSave(emptyDraft("polygon"))).toBe(false);
  });

  it("takes back the last vertex, then reopens the last polygon", () => {
    const closed = closeRing(clicks(emptyDraft("polygon"), ...TRIANGLE));
    expect(undo(clicks(closed, [5, 5])).ring).toEqual([]);
    expect(undo(closed)).toMatchObject({ polygons: [], ring: [[0, 0], [1, 0]] });
  });
});

describe("shapeDraft in point mode", () => {
  it("keeps the last point clicked", () => {
    const draft = clicks(emptyDraft("point"), [1, 1], [2.3, 48.8]);
    expect(draftToGeometry(draft)).toEqual({ type: "Point", coordinates: [2.3, 48.8] });
    expect(undo(draft).point).toBeNull();
  });

  it("is empty until a point is placed", () => {
    expect(isEmpty(emptyDraft("point"))).toBe(true);
    expect(canSave(emptyDraft("point"))).toBe(false);
  });
});
