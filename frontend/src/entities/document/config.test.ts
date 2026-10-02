import { describe, expect, it, vi } from "vitest";
import { documentEntityConfig } from "./config";
import { apiFetch } from "../../api/client";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("documentEntityConfig", () => {
  it("registers under the 'document' key", () => {
    expect(documentEntityConfig.key).toBe("document");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(documentEntityConfig.collectionPath).toBe("documents");
  });

  it("registers the fiche tab, a header, and a patchAnswers write path", () => {
    expect(documentEntityConfig.detail.tabs.map((t) => t.key)).toEqual(["fiche"]);
    expect(documentEntityConfig.detail.header).toBeDefined();
    expect(documentEntityConfig.api.patchAnswers).toBeDefined();
  });

  it("has a dynamic column catalog, from the project's forms", () => {
    expect(documentEntityConfig.list.schema).toBeDefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(documentEntityConfig.list.createForm).toBeDefined();
  });

  // Created in a project: from the organization-wide list, the create form picks it.
  it("is created in a project picked by the form on the unscoped list", () => {
    expect(documentEntityConfig.list.createProjectKind).toBe("document");
  });

  it("derives its bookmark chrome from the resource itself", () => {
    const entity = { id: "7", identifier: "D-7", resourceUri: "/document/7", bookmarked: true } as unknown as Parameters<NonNullable<typeof documentEntityConfig.detail.chrome>>[0];
    expect(documentEntityConfig.detail.chrome?.(entity)).toEqual({ resourceUri: "/document/7", title: "D-7", bookmarked: true });
  });

  // Previous/next through the generic GET /api/v1/{collection}/{id}/siblings.
  it("navigates siblings through its own collection's siblings endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { previous: { id: "1", label: "A", resourceUri: "URI1" }, next: null } });
    const siblings = await documentEntityConfig.api.siblings!("7", {});
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/documents/7/siblings");
    expect(siblings).toEqual({ previous: { id: "1", label: "A", resourceUri: "URI1" }, next: undefined });
  });
});
