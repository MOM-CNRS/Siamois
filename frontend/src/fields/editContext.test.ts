import { describe, expect, it } from "vitest";
import { editContextOf } from "./editContext";

describe("editContextOf", () => {
  it("reads the project off a detail's projectId", () => {
    expect(editContextOf({ id: 4, projectId: "9", fullIdentifier: "UE-4" }, "recordingUnit", 1)).toEqual({
      organizationId: 1,
      projectId: "9",
      entityType: "recordingUnit",
      entityId: 4,
      entityLabel: "UE-4",
    });
  });

  it("reads it off an organization-wide row's project ref", () => {
    expect(editContextOf({ id: 2, project: { resourceId: "9" } }, "phase", 1).projectId).toBe("9");
  });

  it("takes a project as its own project", () => {
    expect(editContextOf({ id: 9, name: "Fouille" }, "project", 1).projectId).toBe("9");
  });

  it("leaves it out for an entity with no project (a place)", () => {
    expect(editContextOf({ id: 3, name: "Cave" }, "place", 1).projectId).toBeUndefined();
  });
});
