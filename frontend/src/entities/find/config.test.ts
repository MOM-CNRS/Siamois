import {describe, expect, it, vi} from "vitest";
import {findEntityConfig} from "./config";
import {apiFetch} from "../../api/client";
import {availableViews} from "../../panels/list/listViews";
import type {EntityTypeConfig} from "../shared/types";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("findEntityConfig", () => {
  it("registers under the 'find' key that relationTab/project config reference", () => {
    expect(findEntityConfig.key).toBe("find");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(findEntityConfig.collectionPath).toBe("finds");
  });

  it("registers a single fiche tab and a header, and a patchAnswers write path for the list overlay", () => {
    expect(findEntityConfig.detail.tabs.map((t) => t.key)).toEqual(["fiche", "documents"]);
    expect(findEntityConfig.detail.header).toBeDefined();
    expect(findEntityConfig.api.patchAnswers).toBeDefined();
  });

  it("has a dynamic column catalog, from the project's forms", () => {
    expect(findEntityConfig.list.schema).toBeDefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(findEntityConfig.list.createForm).toBeDefined();
  });

  // The list panel reads every config as an untyped one.
  const anyConfig = findEntityConfig as unknown as EntityTypeConfig<unknown, unknown>;

  it("is shown on a map from the organization-wide list and from a project's", () => {
    expect(findEntityConfig.list.mappable).toBe(true);
    expect(availableViews(anyConfig, undefined)).toContain("map");
    expect(availableViews(anyConfig, { entityType: "project", id: 5 })).toContain("map");
  });

  it("offers no map inside another parent, whose sub-collection has no map endpoint", () => {
    expect(availableViews(anyConfig, { entityType: "recordingUnit", id: 31 })).not.toContain("map");
  });

  // Created in a project: from the organization-wide list, the create form picks it.
  it("is created in a project picked by the form on the unscoped list", () => {
    expect(findEntityConfig.list.createProjectKind).toBe("find");
  });

  it("is created ON the recording unit whose field offers « Nouveau », and on nothing else", () => {
    const prefillFrom = findEntityConfig.list.createPrefillFrom!;
    expect(prefillFrom({ entityType: "recordingUnit", entityId: 31, entityLabel: "INST-UE31" })).toEqual({
      recordingUnit: { id: 31, label: "INST-UE31" },
    });
    expect(prefillFrom({ entityType: "recordingUnit", entityId: 31 })).toEqual({ recordingUnit: { id: 31, label: "31" } });
    expect(prefillFrom({ entityType: "phase", entityId: 4 })).toBeUndefined();
    expect(prefillFrom({ entityType: "recordingUnit" })).toBeUndefined();
  });

  it("derives its bookmark chrome from the resource itself", () => {
    const entity = { id: "7", fullIdentifier: "M-7", resourceUri: "/specimen/7", bookmarked: true } as unknown as Parameters<NonNullable<typeof findEntityConfig.detail.chrome>>[0];
    expect(findEntityConfig.detail.chrome?.(entity)).toEqual({ resourceUri: "/specimen/7", title: "M-7", bookmarked: true });
  });

  // Previous/next through the generic GET /api/v1/{collection}/{id}/siblings.
  it("navigates siblings through its own collection's siblings endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { previous: { id: "1", label: "A", resourceUri: "URI1" }, next: null } });
    const siblings = await findEntityConfig.api.siblings!("7", {});
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/finds/7/siblings");
    expect(siblings).toEqual({ previous: { id: "1", label: "A", resourceUri: "URI1" }, next: undefined });
  });
});
