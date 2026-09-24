import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import { PhaseFicheTab } from "./FicheTab";
import { getPhaseEffectiveForm } from "./phaseTypes";
import { patchPhaseAnswers } from "./api";
import type { PhaseDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./phaseTypes", () => ({ getPhaseEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ patchPhaseAnswers: vi.fn() }));

const mockedGetEffectiveForm = vi.mocked(getPhaseEffectiveForm);
const mockedPatch = vi.mocked(patchPhaseAnswers);

registerDefaultFieldRenderers();

const STANDARD = { span: 12, md: 6, lg: 3 };
const FULL = { span: 12, md: 12, lg: 12 };

const layoutJson = JSON.stringify([
  {
    className: null,
    name: "common.header.general",
    isSystemPanel: true,
    canUserAddFields: null,
    rows: [
      {
        columns: [
          { width: STANDARD, hidden: true, isRequired: false, isReadOnly: true, fieldId: -501 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: -503 },
        ],
      },
      {
        columns: [{ width: FULL, isRequired: false, isReadOnly: false, fieldId: -504 }],
      },
    ],
  },
  {
    className: null,
    name: "common.header.chronologie",
    isSystemPanel: true,
    canUserAddFields: null,
    rows: [],
  },
]);

function field(over: Partial<FieldResource> & { id: string }): FieldResource {
  return {
    resourceType: "fields",
    label: over.id,
    answerType: "TEXT",
    isSystemField: true,
    ...over,
  };
}

const fields: Record<string, FieldResource> = {
  "-501": field({ id: "-501", label: "Identifiant", valueBinding: "identifier" }),
  "-503": field({ id: "-503", label: "Titre", valueBinding: "title" }),
  "-504": field({ id: "-504", label: "Description", valueBinding: "description" }),
};

function phase(overrides: Partial<PhaseDetail> = {}): PhaseDetail {
  return {
    resourceType: "phases",
    id: "42",
    identifier: "OA-PROJ-PH42",
    projectId: "5",
    organization: { resourceType: "organizations", id: "7" },
    answers: { "-504": "Une description" },
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

function descriptionCell(): HTMLElement {
  const group = Array.from(container.querySelectorAll(".field-value-group")).find((el) =>
    el.textContent?.includes("Description"),
  );
  const cell = group?.querySelector(".field-value-cell");
  if (!cell) throw new Error(`No "Description" field-value-cell`);
  return cell as HTMLElement;
}

function openDescription() {
  return act(async () => {
    descriptionCell().dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

function overlayInput(): HTMLInputElement | null {
  return document.body.querySelector(".cell-edit-overlay input") as HTMLInputElement | null;
}

function render(entity: PhaseDetail, onSaved = vi.fn(), writeMode = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <PhaseFicheTab entity={entity} onSaved={onSaved} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

beforeEach(() => {
  mockedGetEffectiveForm.mockReset();
  mockedGetEffectiveForm.mockResolvedValue({ layoutJson, fields });
  mockedPatch.mockReset();
  mockedPatch.mockResolvedValue(phase());
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

describe("PhaseFicheTab", () => {
  it("resolves the effective form for the phase's own project and type", async () => {
    render(phase({ type: { resourceType: "concepts", id: "9", resolvedLabel: "Comblement" } }));
    await flush();

    expect(mockedGetEffectiveForm).toHaveBeenCalledWith("5", "9");
  });

  it("renders both panels with their labels, skips hidden columns, and shows a real field's value", async () => {
    render(phase());
    await flush();

    expect(container.textContent).toContain("Général");
    expect(container.textContent).toContain("Chronologie");
    expect(container.textContent).not.toContain("Identifiant");
    expect(container.textContent).toContain("Description");
    expect(descriptionCell().textContent).toBe("Une description");
    expect(overlayInput()).toBeNull();
  });

  it("persists an edited field through patchPhaseAnswers, keyed by the field id", async () => {
    render(phase());
    await flush();

    await openDescription();
    const input = overlayInput()!;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Nouvelle description");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(mockedPatch).toHaveBeenCalledWith("42", { "-504": { value: "Nouvelle description" } });
  });

  it("offers no click-to-edit affordance outside write mode", async () => {
    render(phase(), vi.fn(), false);
    await flush();

    expect(descriptionCell().classList.contains("field-value-cell-editable")).toBe(false);
    await act(async () => {
      descriptionCell().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(overlayInput()).toBeNull();
  });

  it("shows a warning instead of loading a form when the phase has no project", async () => {
    render(phase({ projectId: null }));
    await flush();

    expect(container.textContent).toContain("Projet inconnu");
    expect(mockedGetEffectiveForm).not.toHaveBeenCalled();
  });
});
