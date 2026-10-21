import { describe, expect, it } from "vitest";
import type { EntityTypeConfig } from "../../entities/types";
import {
  availableViews,
  DEFAULT_LIST_VIEW,
  resolveViewMode,
  viewOptions,
} from "./listViews";

const config = (list: Record<string, unknown>) =>
  ({ list }) as unknown as EntityTypeConfig<unknown, unknown>;

describe("availableViews", () => {
  it("is only the flat list for a type with no hierarchy", () => {
    expect(availableViews(config({}), undefined)).toEqual(["list"]);
    expect(availableViews(undefined, undefined)).toEqual(["list"]);
  });

  it("adds the cards to a type that describes its cards", () => {
    expect(availableViews(config({ card: {} }), undefined)).toEqual(["list", "cards"]);
    expect(availableViews(config({ tree: true, card: {} }), undefined)).toEqual(["list", "tree", "cards"]);
  });

  it("adds the tree to the organization-wide list of a hierarchical type", () => {
    expect(availableViews(config({ tree: true }), undefined)).toEqual([
      "list",
      "tree",
    ]);
  });

  it("offers the tree of the projects of a place", () => {
    const projects = config({ tree: true, treeScopes: ["place:projects"] });
    expect(
      availableViews(projects, {
        entityType: "place",
        id: 1,
        path: "projects",
      }),
    ).toEqual(["list", "tree"]);
    expect(
      availableViews(projects, {
        entityType: "place",
        id: 1,
        path: "children",
      }),
    ).toEqual(["list"]);
  });

  it("names a context by its sub-collection when it isn't the default one", () => {
    const hierarchical = config({
      tree: true,
      treeScopes: ["recordingUnit:children"],
    });
    // The units below a unit are a tree; its ancestors, or its default list, are not.
    expect(
      availableViews(hierarchical, {
        entityType: "recordingUnit",
        id: 1,
        path: "children",
      }),
    ).toEqual(["list", "tree"]);
    expect(
      availableViews(hierarchical, {
        entityType: "recordingUnit",
        id: 1,
        path: "parents",
      }),
    ).toEqual(["list"]);
    expect(
      availableViews(hierarchical, { entityType: "recordingUnit", id: 1 }),
    ).toEqual(["list"]);
  });

  it("adds the tree inside a context only when the type declares it", () => {
    const hierarchical = config({ tree: true, treeScopes: ["project"] });
    expect(
      availableViews(hierarchical, { entityType: "project", id: 1 }),
    ).toEqual(["list", "tree"]);
    expect(
      availableViews(hierarchical, { entityType: "place", id: 1 }),
    ).toEqual(["list"]);
  });
});

describe("resolveViewMode", () => {
  it("keeps the remembered mode while the list still offers it", () => {
    expect(resolveViewMode("tree", ["list", "tree"])).toBe("tree");
  });

  it("falls back to the flat list when it doesn't (another context, a type that lost the view)", () => {
    expect(resolveViewMode("tree", ["list"])).toBe(DEFAULT_LIST_VIEW);
    expect(resolveViewMode(undefined, ["list", "tree"])).toBe(
      DEFAULT_LIST_VIEW,
    );
  });
});

describe("viewOptions", () => {
  it("labels and icons each available mode, in order", () => {
    expect(viewOptions(["list", "tree"]).map((o) => [o.mode, o.label])).toEqual(
      [
        ["list", "Liste"],
        ["tree", "Arbre"],
      ],
    );
  });
});
