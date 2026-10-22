import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { deleteEntity } from "./deleteApi";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("deleteEntity", () => {
  beforeEach(() => {
    mockedApiFetch.mockReset();
  });

  it("sends a DELETE to the entity's collection and resolves to nothing", async () => {
    mockedApiFetch.mockResolvedValue(undefined);

    await expect(deleteEntity("recording-units", 12)).resolves.toBeUndefined();

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/12", { method: "DELETE" });
  });

  it("lets a refusal through", async () => {
    mockedApiFetch.mockImplementation(() => Promise.reject(new Error("409")));

    await expect(deleteEntity("projects", "3")).rejects.toThrow("409");
  });
});
