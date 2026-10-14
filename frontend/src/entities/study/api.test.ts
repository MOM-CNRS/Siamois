import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { registerEntityType } from "../registry";
import { getStudy, listStudies, patchStudyAnswers, createStudy } from "./api";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

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

describe("listStudies", () => {
  it("builds a scoped URL when given a scope, via the shared fetchList helper", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await listStudies({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/5/studies?");
  });

  it("normalizes meta.total to totalCount like every other entity's list", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "studies", id: "1", identifier: "C1" }],
      meta: { total: 3, limit: 10, offset: 0 },
    });

    const result = await listStudies({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    expect(result).toEqual({
      data: [{ resourceType: "studies", id: "1", identifier: "C1" }],
      totalCount: 3,
      limit: 10,
      offset: 0,
    });
  });
});

describe("getStudy", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const study = { resourceType: "studies", id: "42", identifier: "C42" };
    mockedApiFetch.mockResolvedValueOnce({ data: study });

    const result = await getStudy(42);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/studies/42");
    expect(result).toEqual(study);
  });
});

describe("patchStudyAnswers", () => {
  it("sends a PATCH with the answers map and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchStudyAnswers(42, { "-608": { value: 12.5 } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/studies/42", {
      method: "PATCH",
      body: { answers: { "-608": { value: 12.5 } } },
    });
  });
});

describe("createStudy", () => {
  it("sends a POST with projectId and typeId, and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: { resourceType: "studies", id: "55" } });

    const result = await createStudy({ projectId: "5", typeId: "9" });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/studies", {
      method: "POST",
      body: { projectId: "5", typeId: "9" },
    });
    expect(result).toEqual({ resourceType: "studies", id: "55" });
  });
});
