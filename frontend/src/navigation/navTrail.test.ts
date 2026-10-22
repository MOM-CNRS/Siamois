import { beforeEach, describe, expect, it } from "vitest";

const entry = (name: string) => ({ url: `/focus/${name}`, mainPath: `/${name}` });

async function freshTrail() {
  delete (globalThis as { __siamoisNavTrail?: unknown }).__siamoisNavTrail;
  window.sessionStorage.clear();
  return import("./navTrail");
}

describe("navTrail", () => {
  beforeEach(() => {
    delete (globalThis as { __siamoisNavTrail?: unknown }).__siamoisNavTrail;
  });

  it("walks back and forward, with nothing beyond either end", async () => {
    const trail = await freshTrail();
    trail.recordVisit(entry("a"));
    trail.recordVisit(entry("b"));
    trail.recordVisit(entry("c"));

    expect(trail.nextEntry()).toBeUndefined();
    expect(trail.previousEntry()?.mainPath).toBe("/b");
    expect(trail.moveTrail(-1)?.mainPath).toBe("/b");
    expect(trail.moveTrail(-1)?.mainPath).toBe("/a");
    expect(trail.moveTrail(-1)).toBeUndefined();
    expect(trail.nextEntry()?.mainPath).toBe("/b");
    expect(trail.moveTrail(1)?.mainPath).toBe("/b");
  });

  it("does not add the page it stands on again, and drops what was ahead on a new page", async () => {
    const trail = await freshTrail();
    trail.recordVisit(entry("a"));
    trail.recordVisit(entry("b"));
    trail.moveTrail(-1);
    // The restored page is recorded again once the panes show it: nothing changes.
    trail.recordVisit(entry("a"));
    expect(trail.nextEntry()?.mainPath).toBe("/b");

    trail.recordVisit(entry("x"));
    expect(trail.nextEntry()).toBeUndefined();
    expect(trail.previousEntry()?.mainPath).toBe("/a");
  });
});
