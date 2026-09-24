import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import type { FieldRendererProps } from "../../fields/registry";
import { FindCreateForm } from "./CreateForm";
import { getFindEffectiveForm } from "./findTypes";
import { createFind } from "./api";
import { listRecordingUnits } from "../recordingUnit/api";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./findTypes", () => ({ getFindEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ createFind: vi.fn() }));
vi.mock("../recordingUnit/api", () => ({ listRecordingUnits: vi.fn() }));

// Same reduction as the other CreateForm test files: the concept picker is exercised elsewhere.
vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ onChange }: FieldRendererProps) => (
    <button type="button" data-testid="pick-category" onClick={() => onChange({ resourceId: "12", resourceType: "concepts", label: "Lot" })}>
      Choisir une catégorie
    </button>
  ),
}));

// PrimeReact's AutoComplete (dropdown, keyboard nav, portalled suggestion list) is exercised
// nowhere else in this codebase's own tests either — mocked here to a plain clickable stand-in so
// this file stays a test of CreateForm's own logic (which UE/category actually get submitted),
// not of AutoComplete's internals.
vi.mock("primereact/autocomplete", () => ({
  AutoComplete: ({ onChange }: { onChange: (e: { value: unknown }) => void }) => (
    <button
      type="button"
      data-testid="pick-recording-unit"
      onClick={() => onChange({ value: { resourceType: "recording-units", id: "42", fullIdentifier: "INST-PROJ-UE42" } })}
    >
      Choisir une UE
    </button>
  ),
}));

const mockedGetFindEffectiveForm = vi.mocked(getFindEffectiveForm);
const mockedCreateFind = vi.mocked(createFind);
const mockedListRecordingUnits = vi.mocked(listRecordingUnits);

const categoryField: FieldResource = {
  id: "-201",
  resourceType: "fields",
  label: "Catégorie",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "category",
  fieldCode: "SIAS.CAT",
};

let container: HTMLDivElement;
let root: Root;

function render(onCreated = vi.fn(), onCancel = vi.fn(), scope: { entityType: string; id: string | number } | null = { entityType: "project", id: 5 }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <FindCreateForm organizationId={7} scope={scope ?? undefined} onCreated={onCreated} onCancel={onCancel} />
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
  mockedGetFindEffectiveForm.mockReset();
  mockedGetFindEffectiveForm.mockResolvedValue({ layoutJson: "[]", fields: { "-201": categoryField } });
  mockedCreateFind.mockReset();
  mockedListRecordingUnits.mockReset();
  mockedListRecordingUnits.mockResolvedValue({ data: [], totalCount: 0, limit: 20, offset: 0 });
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

describe("FindCreateForm", () => {
  it("resolves the project's own _default category field (valueBinding 'category', not 'type')", async () => {
    render();
    await flush();

    expect(mockedGetFindEffectiveForm).toHaveBeenCalledWith("5", null);
  });

  it("disables submit until both a recording unit and a category are picked", async () => {
    render();
    await flush();

    expect(submitButton().disabled).toBe(true);

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-recording-unit"]')!.click();
    });
    expect(submitButton().disabled).toBe(true);

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-category"]')!.click();
    });
    expect(submitButton().disabled).toBe(false);
  });

  it("submits the picked recording unit's id and the picked category's id, and hands the created mobilier's id to onCreated", async () => {
    mockedCreateFind.mockResolvedValue({ resourceType: "finds", id: "88", fullIdentifier: "INST-PROJ-M1" } as never);
    const { onCreated } = render();
    await flush();

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-recording-unit"]')!.click();
    });
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-category"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateFind).toHaveBeenCalledWith({ recordingUnitId: "42", typeId: "12" });
    expect(onCreated).toHaveBeenCalledWith("88");
  });

  it("uses a prefilled recording unit instead of the picker (created from that UE)", async () => {
    mockedCreateFind.mockResolvedValue({ resourceType: "finds", id: "89", fullIdentifier: "INST-PROJ-M2" } as never);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <FindCreateForm
            organizationId={7}
            scope={{ entityType: "project", id: 5 }}
            prefill={{ recordingUnit: { id: "31", label: "INST-PROJ-UE31" } }}
            onCreated={vi.fn()}
            onCancel={vi.fn()}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.querySelector('[data-testid="pick-recording-unit"]')).toBeNull();
    expect(container.textContent).toContain("INST-PROJ-UE31");
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-category"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateFind).toHaveBeenCalledWith({ recordingUnitId: "31", typeId: "12" });
  });

  it("asks for the project first (no category catalog yet) when the list has no project of its own", async () => {
    render(vi.fn(), vi.fn(), null);
    await flush();

    expect(container.textContent).toContain("Projet");
    expect(container.textContent).toContain("Choisissez d'abord un projet");
    expect(container.textContent).not.toContain("Projet inconnu");
    expect(mockedGetFindEffectiveForm).not.toHaveBeenCalled();
  });

  it("calls onCancel when the cancel button is clicked", async () => {
    const { onCancel } = render();
    await flush();

    const cancelButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Annuler")!;
    await act(async () => cancelButton.click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
