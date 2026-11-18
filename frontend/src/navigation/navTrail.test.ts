import {beforeEach, describe, expect, it, vi} from "vitest";

const ORGANIZATION = 1;
const OTHER_ORGANIZATION = 2;

const entry = (name: string) => ({ url: `/focus/${name}`, mainPath: `/${name}` });

async function freshTrail() {
  delete (globalThis as { __siamoisNavTrail?: unknown }).__siamoisNavTrail;
  vi.resetModules();
  return import("./navTrail");
}

describe("navTrail", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it("walks back and forward, with nothing beyond either end", async () => {
    const trail = await freshTrail();
    trail.recordVisit(ORGANIZATION, entry("a"));
    trail.recordVisit(ORGANIZATION, entry("b"));
    trail.recordVisit(ORGANIZATION, entry("c"));

    expect(trail.nextEntry(ORGANIZATION)).toBeUndefined();
    expect(trail.previousEntry(ORGANIZATION)?.mainPath).toBe("/b");
    expect(trail.moveTrail(ORGANIZATION, -1)?.mainPath).toBe("/b");
    expect(trail.moveTrail(ORGANIZATION, -1)?.mainPath).toBe("/a");
    expect(trail.moveTrail(ORGANIZATION, -1)).toBeUndefined();
    expect(trail.nextEntry(ORGANIZATION)?.mainPath).toBe("/b");
    expect(trail.moveTrail(ORGANIZATION, 1)?.mainPath).toBe("/b");
  });

  it("does not add the page it stands on again, and drops what was ahead on a new page", async () => {
    const trail = await freshTrail();
    trail.recordVisit(ORGANIZATION, entry("a"));
    trail.recordVisit(ORGANIZATION, entry("b"));
    trail.moveTrail(ORGANIZATION, -1);
    // The restored page is recorded again once the panes show it: nothing changes.
    trail.recordVisit(ORGANIZATION, entry("a"));
    expect(trail.nextEntry(ORGANIZATION)?.mainPath).toBe("/b");

    trail.recordVisit(ORGANIZATION, entry("x"));
    expect(trail.nextEntry(ORGANIZATION)).toBeUndefined();
    expect(trail.previousEntry(ORGANIZATION)?.mainPath).toBe("/a");
  });

  it("never leads to a page of another organisation", async () => {
    const trail = await freshTrail();
    trail.recordVisit(ORGANIZATION, entry("a"));
    trail.recordVisit(ORGANIZATION, entry("b"));

    trail.recordVisit(OTHER_ORGANIZATION, entry("home"));

    expect(trail.previousEntry(OTHER_ORGANIZATION)).toBeUndefined();
    expect(trail.moveTrail(OTHER_ORGANIZATION, -1)).toBeUndefined();
  });

  it("finds an organisation's trail where it was left when the user comes back to it", async () => {
    const trail = await freshTrail();
    trail.recordVisit(ORGANIZATION, entry("a"));
    trail.recordVisit(ORGANIZATION, entry("b"));
    trail.recordVisit(OTHER_ORGANIZATION, entry("home"));

    trail.recordVisit(ORGANIZATION, entry("c"));

    expect(trail.moveTrail(ORGANIZATION, -1)?.mainPath).toBe("/b");
    expect(trail.moveTrail(ORGANIZATION, -1)?.mainPath).toBe("/a");
  });

  it("keeps each organisation's trail across a page load", async () => {
    const before = await freshTrail();
    before.recordVisit(ORGANIZATION, entry("a"));
    before.recordVisit(ORGANIZATION, entry("b"));
    before.recordVisit(OTHER_ORGANIZATION, entry("home"));

    const after = await freshTrail();

    expect(after.previousEntry(ORGANIZATION)?.mainPath).toBe("/a");
    expect(after.previousEntry(OTHER_ORGANIZATION)).toBeUndefined();
  });

  it("gives each organisation its own version of the trail", async () => {
    const trail = await freshTrail();
    const untouched = trail.trailVersion(OTHER_ORGANIZATION);

    trail.recordVisit(ORGANIZATION, entry("a"));

    expect(trail.trailVersion(OTHER_ORGANIZATION)).toBe(untouched);
    expect(trail.trailVersion(ORGANIZATION)).not.toBe(untouched);
  });
});
