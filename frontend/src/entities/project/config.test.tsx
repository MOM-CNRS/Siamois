import { describe, expect, it } from "vitest";
import { projectEntityConfig } from "./config";
import type { ProjectDetail } from "./types";

describe("projectEntityConfig detail tabs", () => {
  it("declares the fiche tab first, then the recording-units relation tab", () => {
    const keys = projectEntityConfig.detail.tabs.map((t) => t.key);
    expect(keys).toEqual(["fiche", "recording-units"]);
  });

  it("the recording-units tab reads its badge count from _counts.recordingUnits", () => {
    const tab = projectEntityConfig.detail.tabs.find((t) => t.key === "recording-units")!;
    const entity = { _counts: { recordingUnits: 7 } } as unknown as ProjectDetail;
    expect(tab.badge?.(entity)).toBe(7);
  });

  it("the recording-units tab badge falls back to 0 when _counts is absent", () => {
    const tab = projectEntityConfig.detail.tabs.find((t) => t.key === "recording-units")!;
    const entity = {} as unknown as ProjectDetail;
    expect(tab.badge?.(entity)).toBe(0);
  });
});
