import {describe, expect, it} from "vitest";
import type {FieldResource} from "../../../fields/types";
import {filtersForGeometryChange, mapGeometryFilter, mapGeometryOptions, resolveMapGeometry} from "./mapGeometry";

const field = (id: string, answerType: string, label: string, isSystemField = true): FieldResource =>
  ({ id, resourceType: "fields", label, answerType, isSystemField }) as FieldResource;

const catalog = {
  "40": field("40", "GEOMETRY", "Emprise de fouille", false),
  "-332": field("-332", "GEOMETRY", "Géométrie"),
  "-304": field("-304", "SELECT_ONE_SPATIAL_UNIT", "Lieu"),
  "-1": field("-1", "TEXT", "Description"),
};

describe("mapGeometryOptions", () => {
  it("offers only the geometry columns, the entity's own first, then the additional ones", () => {
    expect(mapGeometryOptions(catalog).map((o) => [o.value, o.label])).toEqual([
      ["-332", "Géométrie"],
      ["40", "Emprise de fouille"],
    ]);
  });

  it("offers nothing without a catalog", () => {
    expect(mapGeometryOptions(undefined)).toEqual([]);
  });
});

describe("resolveMapGeometry", () => {
  const options = mapGeometryOptions(catalog);

  it("keeps the column chosen, and defaults to the entity's own geometry", () => {
    expect(resolveMapGeometry("40", options)?.value).toBe("40");
    expect(resolveMapGeometry(undefined, options)?.value).toBe("-332");
    expect(resolveMapGeometry("-999", options)?.value).toBe("-332");
  });
});

describe("mapGeometryFilter", () => {
  const geometry = { value: "-40", label: "Géométrie", icon: "" };
  const fields = {
    "-40": { id: "-40", label: "Géométrie", answerType: "GEOMETRY", query: { sortable: false, filterOp: "presence" } },
    "-41": { id: "-41", label: "Ancienne", answerType: "GEOMETRY", query: null },
  } as unknown as Record<string, FieldResource>;

  it("filters the drawn geometry on « not empty »", () => {
    expect(mapGeometryFilter({}, geometry, fields)).toEqual({ op: "notEmpty" });
  });

  it("leaves a filter the user already set on that column alone", () => {
    expect(mapGeometryFilter({ "-40": { op: "empty" } }, geometry, fields)).toBeUndefined();
  });

  it("adds nothing without a geometry column, or on one the server cannot filter", () => {
    expect(mapGeometryFilter({}, undefined, fields)).toBeUndefined();
    expect(mapGeometryFilter({}, { ...geometry, value: "-41" }, fields)).toBeUndefined();
  });
});

describe("filtersForGeometryChange", () => {
  const own = { value: "-40", label: "Géométrie", icon: "" };
  const footprint = { value: "41", label: "Emprise", icon: "" };
  const presence = { sortable: false, filterOp: "presence" };
  const fields = {
    "-40": { id: "-40", label: "Géométrie", answerType: "GEOMETRY", query: presence },
    "41": { id: "41", label: "Emprise", answerType: "GEOMETRY", query: presence },
  } as unknown as Record<string, FieldResource>;
  const name = { op: "contains" as const, v: "UE" };

  it("moves « not empty » to the geometry drawn now, other filters untouched", () => {
    expect(filtersForGeometryChange({ "-40": { op: "notEmpty" }, "-1": name }, own, footprint, fields)).toEqual({
      "-1": name,
      "41": { op: "notEmpty" },
    });
  });

  it("keeps the new geometry's own filter, dropping the moved one", () => {
    expect(filtersForGeometryChange({ "-40": { op: "notEmpty" }, "41": { op: "empty" } }, own, footprint, fields)).toEqual({
      "41": { op: "empty" },
    });
  });

  it("moves nothing once the filter was taken off, or when the column carries another one", () => {
    expect(filtersForGeometryChange({ "-1": name }, own, footprint, fields)).toBeUndefined();
    expect(filtersForGeometryChange({ "-40": { op: "empty" } }, own, footprint, fields)).toBeUndefined();
    expect(filtersForGeometryChange({ "-40": { op: "notEmpty" } }, own, own, fields)).toBeUndefined();
  });
});
