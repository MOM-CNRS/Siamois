import {beforeEach, describe, expect, it, vi} from "vitest";
import {ApiError, fetchExternalJson} from "../api/client";
import {fetchCommuneContour} from "./communeContour";

vi.mock("../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/client")>()),
  fetchExternalJson: vi.fn(),
}));

const square: GeoJSON.Polygon = {
  type: "Polygon",
  coordinates: [[[2, 48], [3, 48], [3, 49], [2, 49], [2, 48]]],
};

describe("fetchCommuneContour", () => {
  beforeEach(() => {
    vi.mocked(fetchExternalJson).mockReset();
  });

  it("asks geo.api.gouv.fr for the commune's outline only", async () => {
    vi.mocked(fetchExternalJson).mockResolvedValueOnce({ contour: square });

    await fetchCommuneContour("75056");

    expect(fetchExternalJson).toHaveBeenCalledWith(
      "https://geo.api.gouv.fr/communes/75056?fields=contour&format=json&geometry=contour",
    );
  });

  it("returns the outline the API answers with", async () => {
    vi.mocked(fetchExternalJson).mockResolvedValueOnce({ contour: square });

    await expect(fetchCommuneContour("75056")).resolves.toBe(square);
  });

  it("returns null when the commune has no outline", async () => {
    vi.mocked(fetchExternalJson).mockResolvedValueOnce({});

    await expect(fetchCommuneContour("75056")).resolves.toBeNull();
  });

  it("returns null when the API answers an explicit null outline", async () => {
    vi.mocked(fetchExternalJson).mockResolvedValueOnce({ contour: null });

    await expect(fetchCommuneContour("75056")).resolves.toBeNull();
  });

  it("encodes a code that would otherwise break the URL", async () => {
    vi.mocked(fetchExternalJson).mockResolvedValueOnce({ contour: null });

    await fetchCommuneContour("12/3?x=1#y");

    expect(fetchExternalJson).toHaveBeenCalledWith(
      "https://geo.api.gouv.fr/communes/12%2F3%3Fx%3D1%23y?fields=contour&format=json&geometry=contour",
    );
  });

  it("keeps a Corsican alphanumeric code as is", async () => {
    vi.mocked(fetchExternalJson).mockResolvedValueOnce({ contour: null });

    await fetchCommuneContour("2A004");

    expect(vi.mocked(fetchExternalJson).mock.calls[0][0]).toContain("/communes/2A004?");
  });

  it("passes an empty code through rather than guessing one", async () => {
    vi.mocked(fetchExternalJson).mockResolvedValueOnce({ contour: null });

    await fetchCommuneContour("");

    expect(vi.mocked(fetchExternalJson).mock.calls[0][0]).toContain("/communes/?fields=contour");
  });

  it("lets the API's error through (unknown commune)", async () => {
    vi.mocked(fetchExternalJson).mockRejectedValueOnce(new ApiError(404, "404 Not Found"));

    await expect(fetchCommuneContour("00000")).rejects.toMatchObject({ status: 404 });
  });
});
