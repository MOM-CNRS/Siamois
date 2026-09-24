import { describe, expect, it } from "vitest";
import { placeEntityConfig } from "./config";

describe("placeEntityConfig", () => {
  it("registers under the 'place' key that PlacesTab/onOpenOverview reference", () => {
    expect(placeEntityConfig.key).toBe("place");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(placeEntityConfig.collectionPath).toBe("places");
  });

  it("registers a single fiche tab and a header, and a patchAnswers write path", () => {
    expect(placeEntityConfig.detail.tabs).toHaveLength(1);
    expect(placeEntityConfig.detail.tabs[0].key).toBe("fiche");
    expect(placeEntityConfig.detail.header).toBeDefined();
    expect(placeEntityConfig.api.patchAnswers).toBeDefined();
  });

  // The organization-wide list (JSF's SpatialUnitListPanel): searchable, and — unlike the
  // project-bound types — creation is allowed, since places belong to the organization.
  it("has a searchable list with a create form, but no schema, siblings or overview chrome", () => {
    expect(placeEntityConfig.list.searchable).toBe(true);
    expect(placeEntityConfig.list.createForm).toBeDefined();
    expect(placeEntityConfig.list.createRequiresScope).toBeUndefined();
    expect(placeEntityConfig.list.schema).toBeUndefined();
    expect(placeEntityConfig.api.siblings).toBeUndefined();
    expect(placeEntityConfig.detail.chrome).toBeUndefined();
  });
});
