import { describe, expect, it } from "vitest";
import { apiUrl, configureBasePath } from "./basePath";

describe("basePath", () => {
  it("prefixes paths with the configured base path", () => {
    configureBasePath("/siamois");
    expect(apiUrl("/api/v1/projects")).toBe("/siamois/api/v1/projects");
  });

  it("normalizes a trailing slash on the base path", () => {
    configureBasePath("/siamois/");
    expect(apiUrl("/api/v1/projects")).toBe("/siamois/api/v1/projects");
  });

  it("defaults to no prefix when the base path is empty", () => {
    configureBasePath("");
    expect(apiUrl("/api/v1/projects")).toBe("/api/v1/projects");
  });
});
