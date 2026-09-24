import { describe, expect, it, vi } from "vitest";
import { placeEntityConfig } from "./config";
import { apiFetch } from "../../api/client";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("placeEntityConfig", () => {
  it("registers under the 'place' key that PlacesTab/onOpenOverview reference", () => {
    expect(placeEntityConfig.key).toBe("place");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(placeEntityConfig.collectionPath).toBe("places");
  });

  it("registers the fiche, contained-places and projects tabs, a header, and a patchAnswers write path", () => {
    expect(placeEntityConfig.detail.tabs.map((t) => t.key)).toEqual(["fiche", "children", "projects"]);
    expect(placeEntityConfig.detail.header).toBeDefined();
    expect(placeEntityConfig.api.patchAnswers).toBeDefined();
  });

  // The organization-wide list (JSF's SpatialUnitListPanel): searchable, and — unlike the
  // project-bound types — creation is allowed, since places belong to the organization.
  it("has a searchable list with a create form, but no schema", () => {
    expect(placeEntityConfig.list.searchable).toBe(true);
    expect(placeEntityConfig.list.createForm).toBeDefined();
    expect(placeEntityConfig.list.createRequiresScope).toBeUndefined();
    expect(placeEntityConfig.list.schema).toBeUndefined();
  });

  it("derives its bookmark chrome from the resource itself", () => {
    const entity = { id: "7", name: "Lieu 7", resourceUri: "/spatial-unit/7", bookmarked: true } as unknown as Parameters<NonNullable<typeof placeEntityConfig.detail.chrome>>[0];
    expect(placeEntityConfig.detail.chrome?.(entity)).toEqual({ resourceUri: "/spatial-unit/7", title: "Lieu 7", bookmarked: true });
  });

  // Previous/next through the generic GET /api/v1/{collection}/{id}/siblings.
  it("navigates siblings through its own collection's siblings endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { previous: { id: "1", label: "A", resourceUri: "URI1" }, next: null } });
    const siblings = await placeEntityConfig.api.siblings!("7", {});
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/places/7/siblings");
    expect(siblings).toEqual({ previous: { id: "1", label: "A", resourceUri: "URI1" }, next: undefined });
  });
});
