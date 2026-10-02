import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { registerEntityType } from "../registry";
import { getDocument, listDocuments, patchDocumentAnswers, createDocument, linkDocument, unlinkDocument } from "./api";

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

describe("listDocuments", () => {
  it("builds a scoped URL when given a scope, via the shared fetchList helper", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await listDocuments({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects/5/documents?");
  });

  it("normalizes meta.total to totalCount like every other entity's list", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "documents", id: "1", identifier: "DOC1" }],
      meta: { total: 3, limit: 10, offset: 0 },
    });

    const result = await listDocuments({ offset: 0, limit: 10, scope: { entityType: "project", id: 5 } });

    expect(result).toEqual({
      data: [{ resourceType: "documents", id: "1", identifier: "DOC1" }],
      totalCount: 3,
      limit: 10,
      offset: 0,
    });
  });
});

describe("getDocument", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const doc = { resourceType: "documents", id: "42", identifier: "DOC42" };
    mockedApiFetch.mockResolvedValueOnce({ data: doc });

    const result = await getDocument(42);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/documents/42");
    expect(result).toEqual(doc);
  });
});

describe("patchDocumentAnswers", () => {
  it("sends a PATCH with the answers map and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchDocumentAnswers(42, { "-708": { value: "Titre" } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/documents/42", {
      method: "PATCH",
      body: { answers: { "-708": { value: "Titre" } } },
    });
  });
});

describe("createDocument", () => {
  it("sends a POST with projectId and categoryId, and unwraps the data envelope", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: { resourceType: "documents", id: "55" } });

    const result = await createDocument({ projectId: "5", categoryId: "9" });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/documents", {
      method: "POST",
      body: { projectId: "5", categoryId: "9" },
    });
    expect(result).toEqual({ resourceType: "documents", id: "55" });
  });
});

describe("linking a document to an entity", () => {
  it("PUTs the link under the entity's own segment", async () => {
    mockedApiFetch.mockResolvedValue(undefined);

    await linkDocument("recording-units", 5, 11);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/5/documents/11", { method: "PUT" });
  });

  it("DELETEs the link, not the document", async () => {
    mockedApiFetch.mockResolvedValue(undefined);

    await unlinkDocument("finds", 5, 11);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/finds/5/documents/11", { method: "DELETE" });
  });
});
