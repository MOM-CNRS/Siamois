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

  it("adds the tree to the organization-wide list of a hierarchical type", () => {
    expect(availableViews(config({ tree: true }), undefined)).toEqual([
      "list",
      "tree",
    ]);
  });

  it("adds the tree inside a context only when the type declares it", () => {
    const hierarchical = config({ tree: true, treeScopes: ["project"] });
    expect(
      availableViews(hierarchical, { entityType: "project", id: 1, path: "x" }),
    ).toEqual(["list", "tree"]);
    expect(
      availableViews(hierarchical, { entityType: "place", id: 1, path: "x" }),
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
