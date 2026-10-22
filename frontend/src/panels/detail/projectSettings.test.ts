import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { openProjectSettings, openTablesSettings, settingsTableOf } from "./projectSettings";

const assign = vi.fn();
const originalLocation = window.location;

beforeEach(() => {
  assign.mockReset();
  Object.defineProperty(window, "location", { configurable: true, value: { assign } });
});

afterEach(() => {
  Object.defineProperty(window, "location", { configurable: true, value: originalLocation });
});

describe("projectSettings", () => {
  it("opens the settings page of a project", () => {
    openProjectSettings(7);

    expect(assign).toHaveBeenCalledWith(expect.stringMatching(/\/settings\/project\/7$/));
  });

  it("opens the tables of a project on a table alone", () => {
    openTablesSettings(7, "UE");

    expect(assign).toHaveBeenCalledWith(expect.stringMatching(/\/settings\/project\/7\/tables\?table=UE$/));
  });

  it("opens the tables of a project on a type, and on a field of it", () => {
    openTablesSettings(7, "MOBILIER", 12, 34);

    const url = String(assign.mock.calls[0][0]);
    expect(url).toContain("table=MOBILIER");
    expect(url).toContain("typeId=12");
    expect(url).toContain("fieldId=34");
  });

  it("never asks for a field without its type", () => {
    openTablesSettings(7, "UE", undefined, 34);

    expect(String(assign.mock.calls[0][0])).not.toContain("fieldId");
  });

  it("maps each project fiche to its table, and the project and the place to none", () => {
    expect(settingsTableOf("recordingUnit")).toBe("UE");
    expect(settingsTableOf("find")).toBe("MOBILIER");
    expect(settingsTableOf("phase")).toBe("PHASE");
    expect(settingsTableOf("container")).toBe("CONTENANT");
    expect(settingsTableOf("document")).toBe("DOCUMENT");
    expect(settingsTableOf("study")).toBe("STUDY");
    expect(settingsTableOf("project")).toBeUndefined();
    expect(settingsTableOf("place")).toBeUndefined();
  });
});
