import { beforeEach, describe, expect, it } from "vitest";
import { configureBasePath } from "../api/basePath";
import { decodeFocusUrl, encodeFocusUrl } from "./url";

function locationOf(url: string) {
  const { pathname, search } = new URL(url, "http://localhost");
  return { pathname, search };
}

describe("focus URLs", () => {
  beforeEach(() => configureBasePath("/siamois"));

  it("matches the base64url tokens FocusViewBean decodes", () => {
    // Java: Base64.getUrlEncoder().withoutPadding().encodeToString("/action-unit/12".getBytes())
    expect(encodeFocusUrl({ mainPath: "/action-unit/12" })).toBe("/siamois/focus/L2FjdGlvbi11bml0LzEy");
  });

  it.each([
    { mainPath: "/action-unit" },
    { mainPath: "/action-unit/12" },
    { mainPath: "/action-unit/12", overviewPath: "/recording-unit/3" },
    { mainPath: "/specimen/7", backUrl: "/siamois/focus/L2FjdGlvbi11bml0LzEy?s=L3NwZWNpbWVuLzc" },
    { mainPath: "/action-unit/12?tab=fiche", overviewPath: "/phase/9", backUrl: "/siamois/focus/abc" },
    { mainPath: "/lieu/é/ü" },
  ])("round-trips %j", (parts) => {
    expect(decodeFocusUrl(locationOf(encodeFocusUrl(parts)))).toEqual({
      overviewPath: undefined,
      backUrl: undefined,
      ...parts,
    });
  });

  it("puts nothing in the query string without an overview or a way back", () => {
    expect(encodeFocusUrl({ mainPath: "/phase" })).not.toContain("?");
  });

  it.each(["/siamois/action-unit/12", "/siamois/focus/", "/siamois/focus/a/b", "/other/focus/abc", "/siamois/focus/%%%"])(
    "reads %s as no focus URL",
    (path) => {
      expect(decodeFocusUrl(locationOf(path))).toBeNull();
    },
  );

  it("reads a malformed token in the query as no focus URL", () => {
    expect(decodeFocusUrl(locationOf("/siamois/focus/L3BoYXNl?s=***"))).toBeNull();
  });
});
