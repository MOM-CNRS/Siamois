import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../../api/client";
import {createEntityApi, wgs84GeometryBody} from "./createEntityApi";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => mockedApiFetch.mockReset());

describe("patchGeometry", () => {
  const polygon: GeoJSON.Polygon = { type: "Polygon", coordinates: [[[2.3, 48.8], [2.4, 48.8], [2.4, 48.9], [2.3, 48.8]]] };

  it("PATCHes the drawn geometry in WGS84", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: { id: "12" } });

    await createEntityApi("recording-units").patchGeometry(12, polygon);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/12", {
      method: "PATCH",
      body: { geom: { ...polygon, srid: 4326 } },
    });
  });

  it("removes the geometry with an explicit null", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: { id: "12" } });

    await createEntityApi("recording-units").patchGeometry(12, null);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/12", { method: "PATCH", body: { geom: null } });
  });
});

describe("wgs84GeometryBody", () => {
  it("tags a geometry with SRID 4326 and leaves null alone", () => {
    expect(wgs84GeometryBody({ type: "Point", coordinates: [2, 48] })).toEqual({ type: "Point", coordinates: [2, 48], srid: 4326 });
    expect(wgs84GeometryBody(null)).toBeNull();
  });
});
