import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import { FindFicheTab } from "./FicheTab";
import { getFindEffectiveForm } from "./findTypes";
import { patchFindAnswers } from "./api";
import type { FindDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./findTypes", () => ({ getFindEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ patchFindAnswers: vi.fn() }));

const mockedGetEffectiveForm = vi.mocked(getFindEffectiveForm);
const mockedPatch = vi.mocked(patchFindAnswers);

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
        columns: [{ width: STANDARD, isRequired: true, isReadOnly: false, fieldId: -400 }],
      },
      {
        columns: [{ width: FULL, isRequired: false, isReadOnly: false, fieldId: 300 }],
      },
    ],
  },
  {
    className: null,
    name: "common.header.chronology",
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
  "-400": field({ id: "-400", label: "Catégorie", answerType: "SELECT_ONE_FROM_FIELD_CODE", fieldCode: "SIAMOBILIER.CAT" }),
  "300": field({ id: "300", label: "Commentaires", isSystemField: false }),
};

function find(overrides: Partial<FindDetail> = {}): FindDetail {
  return {
    resourceType: "finds",
    id: "42",
    fullIdentifier: "OA-PROJ-M42",
    projectId: "5",
    organization: { resourceType: "organizations", id: "7" },
    answers: { "300": { answerType: "TEXT", value: "Une observation" } },
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

function commentsCell(): HTMLElement {
  const group = Array.from(container.querySelectorAll(".field-value-group")).find((el) =>
    el.textContent?.includes("Commentaires"),
  );
  const cell = group?.querySelector(".field-value-cell");
  if (!cell) throw new Error(`No "Commentaires" field-value-cell`);
  return cell as HTMLElement;
}

function openComments() {
  return act(async () => {
    commentsCell().dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

function overlayInput(): HTMLInputElement | null {
  return document.body.querySelector(".cell-edit-overlay input") as HTMLInputElement | null;
}

function render(entity: FindDetail, onSaved = vi.fn(), writeMode = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <FindFicheTab entity={entity} onSaved={onSaved} />
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
  mockedPatch.mockResolvedValue(find());
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

describe("FindFicheTab", () => {
  it("resolves the effective form for the mobilier's own project and type", async () => {
    render(find({ type: { resourceType: "concepts", id: "9", resolvedLabel: "Céramique" } }));
    await flush();

    expect(mockedGetEffectiveForm).toHaveBeenCalledWith("5", "9");
  });

  it("renders both panels with their labels, and a real field with its stored value", async () => {
    render(find());
    await flush();

    expect(container.textContent).toContain("Général");
    expect(container.textContent).toContain("Chronologie");
    expect(container.textContent).toContain("Commentaires");
    expect(commentsCell().textContent).toBe("Une observation");
    expect(overlayInput()).toBeNull();
  });

  it("persists an edited field through patchFindAnswers, keyed by the field id", async () => {
    render(find());
    await flush();

    await openComments();
    const input = overlayInput()!;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Nouvelle observation");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(mockedPatch).toHaveBeenCalledWith("42", { "300": { value: "Nouvelle observation" } });
  });

  it("offers no click-to-edit affordance outside write mode", async () => {
    render(find(), vi.fn(), false);
    await flush();

    expect(commentsCell().classList.contains("field-value-cell-editable")).toBe(false);
    await act(async () => {
      commentsCell().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(overlayInput()).toBeNull();
  });

  it("shows a warning instead of loading a form when the mobilier has no project", async () => {
    render(find({ projectId: null }));
    await flush();

    expect(container.textContent).toContain("Projet inconnu");
    expect(mockedGetEffectiveForm).not.toHaveBeenCalled();
  });
});
