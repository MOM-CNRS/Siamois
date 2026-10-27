import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import type { FieldRendererProps } from "../../fields/registry";
import { RecordingUnitCreateForm } from "./CreateForm";
import { fetchTypesCatalog, getEffectiveForm } from "../shared/typeCatalog";
import { createRecordingUnit } from "./api";
import { apiFetch } from "../../api/client";


vi.mock("../shared/typeCatalog", () => ({ getEffectiveForm: vi.fn(), fetchTypesCatalog: vi.fn() }));
vi.mock("./api", () => ({ createRecordingUnit: vi.fn() }));
// The identifier suggestion (components/create/useSuggestedIdentifier) is the only apiFetch left unmocked.
vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
vi.mock("../shared/creatableProjects", () => ({ searchCreatableProjects: vi.fn() }));

// The project picker (useCreateProject) — PrimeReact's AutoComplete reduced to a button picking
// project 8, same stand-in as FindCreateForm's own recording-unit picker test.
vi.mock("primereact/autocomplete", async () => ({
  // forwardRef: CreateFormField hands the picker a ref, like the real AutoComplete takes.
  AutoComplete: (await import("react")).forwardRef(({ onChange }: { onChange: (e: { value: unknown }) => void }, _ref) => (
    <button
      type="button"
      data-testid="pick-project"
      onClick={() => onChange({ value: { resourceType: "projects", id: "8", fullIdentifier: "INST-P8", name: "Fouille" } })}
    >
      Choisir un projet
    </button>
  )),
}));

// Same reduction as entities/project/CreateForm.test.tsx: the concept autocomplete is exercised
// elsewhere, mocked here to a plain clickable stand-in so this stays a test of the form's own
// validation/submit/error logic. The picked type is mutable (vi.hoisted) so a test can re-pick
// another one and see the identifier suggestion follow.
const mocks = vi.hoisted(() => ({
  typePick: { resourceId: "9", resourceType: "concepts", label: "US" },
}));

vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ onChange }: FieldRendererProps) => (
    <button type="button" data-testid="pick-type" onClick={() => onChange(mocks.typePick)}>
      Choisir un type
    </button>
  ),
}));

const mockedGetRecordingUnitTypes = vi.mocked(getEffectiveForm);
const mockedCreateRecordingUnit = vi.mocked(createRecordingUnit);
// What GET /api/v1/identifier-suggestions answers, called with the request's project and type.
const mockedSuggestIdentifier = vi.fn<(projectId: string | null, typeId: string | null) => Promise<string>>();

