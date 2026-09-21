import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { getProject, listProjects, patchProject } from "./api";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

describe("listProjects", () => {
  it("builds the query string from ListParams and normalizes meta.total to totalCount", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "projects", id: "1", name: "A", fullIdentifier: "A-1", identifier: "A-1" }],
      meta: { total: 42, limit: 20, offset: 0 },
    });

    const result = await listProjects({
      offset: 0,
      limit: 20,
      search: "fouille",
      sort: "name:asc",
      organizationId: 100,
      fields: "-118,-109",
    });

    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/projects?");
    expect(path).toContain("offset=0");
    expect(path).toContain("limit=20");
    expect(path).toContain("search=fouille");
    expect(path).toContain("sort=name%3Aasc");
    expect(path).toContain("organizationId=100");
    expect(path).toContain("fields=-118%2C-109");

    expect(result).toEqual({
      data: [{ resourceType: "projects", id: "1", name: "A", fullIdentifier: "A-1", identifier: "A-1" }],
      totalCount: 42,
      limit: 20,
      offset: 0,
    });
  });

  it("omits optional params from the query string when absent", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 20, offset: 0 } });

    await listProjects({ offset: 0, limit: 20 });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).not.toContain("search=");
    expect(path).not.toContain("sort=");
    expect(path).not.toContain("organizationId=");
    expect(path).not.toContain("fields=");
  });
});

describe("listProjects filters", () => {
  it("encodes a contains filter as a bare f.<key>", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 20, offset: 0 } });
    await listProjects({ offset: 0, limit: 20, filters: { name: { op: "contains", v: "foss" } } });
    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("f.name=foss");
  });

  it("encodes an in filter as repeated f.<key> params", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 20, offset: 0 } });
    await listProjects({ offset: 0, limit: 20, filters: { status: { op: "in", v: ["12", "44"] } } });
    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("f.status=12");
    expect(path).toContain("f.status=44");
  });

  it("encodes a range filter as f.<key>.from / f.<key>.to", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 20, offset: 0 } });
    await listProjects({ offset: 0, limit: 20, filters: { openingRate: { op: "range", from: "10", to: "40" } } });
    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("f.openingRate.from=10");
    expect(path).toContain("f.openingRate.to=40");
  });

  it("omits f.* params entirely when filters is absent", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 20, offset: 0 } });
    await listProjects({ offset: 0, limit: 20 });
    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).not.toContain("f.");
  });
});

describe("getProject", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const project = { resourceType: "projects", id: "5", name: "B", fullIdentifier: "B-1", identifier: "B-1" };
    mockedApiFetch.mockResolvedValueOnce({ data: project });

    const result = await getProject(5);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5");
    expect(result).toEqual(project);
  });

  it("forwards an answers map as-is (the click-to-edit overlay's write path)", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchProject(5, { answers: { "-118": { value: "12" }, "-115": { values: ["1", "2"] } } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5", {
      method: "PATCH",
      body: { answers: { "-118": { value: "12" }, "-115": { values: ["1", "2"] } } },
    });
  });
});

describe("patchProject", () => {
  it("sends a PATCH with the given fields and unwraps the data envelope", async () => {
    const project = { resourceType: "projects", id: "5", name: "Renamed", fullIdentifier: "B-1", identifier: "B-1" };
    mockedApiFetch.mockResolvedValueOnce({ data: project });

    const result = await patchProject(5, { name: "Renamed" });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5", {
      method: "PATCH",
      body: { name: "Renamed" },
    });
    expect(result).toEqual(project);
  });

  it("forwards an answers map as-is (the click-to-edit overlay's write path)", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchProject(5, { answers: { "-118": { value: "12" }, "-115": { values: ["1", "2"] } } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5", {
      method: "PATCH",
      body: { answers: { "-118": { value: "12" }, "-115": { values: ["1", "2"] } } },
    });
  });
});
