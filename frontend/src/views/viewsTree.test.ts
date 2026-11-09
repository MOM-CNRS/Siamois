import {describe, expect, it} from "vitest";
import type {ViewResource} from "../api/resources/views";
import {groupViews, matches, projectOfView} from "./viewsTree";

const view = (id: number, resourceType: string, listFilters?: Record<string, unknown>): ViewResource => ({
  id, resourceType, title: `v${id}`, kind: "USER", state: { listFilters }, canEdit: true,
});

describe("viewsTree", () => {
  it("ties a view to a project only when it picks exactly one", () => {
    expect(projectOfView(view(1, "recordingUnit", { project: { op: "in", v: ["5"] } }))).toBe("5");
    expect(projectOfView(view(2, "recordingUnit", { project: { op: "in", v: ["5", "6"] } }))).toBeUndefined();
    expect(projectOfView(view(3, "recordingUnit"))).toBeUndefined();
  });

  it("groups views by project, the others by table", () => {
    const { byProject, byTable } = groupViews([
      view(1, "recordingUnit", { project: { op: "in", v: ["5"] } }),
      view(2, "recordingUnit"),
      view(3, "find"),
    ]);
    expect([...byProject.keys()]).toEqual(["5"]);
    expect(byTable.get("recordingUnit")?.map((v) => v.id)).toEqual([2]);
    expect(byTable.get("find")?.map((v) => v.id)).toEqual([3]);
  });

  it("matches ignoring case, everything on an empty search", () => {
    expect(matches("Unités", "uni")).toBe(true);
    expect(matches("Unités", "")).toBe(true);
    expect(matches("Unités", "xyz")).toBe(false);
  });
});
