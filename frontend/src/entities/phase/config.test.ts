import { describe, expect, it, vi } from "vitest";
import { phaseEntityConfig } from "./config";
import { apiFetch } from "../../api/client";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("phaseEntityConfig", () => {
  it("registers under the 'phase' key that relationTab/project config reference", () => {
    expect(phaseEntityConfig.key).toBe("phase");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(phaseEntityConfig.collectionPath).toBe("phases");
  });

  it("registers the fiche and recording-units tabs, a header, and a patchAnswers write path", () => {
    expect(phaseEntityConfig.detail.tabs.map((t) => t.key)).toEqual(["fiche", "recording-units"]);
    expect(phaseEntityConfig.detail.header).toBeDefined();
    expect(phaseEntityConfig.api.patchAnswers).toBeDefined();
  });

  // Reduced scope (migration plan lot 2, same precedent as Mobilier's own lot 1): no list.schema
  // (pinned columns only).
  it("has a dynamic column catalog, from the project's forms", () => {
    expect(phaseEntityConfig.list.schema).toBeDefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(phaseEntityConfig.list.createForm).toBeDefined();
  });

  // Created in a project: from the organization-wide list, the create form picks it.
  it("is created in a project picked by the form on the unscoped list", () => {
    expect(phaseEntityConfig.list.createProjectKind).toBe("phase");
  });

  it("derives its bookmark chrome from the resource itself", () => {
    const entity = { id: "7", identifier: "P-7", resourceUri: "/phase/7", bookmarked: true } as unknown as Parameters<NonNullable<typeof phaseEntityConfig.detail.chrome>>[0];
    expect(phaseEntityConfig.detail.chrome?.(entity)).toEqual({ resourceUri: "/phase/7", title: "P-7", bookmarked: true });
  });

  // Previous/next through the generic GET /api/v1/{collection}/{id}/siblings.
  it("navigates siblings through its own collection's siblings endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { previous: { id: "1", label: "A", resourceUri: "URI1" }, next: null } });
    const siblings = await phaseEntityConfig.api.siblings!("7", {});
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/phases/7/siblings");
    expect(siblings).toEqual({ previous: { id: "1", label: "A", resourceUri: "URI1" }, next: undefined });
  });
});
