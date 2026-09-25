import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import type { FieldRendererProps } from "../../fields/registry";
import { PhaseCreateForm } from "./CreateForm";
import { getPhaseEffectiveForm } from "./phaseTypes";
import { createPhase } from "./api";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./phaseTypes", () => ({ getPhaseEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ createPhase: vi.fn() }));
// The project picker (useCreateProject) searches on focus when the list has no project of its own.
vi.mock("../project/api", () => ({ searchCreatableProjects: vi.fn().mockResolvedValue({ data: [] }) }));

vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ onChange }: FieldRendererProps) => (
    <button type="button" data-testid="pick-type" onClick={() => onChange({ resourceId: "9", resourceType: "concepts", label: "Comblement" })}>
      Choisir un type
    </button>
  ),
}));

const mockedGetPhaseEffectiveForm = vi.mocked(getPhaseEffectiveForm);
const mockedCreatePhase = vi.mocked(createPhase);

const typeField: FieldResource = {
  id: "-502",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "type",
  fieldCode: "SIAPHASE.TYPE",
};

let container: HTMLDivElement;
let root: Root;

function render(onCreated = vi.fn(), onCancel = vi.fn(), scope: { entityType: string; id: string | number } | null = { entityType: "project", id: 5 }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <PhaseCreateForm organizationId={7} scope={scope ?? undefined} onCreated={onCreated} onCancel={onCancel} />
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
  mockedGetPhaseEffectiveForm.mockReset();
  mockedGetPhaseEffectiveForm.mockResolvedValue({ layoutJson: "[]", fields: { "-502": typeField } });
  mockedCreatePhase.mockReset();
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

describe("PhaseCreateForm", () => {
  it("resolves the project's own phase types catalog from scope.id", async () => {
    render();
    await flush();

    expect(mockedGetPhaseEffectiveForm).toHaveBeenCalledWith("5", null);
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

  it("submits projectId (from scope) and the picked typeId, and hands the created phase's id to onCreated", async () => {
    mockedCreatePhase.mockResolvedValue({ resourceType: "phases", id: "77", identifier: "PH1" } as never);
    const { onCreated } = render();
    await flush();

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreatePhase).toHaveBeenCalledWith({ projectId: "5", typeId: "9" });
    expect(onCreated).toHaveBeenCalledWith("77");
  });

  it("asks for the project first (no type catalog yet) when the list has no project of its own", async () => {
    render(vi.fn(), vi.fn(), null);
    await flush();

    expect(container.textContent).toContain("Projet");
    expect(container.textContent).toContain("Choisissez d'abord un projet");
    expect(container.textContent).not.toContain("Projet inconnu");
    expect(mockedGetPhaseEffectiveForm).not.toHaveBeenCalled();
  });

  it("calls onCancel when the cancel button is clicked", async () => {
    const { onCancel } = render();
    await flush();

    const cancelButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Annuler")!;
    await act(async () => cancelButton.click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
