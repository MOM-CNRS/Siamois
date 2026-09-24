import { describe, expect, it } from "vitest";
import { phaseEntityConfig } from "./config";

describe("phaseEntityConfig", () => {
  it("registers under the 'phase' key that relationTab/project config reference", () => {
    expect(phaseEntityConfig.key).toBe("phase");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(phaseEntityConfig.collectionPath).toBe("phases");
  });

  it("registers a single fiche tab and a header, and a patchAnswers write path for the list overlay", () => {
    expect(phaseEntityConfig.detail.tabs).toHaveLength(1);
    expect(phaseEntityConfig.detail.tabs[0].key).toBe("fiche");
    expect(phaseEntityConfig.detail.header).toBeDefined();
    expect(phaseEntityConfig.api.patchAnswers).toBeDefined();
  });

  // Reduced scope (migration plan lot 2, same precedent as Mobilier's own lot 1): no list.schema
  // (pinned columns only), no api.siblings, no detail.chrome — see config.tsx's own comment.
  it("has no dynamic column schema, siblings navigation, or overview chrome yet", () => {
    expect(phaseEntityConfig.list.schema).toBeUndefined();
    expect(phaseEntityConfig.api.siblings).toBeUndefined();
    expect(phaseEntityConfig.detail.chrome).toBeUndefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(phaseEntityConfig.list.createForm).toBeDefined();
  });

  // JSF's organization-wide list disables creation for this type (it needs a project).
  it("disables creation on the unscoped list with an explanation", () => {
    expect(phaseEntityConfig.list.createRequiresScope).toMatch(/que depuis un projet/);
  });
});
