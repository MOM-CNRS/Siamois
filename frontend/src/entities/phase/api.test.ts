import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { registerEntityType } from "../registry";
import { getPhase, listPhases, patchPhaseAnswers, createPhase } from "./api";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

// fetchList (entities/listApi.ts) resolves a scope's collection path through the registry —
// "project" must be registered for the scoped URL to come out as "/projects/{id}/…".
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

describe("listPhases", () => {
  it("builds a scoped URL when given a scope, via the shared fetchList helper", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await listPhases({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/5/phases?");
  });

  it("normalizes meta.total to totalCount like every other entity's list", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "phases", id: "1", identifier: "PH1" }],
      meta: { total: 3, limit: 10, offset: 0 },
    });

    const result = await listPhases({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    expect(result).toEqual({
      data: [{ resourceType: "phases", id: "1", identifier: "PH1" }],
      totalCount: 3,
      limit: 10,
      offset: 0,
    });
  });
});

describe("getPhase", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const phase = { resourceType: "phases", id: "42", identifier: "PH42" };
    mockedApiFetch.mockResolvedValueOnce({ data: phase });

    const result = await getPhase(42);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/phases/42");
    expect(result).toEqual(phase);
  });
});

describe("patchPhaseAnswers", () => {
  it("sends a PATCH with the answers map and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchPhaseAnswers(42, { "-503": { value: "Titre" } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/phases/42", {
      method: "PATCH",
      body: { answers: { "-503": { value: "Titre" } } },
    });
  });
});

describe("createPhase", () => {
  it("sends a POST with projectId and typeId, and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: { resourceType: "phases", id: "55" } });

    const result = await createPhase({ projectId: "5", typeId: "9" });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/phases", {
      method: "POST",
      body: { projectId: "5", typeId: "9" },
    });
    expect(result).toEqual({ resourceType: "phases", id: "55" });
  });
});
