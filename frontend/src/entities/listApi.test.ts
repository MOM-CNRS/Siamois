import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../api/client";
import { fetchList } from "./listApi";
import { registerEntityType } from "./registry";
import type { EntityTypeConfig } from "./types";

vi.mock("../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

function stubConfig(key: string, collectionPath: string): EntityTypeConfig {
  return {
    key,
    labels: { singular: key, plural: key },
    collectionPath,
    icon: "bi bi-question",
    api: {
      list: async () => ({ data: [], totalCount: 0, limit: 10, offset: 0 }),
      get: async () => ({}),
    },
    list: { columns: [], searchable: false },
    detail: { tabs: [] },
    routes: { list: `/${key}`, detail: (id) => `/${key}/${id}` },
  };
}

describe("fetchList", () => {
  it("builds an unscoped URL from the entity's own collectionPath", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await fetchList("projects", { offset: 0, limit: 10 });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects?");
  });

  it("serializes offset/limit/search/sort/organizationId/fields the same way for every entity", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 42, limit: 20, offset: 0 } });

    const result = await fetchList("recording-units", {
      offset: 0,
      limit: 20,
      search: "fouille",
      sort: "creationTime:desc",
      organizationId: 100,
      fields: "type,author",
    });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/recording-units?");
    expect(path).toContain("offset=0");
    expect(path).toContain("limit=20");
    expect(path).toContain("search=fouille");
    expect(path).toContain("sort=creationTime%3Adesc");
    expect(path).toContain("organizationId=100");
    expect(path).toContain("fields=type%2Cauthor");
    expect(result).toEqual({ data: [], totalCount: 42, limit: 20, offset: 0 });
  });

  it("encodes filters via the shared f.<key> contract", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await fetchList("recording-units", {
      offset: 0,
      limit: 10,
      filters: { type: { op: "in", v: ["12", "44"] } },
    });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("f.type=12");
    expect(path).toContain("f.type=44");
  });

  it("builds a scoped URL off the parent entity's own collectionPath", async () => {
    registerEntityType(stubConfig("project", "projects"));
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 3, limit: 10, offset: 0 } });

    await fetchList("recording-units", {
      offset: 0,
      limit: 10,
      scope: { entityType: "project", id: 5 },
    });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/5/recording-units?");
  });

  it("uses scope.path to override the child segment when it differs from collectionPath", async () => {
    registerEntityType(stubConfig("actionUnit", "action-units"));
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await fetchList("finds", {
      offset: 0,
      limit: 10,
      scope: { entityType: "actionUnit", id: 7, path: "mobiliers" },
    });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/action-units/7/mobiliers?");
  });

  it("falls back to the scope's raw entityType if it isn't registered", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await fetchList("things", {
      offset: 0,
      limit: 10,
      scope: { entityType: "unregistered-thing", id: 1 },
    });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/unregistered-thing/1/things?");
  });
});
