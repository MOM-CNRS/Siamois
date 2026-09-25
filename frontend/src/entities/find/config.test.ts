import { describe, expect, it, vi } from "vitest";
import { findEntityConfig } from "./config";
import { apiFetch } from "../../api/client";

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
    expect(findEntityConfig.detail.tabs).toHaveLength(1);
    expect(findEntityConfig.detail.tabs[0].key).toBe("fiche");
    expect(findEntityConfig.detail.header).toBeDefined();
    expect(findEntityConfig.api.patchAnswers).toBeDefined();
  });

  it("has a dynamic column catalog, from the project's forms", () => {
    expect(findEntityConfig.list.schema).toBeDefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(findEntityConfig.list.createForm).toBeDefined();
  });

  // Created in a project: from the organization-wide list, the create form picks it.
  it("is created in a project picked by the form on the unscoped list", () => {
    expect(findEntityConfig.list.createProjectKind).toBe("find");
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
