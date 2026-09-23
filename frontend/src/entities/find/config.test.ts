import { describe, expect, it } from "vitest";
import { findEntityConfig } from "./config";

describe("findEntityConfig", () => {
  it("registers under the 'find' key that relationTab/project config reference", () => {
    expect(findEntityConfig.key).toBe("find");
  });

  it("declares its own REST collection segment for the generic scoped-list URL builder", () => {
    expect(findEntityConfig.collectionPath).toBe("finds");
  });

  it("registers a single fiche tab and a header, and a patchAnswers write path for the list overlay", () => {
    expect(findEntityConfig.detail.tabs).toHaveLength(1);
    expect(findEntityConfig.detail.tabs[0].key).toBe("fiche");
    expect(findEntityConfig.detail.header).toBeDefined();
    expect(findEntityConfig.api.patchAnswers).toBeDefined();
  });

  // Reduced scope (migration plan lot 1): no list.schema (pinned columns only), no
  // api.siblings, no detail.chrome — see config.tsx's own comment for why.
  it("has no dynamic column schema, siblings navigation, or overview chrome yet", () => {
    expect(findEntityConfig.list.schema).toBeUndefined();
    expect(findEntityConfig.api.siblings).toBeUndefined();
    expect(findEntityConfig.detail.chrome).toBeUndefined();
  });

  it("declares an overlay-hosted create form for the list toolbar's own Créer button", () => {
    expect(findEntityConfig.list.createForm).toBeDefined();
  });
});
