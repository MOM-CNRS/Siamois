import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { registerEntityType } from "../registry";
import { getFind, listFinds, patchFindAnswers } from "./api";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

// fetchList (entities/listApi.ts) resolves a scope's collection path through the registry —
// "project" must be registered for the scoped URL to come out as "/projects/{id}/…", matching
// EntityTypeConfig.collectionPath, not the raw scope.entityType string.
registerEntityType({
  key: "project",
  labels: { singular: "Projet", plural: "Projets" },
  collectionPath: "projects",
  icon: "bi bi-arrow-down-square",
  api: { list: async () => ({ data: [], totalCount: 0, limit: 10, offset: 0 }), get: async () => ({}) },
  list: { columns: [], searchable: false },
  detail: { tabs: [] },
  routes: { list: "/project", detail: (id) => `/project/${id}` },
});

beforeEach(() => {
  mockedApiFetch.mockClear();
});

describe("listFinds", () => {
  it("builds a scoped URL off the parent project's own collectionPath, not 'finds'", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await listFinds({
      offset: 0,
      limit: 10,
      scope: { entityType: "project", id: 5, path: "mobiliers" },
    });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/5/mobiliers?");
  });

  it("normalizes meta.total to totalCount like every other entity's list", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "finds", id: "1", fullIdentifier: "INST-PROJ-M1" }],
      meta: { total: 3, limit: 10, offset: 0 },
    });

    const result = await listFinds({
      offset: 0,
      limit: 10,
      scope: { entityType: "project", id: 5, path: "mobiliers" },
    });

    expect(result).toEqual({
      data: [{ resourceType: "finds", id: "1", fullIdentifier: "INST-PROJ-M1" }],
      totalCount: 3,
      limit: 10,
      offset: 0,
    });
  });
});

describe("getFind", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const find = { resourceType: "finds", id: "42", fullIdentifier: "INST-PROJ-M42" };
    mockedApiFetch.mockResolvedValueOnce({ data: find });

    const result = await getFind(42);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/finds/42");
    expect(result).toEqual(find);
  });
});

describe("patchFindAnswers", () => {
  it("sends a PATCH with the answers map and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchFindAnswers(42, { "-10": { value: "silex" } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/finds/42", {
      method: "PATCH",
      body: { answers: { "-10": { value: "silex" } } },
    });
  });
});
