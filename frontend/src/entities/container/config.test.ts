import { describe, expect, it, vi } from "vitest";
import { containerEntityConfig } from "./config";
import { apiFetch } from "../../api/client";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("containerEntityConfig", () => {
  it("registers under the 'container' key that relationTab/project config reference", () => {
    expect(containerEntityConfig.key).toBe("container");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(containerEntityConfig.collectionPath).toBe("containers");
  });

  it("registers a single fiche tab and a header, and a patchAnswers write path for the list overlay", () => {
    expect(containerEntityConfig.detail.tabs).toHaveLength(1);
    expect(containerEntityConfig.detail.tabs[0].key).toBe("fiche");
    expect(containerEntityConfig.detail.header).toBeDefined();
    expect(containerEntityConfig.api.patchAnswers).toBeDefined();
  });

  // Reduced scope (migration plan lot 3, same precedent as Phase/Mobilier's own): no
  // list.schema (pinned columns only).
  it("has no dynamic column schema yet", () => {
    expect(containerEntityConfig.list.schema).toBeUndefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(containerEntityConfig.list.createForm).toBeDefined();
  });

  // JSF's organization-wide list disables creation for this type (it needs a project).
  it("disables creation on the unscoped list with an explanation", () => {
    expect(containerEntityConfig.list.createRequiresScope).toMatch(/que depuis un projet/);
  });

  it("derives its bookmark chrome from the resource itself", () => {
    const entity = { id: "7", identifier: "C-7", resourceUri: "/container/7", bookmarked: true } as unknown as Parameters<NonNullable<typeof containerEntityConfig.detail.chrome>>[0];
    expect(containerEntityConfig.detail.chrome?.(entity)).toEqual({ resourceUri: "/container/7", title: "C-7", bookmarked: true });
  });

  // Previous/next through the generic GET /api/v1/{collection}/{id}/siblings.
  it("navigates siblings through its own collection's siblings endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { previous: { id: "1", label: "A", resourceUri: "URI1" }, next: null } });
    const siblings = await containerEntityConfig.api.siblings!("7", {});
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/containers/7/siblings");
    expect(siblings).toEqual({ previous: { id: "1", label: "A", resourceUri: "URI1" }, next: undefined });
  });
});
