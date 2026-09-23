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

  it("registers a single fiche tab and a header, and a patchAnswers write path for the list overlay", () => {
    expect(recordingUnitEntityConfig.detail.tabs).toHaveLength(1);
    expect(recordingUnitEntityConfig.detail.tabs[0].key).toBe("fiche");
    expect(recordingUnitEntityConfig.detail.header).toBeDefined();
    expect(recordingUnitEntityConfig.api.patchAnswers).toBeDefined();
  });

  // No config.api.siblings (no scoped /recording-units/{id}/siblings endpoint yet) and no
  // config.detail.chrome (no resourceUri on RecordingUnitResource yet) — see config.tsx's own
  // comment for why both are deliberately out of scope for now.
  it("still has no siblings navigation or overview chrome", () => {
    expect(recordingUnitEntityConfig.api.siblings).toBeUndefined();
    expect(recordingUnitEntityConfig.detail.chrome).toBeUndefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(recordingUnitEntityConfig.list.createForm).toBeDefined();
  });
});
