import {describe, expect, it} from "vitest";
import type {ViewResource} from "../api/resources/views";
import {matches, projectOfView} from "./viewsTree";

const view = (id: number, resourceType: string, listFilters?: Record<string, unknown>): ViewResource => ({
  id, resourceType, title: `v${id}`, kind: "USER", state: { listFilters }, canEdit: true,
});

describe("viewsTree", () => {
  it("ties a view to a project only when it picks exactly one", () => {
    expect(projectOfView(view(1, "recordingUnit", { project: { op: "in", v: ["5"] } }))).toBe("5");
    expect(projectOfView(view(2, "recordingUnit", { project: { op: "in", v: ["5", "6"] } }))).toBeUndefined();
    expect(projectOfView(view(3, "recordingUnit"))).toBeUndefined();
  });

  it("matches ignoring case, everything on an empty search", () => {
    expect(matches("Unités", "uni")).toBe(true);
    expect(matches("Unités", "")).toBe(true);
    expect(matches("Unités", "xyz")).toBe(false);
  });
});
