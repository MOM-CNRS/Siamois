import { describe, expect, it, vi } from "vitest";
import { apiFetch } from "../api/client";
import { getOrganizationCounts } from "./organizationCounts";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));

const mockedApiFetch = vi.mocked(apiFetch);

describe("getOrganizationCounts", () => {
  it("calls the organization counts endpoint and unwraps the data envelope", async () => {
    const counts = { projects: 1, places: 2, recordingUnits: 3, finds: 4, phases: 5, containers: 6 };
    mockedApiFetch.mockResolvedValueOnce({ data: counts });

    await expect(getOrganizationCounts(7)).resolves.toEqual(counts);
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/counts");
  });
});
