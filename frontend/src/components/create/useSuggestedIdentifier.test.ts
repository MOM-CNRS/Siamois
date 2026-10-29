import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../../api/client";
import {suggestIdentifier} from "./useSuggestedIdentifier";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockReset();
});

describe("suggestIdentifier", () => {
  it("asks the one endpoint for an entity created in a project, named by its collection", async () => {
    mockedApiFetch.mockResolvedValueOnce({ suggestedIdentifier: "PROJ-PH1" });

    const result = await suggestIdentifier("phase", "5", 42);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/identifier-suggestions?entityType=phases&projectId=5&typeId=42");
    expect(result).toBe("PROJ-PH1");
  });

  it("asks for a find on its recording unit", async () => {
    mockedApiFetch.mockResolvedValueOnce({ suggestedIdentifier: "UE1-M1" });

    await suggestIdentifier("find", 187, "3");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/identifier-suggestions?entityType=finds&recordingUnitId=187&typeId=3");
  });

  it("asks for a recording unit under its parent", async () => {
    mockedApiFetch.mockResolvedValueOnce({ suggestedIdentifier: "UE4-1" });

    await suggestIdentifier("recordingUnit", "5", "42", 187);

    expect(mockedApiFetch).toHaveBeenCalledWith(
      "/api/v1/identifier-suggestions?entityType=recording-units&projectId=5&typeId=42&parentRecordingUnitId=187",
    );
  });

  it("gives null when the project configures no format", async () => {
    mockedApiFetch.mockResolvedValueOnce({ suggestedIdentifier: null });

    expect(await suggestIdentifier("document", "5", "9")).toBeNull();
  });
});