const typeField: FieldResource = {
  id: "-101",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE",
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

// The identifier field is the form's only real input (the project and type pickers are mocked to
// buttons), targeted by its placeholder rather than by position.
function identifierInput(): HTMLInputElement {
  return container.querySelector<HTMLInputElement>('input[placeholder="Automatique"]')!;
}

function setInputValue(input: HTMLInputElement, value: string) {
  const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
  nativeSetter.call(input, value);
  input.dispatchEvent(new Event("input", { bubbles: true }));
}

beforeEach(() => {
  mockedGetRecordingUnitTypes.mockReset();
  mockedGetRecordingUnitTypes.mockResolvedValue({ layoutJson: "[]", fields: { "-101": typeField } });
  mockedCreateRecordingUnit.mockReset();
  mockedSuggestIdentifier.mockReset();
  mockedSuggestIdentifier.mockResolvedValue("INST-PROJ-UE1");
  vi.mocked(apiFetch).mockReset();
  vi.mocked(apiFetch).mockImplementation(async (path: string) => {
    const url = new URL(path, "http://localhost");
    expect(url.pathname).toBe("/api/v1/identifier-suggestions");
    expect(url.searchParams.get("entityType")).toBe("recording-units");
    return { suggestedIdentifier: await mockedSuggestIdentifier(url.searchParams.get("projectId"), url.searchParams.get("typeId")) };
  });
  mocks.typePick = { resourceId: "9", resourceType: "concepts", label: "US" };
  vi.mocked(fetchTypesCatalog).mockReset();
  vi.mocked(fetchTypesCatalog).mockRejectedValue(new Error("not needed here"));
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

    expect(mockedGetRecordingUnitTypes).toHaveBeenCalledWith("recording-unit-types", "5", null);
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

  it("prefills the identifier the server would generate, and sends none when it is left untouched", async () => {
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "77", fullIdentifier: "INST-PROJ-UE1" } as never);
    render();
    await flush();

    // No suggestion before a type is picked: it depends on the project AND the type.
    expect(mockedSuggestIdentifier).not.toHaveBeenCalled();
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await flush();

    expect(mockedSuggestIdentifier).toHaveBeenCalledWith("5", "9");
    expect(identifierInput().value).toBe("INST-PROJ-UE1");

    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    // Untouched: the server allocates the identifier itself.
    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith({ projectId: "5", typeId: "9" });
  });

  it("sends the identifier the user typed instead of the suggestion", async () => {
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "80", fullIdentifier: "INST-PROJ-UE42" } as never);
    render();
    await flush();
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await flush();

    await act(async () => {
      setInputValue(identifierInput(), "INST-PROJ-UE42");
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith(expect.objectContaining({ projectId: "5", typeId: "9", identifier: "INST-PROJ-UE42" }));
  });

  it("treats an emptied identifier as automatic: none is sent", async () => {
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "81", fullIdentifier: "INST-PROJ-UE1" } as never);
    render();
    await flush();
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await flush();

    await act(async () => {
      setInputValue(identifierInput(), "");
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith({ projectId: "5", typeId: "9" });
  });

  it("asks for a new suggestion and drops the edit when the type changes", async () => {
    mockedSuggestIdentifier.mockReset();
    mockedSuggestIdentifier.mockResolvedValueOnce("INST-PROJ-UE1").mockResolvedValueOnce("INST-PROJ-UE2");
    render();
    await flush();
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await flush();
    await act(async () => {
      setInputValue(identifierInput(), "INST-PROJ-UE42");
    });

    mocks.typePick = { resourceId: "10", resourceType: "concepts", label: "Mur" };
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await flush();

    expect(mockedSuggestIdentifier).toHaveBeenLastCalledWith("5", "10");
    expect(identifierInput().value).toBe("INST-PROJ-UE2");
  });

  it("links the new UE to a row action's parent, and shows that parent in the form", async () => {
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "78", fullIdentifier: "INST-PROJ-UE2" } as never);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <RecordingUnitCreateForm
            organizationId={7}
            scope={{ entityType: "project", id: 5 }}
            prefill={{ parent: { id: "12", label: "INST-PROJ-UE12" } }}
            onCreated={vi.fn()}
            onCancel={vi.fn()}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.textContent).toContain("Contenue dans");
    expect(container.textContent).toContain("INST-PROJ-UE12");
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith({ projectId: "5", typeId: "9", parentRecordingUnitId: "12", childRecordingUnitId: undefined });
  });

  it("attaches the new UE to the phase a phase's tab creates it from, and shows that phase in the form", async () => {
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "79", fullIdentifier: "INST-PROJ-UE3" } as never);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <RecordingUnitCreateForm
            organizationId={7}
            scope={{ entityType: "project", id: 5 }}
            prefill={{ phase: { id: "21", label: "Phase par défaut" } }}
            onCreated={vi.fn()}
            onCancel={vi.fn()}
          />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.textContent).toContain("Phase par défaut");
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith({
      projectId: "5",
      typeId: "9",
      parentRecordingUnitId: undefined,
      childRecordingUnitId: undefined,
      phaseId: "21",
    });
  });

  it("creates in the project picked in the form when the list has no project of its own", async () => {
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "79", fullIdentifier: "INST-P8-UE1" } as never);
    const { onCreated } = render(vi.fn(), vi.fn(), null);
    await flush();

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-project"]')!.click();
    });
    await flush();
    expect(mockedGetRecordingUnitTypes).toHaveBeenCalledWith("recording-unit-types", "8", null);

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith(expect.objectContaining({ projectId: "8", typeId: "9" }));
    expect(onCreated).toHaveBeenCalledWith("79");
  });

  it("asks for the project first (no type catalog yet) when the list has no project of its own", async () => {
    render(vi.fn(), vi.fn(), null);
    await flush();

    expect(container.textContent).toContain("Projet");
    expect(container.textContent).toContain("Choisissez d'abord un projet");
    expect(container.textContent).not.toContain("Projet inconnu");
    expect(mockedGetRecordingUnitTypes).not.toHaveBeenCalled();
  });

  it("calls onCancel when the cancel button is clicked", async () => {
    const { onCancel } = render();
    await flush();

    const cancelButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Annuler")!;
    await act(async () => cancelButton.click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("is created of the type a list tab filtered on one type fixes, still changeable", async () => {
    vi.mocked(fetchTypesCatalog).mockResolvedValue({ data: [{ id: 9, concept: { resolvedLabel: "US" } }, { id: 10, concept: { resolvedLabel: "Mur" } }] } as never);
    mockedCreateRecordingUnit.mockResolvedValue({ resourceType: "recording-units", id: "79", fullIdentifier: "INST-PROJ-UE3" } as never);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <RecordingUnitCreateForm organizationId={7} scope={{ entityType: "project", id: 5 }} prefill={{ typeId: "9" }} onCreated={vi.fn()} onCancel={vi.fn()} />
        </QueryClientProvider>,
      );
    });
    await flush();

    // No type picked by hand: the tab's one is already there.
    expect(submitButton().disabled).toBe(false);
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateRecordingUnit).toHaveBeenCalledWith(expect.objectContaining({ projectId: "5", typeId: "9" }));
  });
});
