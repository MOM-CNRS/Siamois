import { describe, expect, it } from "vitest";
import { recordingUnitEntityConfig } from "./config";

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

  it("has no detail tabs yet (fiche not migrated) and a patchAnswers write path for the list overlay", () => {
    expect(recordingUnitEntityConfig.detail.tabs).toEqual([]);
    expect(recordingUnitEntityConfig.api.patchAnswers).toBeDefined();
  });
});
