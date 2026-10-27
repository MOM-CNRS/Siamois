import {describe, expect, it, vi} from "vitest";
import {studyEntityConfig} from "./config";
import {apiFetch} from "../../api/client";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("studyEntityConfig", () => {
  it("registers under the 'study' key that relationTab/project config reference", () => {
    expect(studyEntityConfig.key).toBe("study");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(studyEntityConfig.collectionPath).toBe("studies");
  });

  it("registers the fiche tab only, a header, and a patchAnswers write path", () => {
    expect(studyEntityConfig.detail.tabs.map((t) => t.key)).toEqual(["fiche"]);
    expect(studyEntityConfig.detail.header).toBeDefined();
    expect(studyEntityConfig.api.patchAnswers).toBeDefined();
  });

  it("has a dynamic column catalog, from the project's forms", () => {
    expect(studyEntityConfig.list.schema).toBeDefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(studyEntityConfig.list.createForm).toBeDefined();
  });

  // Created in a project: from the organization-wide list, the create form picks it.
  it("is created in a project picked by the form on the unscoped list", () => {
    expect(studyEntityConfig.list.createProjectKind).toBe("study");
  });

  it("derives its bookmark chrome from the resource itself", () => {
    const entity = { id: "7", identifier: "ETU-7", resourceUri: "/study/7", bookmarked: true } as unknown as Parameters<NonNullable<typeof studyEntityConfig.detail.chrome>>[0];
    expect(studyEntityConfig.detail.chrome?.(entity)).toEqual({ resourceUri: "/study/7", title: "ETU-7", bookmarked: true });
  });

  // Previous/next through the generic GET /api/v1/{collection}/{id}/siblings.
  it("navigates siblings through its own collection's siblings endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { previous: { id: "1", label: "A", resourceUri: "URI1" }, next: null } });
    const siblings = await studyEntityConfig.api.siblings!("7", {});
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/studies/7/siblings");
    expect(siblings).toEqual({ previous: { id: "1", label: "A", resourceUri: "URI1" }, next: undefined });
  });
});
