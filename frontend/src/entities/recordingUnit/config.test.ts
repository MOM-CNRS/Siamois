import { describe, expect, it, vi } from "vitest";
import { recordingUnitEntityConfig } from "./config";
import { apiFetch } from "../../api/client";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("recordingUnitEntityConfig", () => {
  it("registers under the 'recordingUnit' key that relationTab/project config reference", () => {
    expect(recordingUnitEntityConfig.key).toBe("recordingUnit");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(recordingUnitEntityConfig.collectionPath).toBe("recording-units");
  });

  it("resolves an unscoped schema load to an empty catalog rather than throwing", async () => {
    const catalog = await recordingUnitEntityConfig.list.schema!.load({});
    expect(catalog).toEqual({ fields: {}, columns: [] });
  });

  it("registers the fiche, contained-RU and finds tabs, a header, and a patchAnswers write path", () => {
    expect(recordingUnitEntityConfig.detail.tabs.map((t) => t.key)).toEqual(["fiche", "children", "finds"]);
    expect(recordingUnitEntityConfig.detail.header).toBeDefined();
    expect(recordingUnitEntityConfig.api.patchAnswers).toBeDefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(recordingUnitEntityConfig.list.createForm).toBeDefined();
  });

  // JSF's organization-wide list disables creation for this type (it needs a project).
  it("disables creation on the unscoped list with an explanation", () => {
    expect(recordingUnitEntityConfig.list.createRequiresScope).toMatch(/que depuis un projet/);
  });

  it("derives its bookmark chrome from the resource itself", () => {
    const entity = { id: "7", fullIdentifier: "UE-7", resourceUri: "/recording-unit/7", bookmarked: true } as unknown as Parameters<NonNullable<typeof recordingUnitEntityConfig.detail.chrome>>[0];
    expect(recordingUnitEntityConfig.detail.chrome?.(entity)).toEqual({ resourceUri: "/recording-unit/7", title: "UE-7", bookmarked: true });
  });

  // Previous/next through the generic GET /api/v1/{collection}/{id}/siblings.
  it("navigates siblings through its own collection's siblings endpoint", async () => {
    mockedApiFetch.mockResolvedValue({ data: { previous: { id: "1", label: "A", resourceUri: "URI1" }, next: null } });
    const siblings = await recordingUnitEntityConfig.api.siblings!("7", {});
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/7/siblings");
    expect(siblings).toEqual({ previous: { id: "1", label: "A", resourceUri: "URI1" }, next: undefined });
  });
});
