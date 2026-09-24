import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { registerEntityType } from "../registry";
import { getRecordingUnit, listRecordingUnits, patchRecordingUnitAnswers } from "./api";

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

describe("listRecordingUnits", () => {
  it("builds a scoped URL when given a scope, via the shared fetchList helper", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await listRecordingUnits({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/5/recording-units?");
  });

  it("normalizes meta.total to totalCount like every other entity's list", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "recording-units", id: "1", fullIdentifier: "INST-PROJ-UE1" }],
      meta: { total: 3, limit: 10, offset: 0 },
    });

    const result = await listRecordingUnits({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    expect(result).toEqual({
      data: [{ resourceType: "recording-units", id: "1", fullIdentifier: "INST-PROJ-UE1" }],
      totalCount: 3,
      limit: 10,
      offset: 0,
    });
  });
});

describe("getRecordingUnit", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const ru = { resourceType: "recording-units", id: "42", fullIdentifier: "INST-PROJ-UE42" };
    mockedApiFetch.mockResolvedValueOnce({ data: ru });

    const result = await getRecordingUnit(42);

    // counts: the fiche's tab badges (contained RUs, finds).
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42?counts=specimen,children");
    expect(result).toEqual(ru);
  });
});

describe("patchRecordingUnitAnswers", () => {
  it("sends a PATCH with the answers map and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchRecordingUnitAnswers(42, { "-90": { value: "silex" } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42", {
      method: "PATCH",
      body: { answers: { "-90": { value: "silex" } } },
    });
  });
});
