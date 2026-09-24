import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { createPlace, getPlace, listPlaces, patchPlaceAnswers } from "./api";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

describe("listPlaces", () => {
  it("uses the organization-wide list at the collection root, like every other entity", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], meta: { total: 0, limit: 10, offset: 0 } });

    await listPlaces({ offset: 0, limit: 10, organizationId: 7, search: "cave", sort: "name:asc" });

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/places?");
    expect(path).toContain("organizationId=7");
    expect(path).toContain("search=cave");
  });

  it("normalizes meta.total to totalCount like every other entity's list", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ resourceType: "places", id: "1", name: "Cave A" }],
      meta: { total: 3, limit: 10, offset: 0 },
    });

    const result = await listPlaces({ offset: 0, limit: 10, organizationId: 7 });

    expect(result).toEqual({
      data: [{ resourceType: "places", id: "1", name: "Cave A" }],
      totalCount: 3,
      limit: 10,
      offset: 0,
    });
  });
});

describe("createPlace", () => {
  it("posts organizationId, name and a numeric typeConceptId", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: { id: 42, name: "Cave A" } });

    const created = await createPlace({ organizationId: 7, name: "Cave A", typeConceptId: "9" });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/places", {
      method: "POST",
      body: { organizationId: 7, name: "Cave A", typeConceptId: 9 },
    });
    expect(created.id).toBe(42);
  });
});

describe("getPlace", () => {
  it("fetches by id and unwraps the data envelope", async () => {
    const place = { resourceType: "places", id: "42", name: "Cave A" };
    mockedApiFetch.mockResolvedValueOnce({ data: place });

    const result = await getPlace(42);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/places/42");
    expect(result).toEqual(place);
  });
});

describe("patchPlaceAnswers", () => {
  it("translates the name field id into PlacePatchRequest's flat `name`", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchPlaceAnswers(5, { "-202": { value: "Nouveau nom" } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/places/5", {
      method: "PATCH",
      body: { name: "Nouveau nom" },
    });
  });

  it("translates the type field id into `typeConceptId`, coerced to a number", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchPlaceAnswers(5, { "-201": { value: "9" } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/places/5", {
      method: "PATCH",
      body: { typeConceptId: 9 },
    });
  });

  it("translates the place-number field id into `placeNumber`", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchPlaceAnswers(5, { "-205": { value: 3 } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/places/5", {
      method: "PATCH",
      body: { placeNumber: 3 },
    });
  });

  it("ignores an unmapped field id (e.g. the address field, deliberately unsupported)", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: {} });

    await patchPlaceAnswers(5, { "-204": { value: { city: "Paris" } } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/places/5", {
      method: "PATCH",
      body: {},
    });
  });

  it("unwraps the data envelope from the response", async () => {
    const updated = { resourceType: "places", id: "5", name: "Nouveau nom" };
    mockedApiFetch.mockResolvedValueOnce({ data: updated });

    const result = await patchPlaceAnswers(5, { "-202": { value: "Nouveau nom" } });

    expect(result).toEqual(updated);
  });
});
