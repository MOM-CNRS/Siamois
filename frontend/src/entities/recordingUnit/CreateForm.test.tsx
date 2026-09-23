import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import type { FieldRendererProps } from "../../fields/registry";
import { RecordingUnitCreateForm } from "./CreateForm";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import { createRecordingUnit } from "./api";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./recordingUnitTypes", () => ({ getRecordingUnitTypes: vi.fn() }));
vi.mock("./api", () => ({ createRecordingUnit: vi.fn() }));

// Same reduction as entities/project/CreateForm.test.tsx: the concept autocomplete is exercised
// elsewhere, mocked here to a plain clickable stand-in so this stays a test of the form's own
// validation/submit/error logic.
vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ onChange }: FieldRendererProps) => (
    <button type="button" data-testid="pick-type" onClick={() => onChange({ resourceId: "9", resourceType: "concepts", label: "US" })}>
      Choisir un type
    </button>
  ),
}));

const mockedGetRecordingUnitTypes = vi.mocked(getRecordingUnitTypes);
const mockedCreateRecordingUnit = vi.mocked(createRecordingUnit);

const typeField: FieldResource = {
  id: "-101",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "type",
  fieldCode: "SIAUE.NATURE",
};

let container: HTMLDivElement;
let root: Root;

function render(onCreated = vi.fn(), onCancel = vi.fn(), scope: { entityType: string; id: string | number } | null = { entityType: "project", id: 5 }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <RecordingUnitCreateForm organizationId={7} scope={scope ?? undefined} onCreated={onCreated} onCancel={onCancel} />
      </QueryClientProvider>,
    );
  });
  return { onCreated, onCancel };
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

function submitButton(): HTMLButtonElement {
  return Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Créer") as HTMLButtonElement;
}

beforeEach(() => {
  mockedGetRecordingUnitTypes.mockReset();
  mockedGetRecordingUnitTypes.mockResolvedValue({ tableColumns: [], fields: { "-101": typeField } });
  mockedCreateRecordingUnit.mockReset();
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

describe("RecordingUnitCreateForm", () => {
  it("resolves the project's own recording-unit types catalog from scope.id", async () => {
    render();
    await flush();

    expect(mockedGetRecordingUnitTypes).toHaveBeenCalledWith("5");
  });

  it("disables submit until a type is picked", async () => {
    render();
    await flush();

    expect(submitButton().disabled).toBe(true);

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    expect(submitButton().disabled).toBe(false);
  });

  it("submits projectId (from scope) and the picked typeId, and hands the created UE's id to onCreated", async () => {
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "77", fullIdentifier: "INST-PROJ-UE1" } as never);
    const { onCreated } = render();
    await flush();

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith({ projectId: "5", typeId: "9" });
    expect(onCreated).toHaveBeenCalledWith("77");
  });

  it("shows a warning instead of loading the type catalog when there is no project scope", async () => {
    render(vi.fn(), vi.fn(), null);
    await flush();

    expect(container.textContent).toContain("Projet inconnu");
    expect(mockedGetRecordingUnitTypes).not.toHaveBeenCalled();
  });

  it("calls onCancel when the cancel button is clicked", async () => {
    const { onCancel } = render();
    await flush();

    const cancelButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Annuler")!;
    await act(async () => cancelButton.click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
