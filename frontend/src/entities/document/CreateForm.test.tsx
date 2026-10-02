import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import type { FieldRendererProps } from "../../fields/registry";
import { DocumentCreateForm } from "./CreateForm";
import { getEffectiveForm } from "../typeCatalog";
import { createDocument } from "./api";


vi.mock("../typeCatalog", () => ({ getEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ createDocument: vi.fn() }));
// The project picker (useCreateProject) searches on focus when the list has no project of its own.
vi.mock("../creatableProjects", () => ({ searchCreatableProjects: vi.fn().mockResolvedValue({ data: [] }) }));

vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ onChange }: FieldRendererProps) => (
    <button type="button" data-testid="pick-type" onClick={() => onChange({ resourceId: "9", resourceType: "concepts", label: "Comblement" })}>
      Choisir un type
    </button>
  ),
}));

const mockedGetDocumentEffectiveForm = vi.mocked(getEffectiveForm);
const mockedCreateDocument = vi.mocked(createDocument);

const typeField: FieldResource = {
  id: "-704",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "category",
  fieldCode: "SIAD.CATEGORY",
};

let container: HTMLDivElement;
let root: Root;

function render(onCreated = vi.fn(), onCancel = vi.fn(), scope: { entityType: string; id: string | number } | null = { entityType: "project", id: 5 }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <DocumentCreateForm organizationId={7} scope={scope ?? undefined} onCreated={onCreated} onCancel={onCancel} />
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
  mockedGetDocumentEffectiveForm.mockReset();
  mockedGetDocumentEffectiveForm.mockResolvedValue({ layoutJson: "[]", fields: { "-704": typeField } });
  mockedCreateDocument.mockReset();
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

describe("DocumentCreateForm", () => {
  it("resolves the project's own document categories catalog from scope.id", async () => {
    render();
    await flush();

    expect(mockedGetDocumentEffectiveForm).toHaveBeenCalledWith("document-types", "5", null);
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

  it("submits projectId (from scope) and the picked categoryId, and hands the created document's id to onCreated", async () => {
    mockedCreateDocument.mockResolvedValue({ resourceType: "documents", id: "77", identifier: "DOC1" } as never);
    const { onCreated } = render();
    await flush();

    await act(async () => {
      container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click();
    });
    await act(async () => {
      submitButton().dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });
    await flush();

    expect(mockedCreateDocument).toHaveBeenCalledWith({ projectId: "5", categoryId: "9" });
    expect(onCreated).toHaveBeenCalledWith("77");
  });

  it("asks for the project first (no type catalog yet) when the list has no project of its own", async () => {
    render(vi.fn(), vi.fn(), null);
    await flush();

    expect(container.textContent).toContain("Projet");
    expect(container.textContent).toContain("Choisissez d'abord un projet");
    expect(container.textContent).not.toContain("Projet inconnu");
    expect(mockedGetDocumentEffectiveForm).not.toHaveBeenCalled();
  });

  it("calls onCancel when the cancel button is clicked", async () => {
    const { onCancel } = render();
    await flush();

    const cancelButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Annuler")!;
    await act(async () => cancelButton.click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
