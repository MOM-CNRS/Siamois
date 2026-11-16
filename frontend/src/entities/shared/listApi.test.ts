import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../../api/client";
import {fetchList, fetchMap, fetchMapFeature, resultSetOf} from "./listApi";
import {registerEntityType} from "../registry";
import type {EntityTypeConfig} from "./types";

vi.mock("../../api/client", () => ({
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
    registerEntityType(stubConfig("actionUnit", "projects"));
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await fetchList("finds", {
      offset: 0,
      limit: 10,
      scope: { entityType: "actionUnit", id: 7, path: "finds" },
    });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/7/finds?");
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

describe("fetchMap", () => {
  it("asks the collection's /map for the same result set, without paging", async () => {
    mockedApiFetch.mockResolvedValueOnce({ features: [], meta: { total: 0, examined: 0 } });

    await fetchMap("recording-units", { organizationId: 7, search: "UE" });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/recording-units/map?");
    expect(path).toContain("organizationId=7");
    expect(path).toContain("search=UE");
    expect(path).not.toContain("sort");
    expect(path).not.toContain("offset");
    expect(path).not.toContain("limit");
  });

  it("goes through the project's own sub-collection when scoped to a project", async () => {
    registerEntityType(stubConfig("project", "projects"));
    mockedApiFetch.mockResolvedValueOnce({ features: [], meta: { total: 0, examined: 0 } });

    await fetchMap("recording-units", { scope: { entityType: "project", id: 5 } });

    expect(mockedApiFetch.mock.calls[0][0]).toContain("/api/v1/projects/5/recording-units/map?");
  });
});

describe("resultSetOf", () => {
  it("keeps what selects the rows and drops their order and paging", () => {
    const scope = { entityType: "project", id: 5 };
    expect(resultSetOf({ search: "UE", organizationId: 7, filters: { a: 1 }, scope, sort: "x:asc", offset: 20 } as never))
      .toEqual({ search: "UE", organizationId: 7, filters: { a: 1 }, scope });
  });
});

describe("fetchMapFeature", () => {
  it("asks the entity's own /map and unwraps its feature", async () => {
    const feature = { id: "12", label: "UE-12", geometry: { type: "Point", coordinates: [2, 48] } };
    mockedApiFetch.mockResolvedValueOnce({ feature });

    await expect(fetchMapFeature("recording-units", 12)).resolves.toEqual(feature);
    expect(mockedApiFetch.mock.calls[0][0]).toBe("/api/v1/recording-units/12/map");
  });

  it("answers null for an entity with no placeable geometry", async () => {
    mockedApiFetch.mockResolvedValueOnce({ feature: null });

    await expect(fetchMapFeature("recording-units", "13")).resolves.toBeNull();
  });
});
