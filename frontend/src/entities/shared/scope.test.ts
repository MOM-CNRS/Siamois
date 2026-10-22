import { describe, expect, it } from "vitest";
import { scopeProjectId } from "./scope";

describe("scopeProjectId", () => {
  it("is the scope itself for a project scope", () => {
    expect(scopeProjectId({ entityType: "project", id: 5 })).toBe("5");
  });

  it("is the projectId carried by any other scope (a recording unit's children, a phase's RUs)", () => {
    expect(scopeProjectId({ entityType: "recordingUnit", id: 94, path: "children", projectId: "6" })).toBe("6");
  });

  it("is undefined without a scope or without a project (a place's children)", () => {
    expect(scopeProjectId(undefined)).toBeUndefined();
    expect(scopeProjectId({ entityType: "place", id: 3, path: "children" })).toBeUndefined();
  });
});
