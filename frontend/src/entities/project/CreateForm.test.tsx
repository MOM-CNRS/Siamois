import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import type { FieldRendererProps } from "../../fields/registry";
import { ProjectCreateForm } from "./CreateForm";
import { getProjectTypes } from "./projectTypes";
import { createProject } from "./api";


vi.mock("./projectTypes", () => ({ getProjectTypes: vi.fn() }));
vi.mock("./api", () => ({ createProject: vi.fn() }));

// The concept autocomplete (AutoComplete + its async option source) is exercised by
// fields/renderers.test.tsx already — mocked here to a plain, directly-clickable stand-in so this
// file stays a test of CreateForm's own validation/submit/error logic, not of AutoComplete.
vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ onChange }: FieldRendererProps) => (
    <button type="button" data-testid="pick-type" onClick={() => onChange({ resourceId: "9", resourceType: "concepts", label: "Sondage" })}>
      Choisir un type
    </button>
  ),
  SelectOneSpatialUnitRenderer: ({ onChange, placeContext }: FieldRendererProps) => (
    <button type="button" data-testid="pick-main-location" data-place-context={JSON.stringify(placeContext ?? null)} onClick={() => onChange({ resourceId: "31", resourceType: "places", label: "Lyon" })}>
      Choisir le lieu principal
    </button>
  ),
  SelectManyRefRenderer: ({ onChange, placeContext }: FieldRendererProps) => (
    <button
      type="button"
      data-testid="pick-spatial-context"
      data-place-context={JSON.stringify(placeContext ?? null)}
      onClick={() =>
        onChange([
          { resourceId: "32", resourceType: "places", label: "Rhône" },
          { resourceId: "33", resourceType: "places", label: "Ain" },
        ])
      }
    >
      Choisir le contexte
    </button>
  ),
}));

const mockedGetProjectTypes = vi.mocked(getProjectTypes);
const mockedCreateProject = vi.mocked(createProject);

const typeField: FieldResource = {
  id: "-101",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "type",
  fieldCode: "SIAACTIONUNIT.TYPE",
};

let container: HTMLDivElement;
let root: Root;

function render(onCreated = vi.fn(), onCancel = vi.fn(), organizationId: number | undefined = 7) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <ProjectCreateForm organizationId={organizationId} onCreated={onCreated} onCancel={onCancel} />
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

function setInputValue(input: HTMLInputElement, value: string) {
  const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
  nativeSetter.call(input, value);
  input.dispatchEvent(new Event("input", { bubbles: true }));
}

beforeEach(() => {
  mockedGetProjectTypes.mockReset();
  mockedGetProjectTypes.mockResolvedValue({
    layoutJson: "[]",
    fieldConfigs: [],
    tableColumns: [],
    fields: { "-101": typeField },
  });
  mockedCreateProject.mockReset();
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

describe("ProjectCreateForm", () => {
  it("disables submit until name, identifier and type are all filled", async () => {
    render();
    await flush();

    expect(submitButton().disabled).toBe(true);

    const inputs = Array.from(container.querySelectorAll("input")) as HTMLInputElement[];
    await act(async () => setInputValue(inputs[0], "Fouille 2026"));
    expect(submitButton().disabled).toBe(true);

    await act(async () => setInputValue(inputs[1], "F2026"));
    expect(submitButton().disabled).toBe(true);

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    expect(submitButton().disabled).toBe(false);
  });

  it("submits the trio and hands the created project's id to onCreated", async () => {
    mockedCreateProject.mockResolvedValue({ resourceType: "projects", id: "55", name: "Fouille 2026" } as never);
    const { onCreated } = render();
    await flush();

    const inputs = Array.from(container.querySelectorAll("input")) as HTMLInputElement[];
    await act(async () => setInputValue(inputs[0], "Fouille 2026"));
    await act(async () => setInputValue(inputs[1], "F2026"));
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateProject).toHaveBeenCalledWith({
      organizationId: "7",
      name: "Fouille 2026",
      identifier: "F2026",
      typeId: "9",
    });
    expect(onCreated).toHaveBeenCalledWith("55");
  });

  it("offers the place fields of the catalog and submits the picked places", async () => {
    mockedGetProjectTypes.mockResolvedValue({
      layoutJson: "[]",
      fieldConfigs: [],
      tableColumns: [],
      fields: {
        "-101": typeField,
        "-108": { id: "-108", resourceType: "fields", label: "Localisation principale", answerType: "SELECT_ONE_SPATIAL_UNIT", isSystemField: true, valueBinding: "mainLocation", rules: { placeSources: [{ source: "INSEE" }] } },
        "-104": { id: "-104", resourceType: "fields", label: "Contexte spatial", answerType: "SELECT_MULTIPLE_SPATIAL_UNIT_TREE", isSystemField: true, valueBinding: "spatialContext", rules: { placeSources: [{ source: "GEOPLAT", params: { citycode: { fromField: -108, attribute: "CODE" } } }] } },
      },
    });
    mockedCreateProject.mockResolvedValue({ resourceType: "projects", id: "56", name: "Fouille" } as never);
    render();
    await flush();

    expect(container.textContent).toContain("Localisation principale");
    expect(container.textContent).toContain("Contexte spatial");
    const inputs = Array.from(container.querySelectorAll("input")) as HTMLInputElement[];
    await act(async () => setInputValue(inputs[0], "Fouille"));
    await act(async () => setInputValue(inputs[1], "F1"));
    const contextOf = (testId: string) =>
      JSON.parse(container.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!.dataset.placeContext as string);
    // Nothing picked yet: the precise places wait for the commune.
    expect(contextOf("pick-main-location")).toEqual({ deps: {} });
    expect(contextOf("pick-spatial-context")).toEqual({ deps: { "-108": null } });
    for (const id of ["pick-type", "pick-main-location", "pick-spatial-context"]) {
      await act(async () => {
        container.querySelector<HTMLButtonElement>(`[data-testid="${id}"]`)!.click();
      });
    }
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    // Once the commune is picked, the precise places search inside it.
    expect(contextOf("pick-spatial-context")).toEqual({ deps: { "-108": "31" } });
    expect(mockedCreateProject).toHaveBeenCalledWith({
      organizationId: "7",
      name: "Fouille",
      identifier: "F1",
      typeId: "9",
      mainLocationId: "31",
      spatialContextSpatialUnitIds: ["32", "33"],
    });
  });

  it("shows the server's error message instead of calling onCreated on failure", async () => {
    const { ApiError } = await import("../../api/client");
    mockedCreateProject.mockRejectedValue(new ApiError(409, "Identifiant déjà utilisé"));
    const { onCreated } = render();
    await flush();

    const inputs = Array.from(container.querySelectorAll("input")) as HTMLInputElement[];
    await act(async () => setInputValue(inputs[0], "Fouille 2026"));
    await act(async () => setInputValue(inputs[1], "F2026"));
    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(container.textContent).toContain("Identifiant déjà utilisé");
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("calls onCancel when the cancel button is clicked", async () => {
    const { onCancel } = render();
    await flush();

    const cancelButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Annuler")!;
    await act(async () => cancelButton.click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
