import { describe, expect, it } from "vitest";
import { containerEntityConfig } from "./config";

describe("containerEntityConfig", () => {
  it("registers under the 'container' key that relationTab/project config reference", () => {
    expect(containerEntityConfig.key).toBe("container");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(containerEntityConfig.collectionPath).toBe("containers");
  });

  it("registers a single fiche tab and a header, and a patchAnswers write path for the list overlay", () => {
    expect(containerEntityConfig.detail.tabs).toHaveLength(1);
    expect(containerEntityConfig.detail.tabs[0].key).toBe("fiche");
    expect(containerEntityConfig.detail.header).toBeDefined();
    expect(containerEntityConfig.api.patchAnswers).toBeDefined();
  });

  // Reduced scope (migration plan lot 3, same precedent as Phase/Mobilier's own): no
  // list.schema (pinned columns only), no api.siblings, no detail.chrome.
  it("has no dynamic column schema, siblings navigation, or overview chrome yet", () => {
    expect(containerEntityConfig.list.schema).toBeUndefined();
    expect(containerEntityConfig.api.siblings).toBeUndefined();
    expect(containerEntityConfig.detail.chrome).toBeUndefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(containerEntityConfig.list.createForm).toBeDefined();
  });

  // JSF's organization-wide list disables creation for this type (it needs a project).
  it("disables creation on the unscoped list with an explanation", () => {
    expect(containerEntityConfig.list.createRequiresScope).toMatch(/que depuis un projet/);
  });
});
