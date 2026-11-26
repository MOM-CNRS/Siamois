import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    addHierarchyLink,
    addStratigraphyLink,
    linkIdOf,
    removeHierarchyLink,
    removeStratigraphyLink,
    updateStratigraphyLink
} from "./api";

const apiFetch = vi.fn();
vi.mock("../../api/client", () => ({ apiFetch: (...args: unknown[]) => apiFetch(...args) }));

describe("relation graph writes", () => {
  beforeEach(() => apiFetch.mockReset().mockResolvedValue(undefined));

  it("links and unlinks a parent or a child on the hierarchy endpoints", async () => {
    await addHierarchyLink(5, "parents", "9");
    await removeHierarchyLink(5, "children", "9");
    expect(apiFetch).toHaveBeenNthCalledWith(1, "/api/v1/recording-units/5/parents", { method: "POST", body: { relatedRecordingUnitId: 9 } });
    expect(apiFetch).toHaveBeenNthCalledWith(2, "/api/v1/recording-units/5/children/9", { method: "DELETE" });
  });

  it("creates a stratigraphic link through the field it is entered from, with its type and its uncertainty", async () => {
    await addStratigraphyLink("-328", 5, "9", { typeConceptId: 7, uncertain: true });
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/relation-links", {
      method: "POST",
      body: {
        field: -328,
        record: { resourceType: "recording-units", resourceId: 5 },
        other: { resourceType: "recording-units", resourceId: 9 },
        qualifier: 7,
        uncertain: true,
      },
    });
  });

  it("replaces and removes a stratigraphic link by its id, through its field", async () => {
    await updateStratigraphyLink("-329", "900", { uncertain: true });
    await removeStratigraphyLink("-329", "900");
    expect(apiFetch).toHaveBeenNthCalledWith(1, "/api/v1/relation-links/900", {
      method: "PATCH",
      body: { field: -329, qualifier: null, uncertain: true },
    });
    expect(apiFetch).toHaveBeenNthCalledWith(2, "/api/v1/relation-links/900?field=-329", { method: "DELETE" });
  });

  it("refuses a value that does not say which link it stands for", () => {
    expect(() => linkIdOf({ qualifier: {} })).toThrow();
    expect(linkIdOf({ qualifier: { linkId: 900 } })).toBe("900");
  });
});
