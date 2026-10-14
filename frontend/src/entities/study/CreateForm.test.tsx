import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import type { FieldRendererProps } from "../../fields/registry";
import { StudyCreateForm } from "./CreateForm";
import { getEffectiveForm } from "../typeCatalog";
import { createStudy } from "./api";


vi.mock("../typeCatalog", () => ({ getEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ createStudy: vi.fn() }));
// The project picker (useCreateProject) searches on focus when the list has no project of its own.
vi.mock("../creatableProjects", () => ({ searchCreatableProjects: vi.fn().mockResolvedValue({ data: [] }) }));

vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ onChange }: FieldRendererProps) => (
    <button type="button" data-testid="pick-type" onClick={() => onChange({ resourceId: "9", resourceType: "concepts", label: "Traitement" })}>
      Choisir un type
    </button>
  ),
}));

const mockedGetStudyEffectiveForm = vi.mocked(getEffectiveForm);
const mockedCreateStudy = vi.mocked(createStudy);

const typeField: FieldResource = {
  id: "-803",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "type",
  fieldCode: "SIAST.TYPE",
};

let host: HTMLDivElement;
let root: Root;

function render(onCreated = vi.fn(), onCancel = vi.fn(), scope: { entityType: string; id: string | number } | null = { entityType: "project", id: 5 }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <StudyCreateForm organizationId={7} scope={scope ?? undefined} onCreated={onCreated} onCancel={onCancel} />
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
  return Array.from(host.querySelectorAll("button")).find((b) => b.textContent === "Créer") as HTMLButtonElement;
}

beforeEach(() => {
  mockedGetStudyEffectiveForm.mockReset();
  mockedGetStudyEffectiveForm.mockResolvedValue({ layoutJson: "[]", fields: { "-803": typeField } });
  mockedCreateStudy.mockReset();
  host = document.createElement("div");
  document.body.appendChild(host);
  root = createRoot(host);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  host.remove();
});

describe("StudyCreateForm", () => {
  it("resolves the project's own study types catalog from scope.id", async () => {
    render();
    await flush();

    expect(mockedGetStudyEffectiveForm).toHaveBeenCalledWith("study-types", "5", null);
  });

  it("disables submit until a type is picked", async () => {
    render();
    await flush();

    expect(submitButton().disabled).toBe(true);

    await act(async () => {
      host.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    expect(submitButton().disabled).toBe(false);
  });

  it("submits projectId (from scope) and the picked typeId, and hands the created study's id to onCreated", async () => {
    mockedCreateStudy.mockResolvedValue({ resourceType: "studies", id: "77", identifier: "C1" } as never);
    const { onCreated } = render();
    await flush();

    await act(async () => {
      host.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateStudy).toHaveBeenCalledWith({ projectId: "5", typeId: "9" });
    expect(onCreated).toHaveBeenCalledWith("77");
  });

  it("asks for the project first (no type catalog yet) when the list has no project of its own", async () => {
    render(vi.fn(), vi.fn(), null);
    await flush();

    expect(host.textContent).toContain("Projet");
    expect(host.textContent).toContain("Choisissez d'abord un projet");
    expect(host.textContent).not.toContain("Projet inconnu");
    expect(mockedGetStudyEffectiveForm).not.toHaveBeenCalled();
  });

  it("calls onCancel when the cancel button is clicked", async () => {
    const { onCancel } = render();
    await flush();

    const cancelButton = Array.from(host.querySelectorAll("button")).find((b) => b.textContent === "Annuler")!;
    await act(async () => cancelButton.click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
