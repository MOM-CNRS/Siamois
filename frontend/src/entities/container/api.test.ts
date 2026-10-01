import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { registerEntityType } from "../registry";
import { getContainer, listContainers, patchContainerAnswers, createContainer } from "./api";

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

describe("listContainers", () => {
  it("builds a scoped URL when given a scope, via the shared fetchList helper", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await listContainers({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/5/containers?");
  });

  it("normalizes meta.total to totalCount like every other entity's list", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "containers", id: "1", identifier: "C1" }],
      meta: { total: 3, limit: 10, offset: 0 },
    });

    const result = await listContainers({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    expect(result).toEqual({
      data: [{ resourceType: "containers", id: "1", identifier: "C1" }],
      totalCount: 3,
      limit: 10,
      offset: 0,
    });
  });
});

describe("getContainer", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const container = { resourceType: "containers", id: "42", identifier: "C42" };
    mockedApiFetch.mockResolvedValueOnce({ data: container });

    const result = await getContainer(42);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/containers/42");
    expect(result).toEqual(container);
  });
});

describe("patchContainerAnswers", () => {
  it("sends a PATCH with the answers map and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchContainerAnswers(42, { "-608": { value: 12.5 } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/containers/42", {
      method: "PATCH",
      body: { answers: { "-608": { value: 12.5 } } },
    });
  });
});

describe("createContainer", () => {
  it("sends a POST with projectId and typeId, and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: { resourceType: "containers", id: "55" } });

    const result = await createContainer({ projectId: "5", typeId: "9" });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/containers", {
      method: "POST",
      body: { projectId: "5", typeId: "9" },
    });
    expect(result).toEqual({ resourceType: "containers", id: "55" });
  });
});
