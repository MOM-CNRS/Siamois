import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClientProvider, QueryClient } from "@tanstack/react-query";
import { patchRecordingUnitAnswers } from "./api";
import { RecordingUnitDetailHeader } from "./DetailHeader";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import type { RecordingUnitDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./api", () => ({ patchRecordingUnitAnswers: vi.fn() }));
vi.mock("./recordingUnitTypes", () => ({ getRecordingUnitTypes: vi.fn() }));
const mockedPatch = vi.mocked(patchRecordingUnitAnswers);
const mockedGetTypes = vi.mocked(getRecordingUnitTypes);

// RecordingUnitForm.RECORDING_UNIT_TYPE_FIELD as the project catalog serves it — the category
// chip finds it by valueBinding "type", never by its id, mirroring Project's own CategoryChip.
const typeField: FieldResource = {
  id: "-302",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "type",
  fieldCode: "SIAUE.TYPE",
};

function ru(overrides: Partial<RecordingUnitDetail> = {}): RecordingUnitDetail {
  return {
    resourceType: "recording-units",
    id: "42",
    fullIdentifier: "OA-UE-42",
    projectId: "5",
    organization: { resourceType: "organizations", id: "7" },
    type: { resourceType: "concepts", id: "9", resolvedLabel: "US" },
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

function renderHeader(entity: RecordingUnitDetail, onSaved = vi.fn(), writeMode = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <RecordingUnitDetailHeader entity={entity} onSaved={onSaved} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

beforeEach(() => {
  mockedPatch.mockReset();
  mockedGetTypes.mockReset();
  mockedGetTypes.mockResolvedValue({ tableColumns: [], fields: { "-302": typeField } });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
});

describe("RecordingUnitDetailHeader", () => {
  it("shows the fullIdentifier as a plain, non-editable chip (no PATCH path exists for it)", async () => {
    renderHeader(ru());
    await flush();

    expect(container.textContent).toContain("OA-UE-42");
    // Exactly one pencil on the whole header — the category chip's. The identifier chip itself
    // has none: unlike Project's, this one is read-only (no PATCH path for fullIdentifier).
    expect(container.querySelectorAll(".pi-pencil")).toHaveLength(1);
    expect(container.querySelector(".recording-unit-detail-header-category .pi-pencil")).toBeTruthy();
  });

  it("shows the type as a chip with a pencil once the type catalog has loaded", async () => {
    renderHeader(ru());
    await flush();

    expect(container.querySelector(".recording-unit-detail-header-category")?.textContent).toContain("US");
    expect(container.querySelector(".pi-pencil")).toBeTruthy();
  });

  it("labels an untyped recording unit rather than rendering an empty chip", async () => {
    renderHeader(ru({ type: undefined }));
    await flush();

    expect(container.querySelector(".recording-unit-detail-header-category")?.textContent).toContain("Sans type");
  });

  it("offers no pencil when the catalog has no type field to edit with", async () => {
    mockedGetTypes.mockResolvedValue({ tableColumns: [], fields: {} });
    renderHeader(ru());
    await flush();

    expect(container.querySelector(".recording-unit-detail-header-category")?.textContent).toContain("US");
    expect(container.querySelector(".pi-pencil")).toBeFalsy();
  });

  it("swaps the chip for the shared concept autocomplete when the pencil is pressed", async () => {
    renderHeader(ru());
    await flush();

    const pencil = container.querySelector(".pi-pencil")!.closest("button") as HTMLElement;
    await act(async () => {
      pencil.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const input = container.querySelector(".recording-unit-detail-header-category .p-autocomplete input") as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(input.value).toBe("US");
  });

  it("offers no pencil at all in read mode", async () => {
    renderHeader(ru(), vi.fn(), false);
    await flush();

    expect(container.querySelector(".pi-pencil")).toBeNull();
    expect(container.textContent).toContain("OA-UE-42");
    expect(container.textContent).toContain("US");
  });

  it("offers no pencil in write mode when the user has no edit right on this unit", async () => {
    renderHeader(ru({ _permissions: { canEdit: false, canDelete: false } }));
    await flush();

    expect(container.querySelector(".pi-pencil")).toBeNull();
  });
});
