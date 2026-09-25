import { beforeEach, describe, expect, it } from "vitest";
import { listPrefsKey, loadListPrefs, reconcileActionBar, saveListPrefs } from "./listPreferences";

beforeEach(() => window.localStorage.clear());

describe("listPreferences", () => {
  it("keys a relation tab apart from the organization-wide list", () => {
    expect(listPrefsKey("recordingUnit")).toBe("siamois.list.recordingUnit.global");
    expect(listPrefsKey("recordingUnit", { entityType: "project", id: 5 })).toBe("siamois.list.recordingUnit.project-tab");
  });

  it("round-trips, merging each save into what's already stored", () => {
    saveListPrefs("k", { visibleColumns: ["a"] });
    saveListPrefs("k", { actionBar: { order: ["x"], inline: [] } });
    expect(loadListPrefs("k")).toEqual({ v: 1, visibleColumns: ["a"], actionBar: { order: ["x"], inline: [] } });
  });

  it("reads corrupt or foreign data as nothing saved", () => {
    window.localStorage.setItem("k", "{nope");
    expect(loadListPrefs("k")).toEqual({ v: 1 });
    window.localStorage.setItem("k", JSON.stringify({ v: 9, visibleColumns: ["a"] }));
    expect(loadListPrefs("k")).toEqual({ v: 1 });
  });

  it("reconciles a saved action layout with the actions that exist now", () => {
    expect(reconcileActionBar(undefined, ["a", "b"])).toEqual({ order: ["a", "b"], inline: ["a", "b"] });
    expect(reconcileActionBar({ order: ["b", "gone", "a"], inline: ["gone"] }, ["a", "b", "c"])).toEqual({
      order: ["b", "a", "c"],
      inline: ["c"],
    });
  });
});
