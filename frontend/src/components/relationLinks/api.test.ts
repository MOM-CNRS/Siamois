import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    addHierarchyLink,
    addStratigraphyLink,
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

  it("creates a stratigraphic link with the position of the other unit, its type and its uncertainty", async () => {
    await addStratigraphyLink(5, "anterior", "9", { typeConceptId: 7, uncertain: true });
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/5/stratigraphic-relationships", {
      method: "POST",
      body: { relatedRecordingUnitId: 9, position: "anterior", typeConceptId: 7, uncertain: true },
    });
  });

  it("replaces and removes a stratigraphic link", async () => {
    await updateStratigraphyLink(5, "9", { uncertain: true });
    await removeStratigraphyLink(5, "9");
    expect(apiFetch).toHaveBeenNthCalledWith(1, "/api/v1/recording-units/5/stratigraphic-relationships/9", {
      method: "PUT",
      body: { typeConceptId: null, uncertain: true },
    });
    expect(apiFetch).toHaveBeenNthCalledWith(2, "/api/v1/recording-units/5/stratigraphic-relationships/9", { method: "DELETE" });
  });
});
