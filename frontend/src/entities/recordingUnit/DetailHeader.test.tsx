import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClientProvider, QueryClient } from "@tanstack/react-query";
import { patchRecordingUnit } from "./api";
import { RecordingUnitDetailHeader } from "./DetailHeader";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import type { RecordingUnitDetail } from "./types";


vi.mock("./api", () => ({ patchRecordingUnit: vi.fn() }));
vi.mock("./recordingUnitTypes", () => ({ getRecordingUnitTypes: vi.fn() }));
const mockedPatch = vi.mocked(patchRecordingUnit);
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
  it("shows the fullIdentifier with ONE pencil for the whole header", async () => {
    renderHeader(ru());
    await flush();

    expect(container.textContent).toContain("OA-UE-42");
    expect(container.querySelectorAll(".pi-pencil")).toHaveLength(1);
  });

  it("saves a new identifier through the flat `identifier` of the patch", async () => {
    mockedPatch.mockResolvedValue({} as never);
    const onSaved = renderHeader(ru());
    await flush();

    const pencil = () => container.querySelector(".entity-detail-header-edit") as HTMLElement;
    await act(async () => {
      pencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    const input = container.querySelector(".entity-detail-header-primary-input") as HTMLInputElement;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      setter.call(input, "OA-UE-43");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      pencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedPatch).toHaveBeenCalledWith("42", { identifier: "OA-UE-43" });
    expect(onSaved).toHaveBeenCalled();
  });

  it("shows the type as a chip with a pencil once the type catalog has loaded", async () => {
    renderHeader(ru());
    await flush();

    expect(container.querySelector(".entity-detail-header-category")?.textContent).toContain("US");
    expect(container.querySelector(".pi-pencil")).toBeTruthy();
  });

  it("labels an untyped recording unit rather than rendering an empty chip", async () => {
    renderHeader(ru({ type: undefined }));
    await flush();

    expect(container.querySelector(".entity-detail-header-category")?.textContent).toContain("Sans type");
  });

  it("keeps the type as a chip when the catalog has no type field to edit with", async () => {
    mockedGetTypes.mockResolvedValue({ tableColumns: [], fields: {} });
    renderHeader(ru());
    await flush();
    await act(async () => {
      (container.querySelector(".entity-detail-header-edit") as HTMLElement).dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelector(".entity-detail-header-category")?.textContent).toContain("US");
    expect(container.querySelector(".entity-detail-header-category .p-autocomplete")).toBeFalsy();
  });

  it("swaps the chip for the shared concept autocomplete when the pencil is pressed", async () => {
    renderHeader(ru());
    await flush();

    const pencil = container.querySelector(".pi-pencil")!.closest("button") as HTMLElement;
    await act(async () => {
      pencil.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const input = container.querySelector(".entity-detail-header-category .p-autocomplete input") as HTMLInputElement;
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
