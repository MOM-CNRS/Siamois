import {describe, expect, it} from "vitest";
import {queryKeys} from "./queryKeys";

// Keys are hierarchical: invalidating a prefix reaches every key below it. These tests pin the shapes the
// invalidations rely on, and the optional parts that must collapse to a stable value.

function startsWith(key: readonly unknown[], prefix: readonly unknown[]): boolean {
  return prefix.every((part, i) => Object.is(key[i], part));
}

describe("queryKeys.entityListMap", () => {
  const resultSet = { search: "fosse", organizationId: 3 };

  it("sits under the list's prefix, so refreshing the list refreshes its map", () => {
    const key = queryKeys.entityListMap("recordingUnit", resultSet, "-12");

    expect(key).toEqual(["entity-list", "recordingUnit", "map", resultSet, "-12"]);
    expect(startsWith(key, queryKeys.entityList("recordingUnit"))).toBe(true);
    expect(startsWith(key, queryKeys.entityLists())).toBe(true);
  });

  it("keys the entity's own geometry as null rather than undefined", () => {
    expect(queryKeys.entityListMap("place", resultSet)).toEqual(["entity-list", "place", "map", resultSet, null]);
  });

  it("keeps an empty geometry string apart from the default column", () => {
    expect(queryKeys.entityListMap("place", resultSet, "")[4]).toBe("");
  });

  it("is not under another type's list", () => {
    expect(startsWith(queryKeys.entityListMap("place", resultSet), queryKeys.entityList("recordingUnit"))).toBe(false);
  });
});

describe("queryKeys.entityMapFeature", () => {
  it("without a geometry, is the prefix of every geometry column's key", () => {
    const prefix = queryKeys.entityMapFeature("recordingUnit", 12);
    const byColumn = queryKeys.entityMapFeature("recordingUnit", 12, "-7");

    expect(prefix).toEqual(["entity-detail", "recordingUnit", 12, "map"]);
    expect(byColumn).toEqual(["entity-detail", "recordingUnit", 12, "map", "-7"]);
    expect(startsWith(byColumn, prefix)).toBe(true);
  });

  it("sits under the entity's detail, so editing the fiche refreshes it", () => {
    const key = queryKeys.entityMapFeature("place", "abc", "geom");

    expect(startsWith(key, queryKeys.entityDetail("place", "abc"))).toBe(true);
    expect(startsWith(key, queryKeys.entityDetailsOf("place"))).toBe(true);
  });

  it("treats a null geometry like an absent one", () => {
    expect(queryKeys.entityMapFeature("place", 1, null as unknown as string)).toEqual(["entity-detail", "place", 1, "map"]);
  });

  it("keeps an empty geometry string as its own column", () => {
    expect(queryKeys.entityMapFeature("place", 1, "")).toEqual(["entity-detail", "place", 1, "map", ""]);
  });

  it("keeps string and numeric ids apart, as the detail key does", () => {
    expect(queryKeys.entityMapFeature("place", 1)).not.toEqual(queryKeys.entityMapFeature("place", "1"));
  });
});

describe("queryKeys.communeContour", () => {
  it("is keyed by the INSEE code alone", () => {
    expect(queryKeys.communeContour("75056")).toEqual(["commune-contour", "75056"]);
  });

  it("keeps leading zeros of overseas and Corsican codes", () => {
    expect(queryKeys.communeContour("01001")[1]).toBe("01001");
    expect(queryKeys.communeContour("2A004")[1]).toBe("2A004");
  });
});

describe("queryKeys with optional parts", () => {
  it("entityListTotal fills a missing scope and filters with stable values", () => {
    expect(queryKeys.entityListTotal("find", 3, undefined)).toEqual(["entity-list", "find", "unfiltered-total", 3, undefined, undefined, null]);
    expect(queryKeys.entityListTotal("find", 3, { entityType: "project", id: 5 }, { a: 1 })).toEqual([
      "entity-list",
      "find",
      "unfiltered-total",
      3,
      "project",
      5,
      { a: 1 },
    ]);
  });

  it("stratigraphyValues is a prefix of each field's key", () => {
    const prefix = queryKeys.stratigraphyValues(4);
    const byField = queryKeys.stratigraphyValues(4, "-3");

    expect(prefix).toEqual(["stratigraphy-values", 4]);
    expect(byField).toEqual(["stratigraphy-values", 4, "-3"]);
  });

  it("relationList is a prefix of each parameter set's key", () => {
    expect(queryKeys.relationList(4)).toEqual(["relation-list", 4]);
    expect(queryKeys.relationList(4, { offset: 0 })).toEqual(["relation-list", 4, { offset: 0 }]);
  });

  it("entityBreadcrumbs adds the path it came by only when there is one", () => {
    expect(queryKeys.entityBreadcrumbs("find", 9)).toEqual(["entity-breadcrumbs", "find", 9]);
    expect(queryKeys.entityBreadcrumbs("find", 9, "")).toEqual(["entity-breadcrumbs", "find", 9]);
    expect(queryKeys.entityBreadcrumbs("find", 9, "ru:4")).toEqual(["entity-breadcrumbs", "find", 9, "ru:4"]);
  });

  it("collapses missing optional ids to null", () => {
    expect(queryKeys.entitySiblings("find", 9, undefined, undefined)).toEqual(["entity-siblings", "find", 9, undefined, null]);
    expect(queryKeys.stratigraphyTypes(1, "-3")).toEqual(["stratigraphy-types", 1, "-3", null, null]);
    expect(queryKeys.ficheConfig(1, "find")).toEqual(["fiche-config", 1, "find", null, null]);
    expect(queryKeys.effectiveForm("finds", 2, undefined)).toEqual(["effective-form", "finds", 2, null]);
    expect(queryKeys.identifierSuggestion("find", undefined, null)).toEqual(["identifier-suggestion", "find", null, null, null]);
  });

  it("keeps a zero id rather than collapsing it", () => {
    expect(queryKeys.identifierSuggestion("find", 0, 0, 0)).toEqual(["identifier-suggestion", "find", 0, 0, 0]);
    expect(queryKeys.ficheConfig(0, "find", 0, 0)).toEqual(["fiche-config", 0, "find", 0, 0]);
  });

  it("puts each saved-views key under the organization's views", () => {
    const prefix = queryKeys.views(1);

    expect(startsWith(queryKeys.viewList(1, "find", "q"), prefix)).toBe(true);
    expect(startsWith(queryKeys.view(1, 8), prefix)).toBe(true);
    expect(startsWith(queryKeys.view(2, 8), prefix)).toBe(false);
  });

  it("puts each bookmark under the bookmarks prefix", () => {
    expect(startsWith(queryKeys.bookmark("/project/1", 1), queryKeys.bookmarks())).toBe(true);
  });
});
