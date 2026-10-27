import {describe, expect, it} from "vitest";
import type {EntityTypeConfig, FieldCatalog} from "../../entities/shared/types";
import type {FieldResource} from "../../fields/types";
import {availableFilters, isFilterAvailable, isSortAvailable} from "./listStateValidity";

const field = (id: string, query?: FieldResource["query"]) =>
  ({ id, resourceType: "fields", label: id, answerType: "TEXT", isSystemField: false, query }) as FieldResource;

const catalog: FieldCatalog = {
  fields: {
    "10": field("10", { sortable: true, filterOp: "contains" }),
    "11": field("11", { sortable: false, filterOp: "in" }),
    "12": field("12", null),
  },
  columns: [],
};

const config = {
  list: {
    defaultSort: "creationTime:desc",
    columns: [
      { key: "name", header: "Name", render: () => null, sortable: true, filterable: true },
      { key: "plain", header: "Plain", render: () => null },
      { key: "shown", header: "Shown", render: () => null, fieldId: "10" },
    ],
  },
} as unknown as EntityTypeConfig<unknown, unknown>;

describe("isFilterAvailable", () => {
  it("accepts a pinned filterable column and a catalog field with a filter", () => {
    expect(isFilterAvailable("name", config, catalog)).toBe(true);
    expect(isFilterAvailable("11", config, catalog)).toBe(true);
  });

  it("refuses a field the catalog lost, one with no filter, and a pinned column that isn't filterable", () => {
    expect(isFilterAvailable("99", config, catalog)).toBe(false);
    expect(isFilterAvailable("12", config, catalog)).toBe(false);
    expect(isFilterAvailable("plain", config, catalog)).toBe(false);
  });

  it("refuses every catalog field while there is no catalog, but keeps the pinned ones", () => {
    expect(isFilterAvailable("10", config, undefined)).toBe(false);
    expect(isFilterAvailable("name", config, undefined)).toBe(true);
  });
});

describe("availableFilters", () => {
  it("keeps the filters that still apply, whole", () => {
    const filters = {
      name: { op: "contains" as const, v: "a" },
      "99": { op: "contains" as const, v: "gone" },
      "11": { op: "in" as const, v: ["1"] },
    };
    expect(availableFilters(filters, config, catalog)).toEqual({
      name: { op: "contains", v: "a" },
      "11": { op: "in", v: ["1"] },
    });
  });
});

describe("isSortAvailable", () => {
  it("accepts no sort, the entity's default, a sortable pinned column and a sortable catalog field", () => {
    expect(isSortAvailable(undefined, config, catalog)).toBe(true);
    expect(isSortAvailable("creationTime:asc", config, catalog)).toBe(true);
    expect(isSortAvailable("name:desc", config, catalog)).toBe(true);
    expect(isSortAvailable("10:asc", config, catalog)).toBe(true);
  });

  it("refuses a catalog field the server can no longer order, one that is gone, and a pinned column that can't sort", () => {
    expect(isSortAvailable("11:asc", config, catalog)).toBe(false);
    expect(isSortAvailable("99:asc", config, catalog)).toBe(false);
    expect(isSortAvailable("plain:asc", config, catalog)).toBe(false);
  });
});
