import { describe, expect, it } from "vitest";
import { getAllEntityTypes, getEntityType, registerEntityType } from "./registry";
import type { EntityTypeConfig } from "./types";

function fakeConfig(key: string): EntityTypeConfig {
  return {
    key,
    labels: { singular: key, plural: key },
    api: { list: async () => ({ data: [], totalCount: 0, limit: 20, offset: 0 }), get: async () => ({}) },
    list: { columns: [], searchable: false },
    detail: { tabs: [] },
    routes: { list: `/${key}`, detail: (id) => `/${key}/${id}` },
  };
}

describe("registry", () => {
  it("returns a registered config by key", () => {
    registerEntityType(fakeConfig("registry-test-a"));
    expect(getEntityType("registry-test-a")?.key).toBe("registry-test-a");
  });

  it("returns undefined for an unregistered key", () => {
    expect(getEntityType("registry-test-does-not-exist")).toBeUndefined();
  });

  it("getAllEntityTypes includes every registered config", () => {
    registerEntityType(fakeConfig("registry-test-b"));
    registerEntityType(fakeConfig("registry-test-c"));
    const keys = getAllEntityTypes().map((c) => c.key);
    expect(keys).toContain("registry-test-b");
    expect(keys).toContain("registry-test-c");
  });
});
