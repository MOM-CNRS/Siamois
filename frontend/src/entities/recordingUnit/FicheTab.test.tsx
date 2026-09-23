import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import { RecordingUnitFicheTab } from "./FicheTab";
import { getRecordingUnitEffectiveForm } from "./recordingUnitTypes";
import { patchRecordingUnitAnswers } from "./api";
import type { RecordingUnitDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./recordingUnitTypes", () => ({ getRecordingUnitEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ patchRecordingUnitAnswers: vi.fn() }));

const mockedGetEffectiveForm = vi.mocked(getRecordingUnitEffectiveForm);
const mockedPatch = vi.mocked(patchRecordingUnitAnswers);

registerDefaultFieldRenderers();

// Mirrors RecordingUnitDetailsForm.generalPanel() closely enough to exercise FicheTab's own
// branches: an editable TEXT field (comments), the two hidden system columns
// (ACTION_UNIT_FIELD/FULL_IDENTIFIER_FIELD — displayed via the panel header instead, not this
// grid), and a required field (nature).
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
          { width: STANDARD, hidden: true, isRequired: false, isReadOnly: true, fieldId: -301 },
          { width: STANDARD, hidden: true, isRequired: true, isReadOnly: true, fieldId: -303 },
          { width: STANDARD, isRequired: true, isReadOnly: false, fieldId: -305 },
        ],
      },
      {
        columns: [{ width: FULL, isRequired: false, isReadOnly: false, fieldId: 200 }],
      },
    ],
  },
  {
    className: null,
    name: "recordingunit.panel.chronology",
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
  "-301": field({ id: "-301", label: "Unité", valueBinding: "actionUnit" }),
  "-303": field({ id: "-303", label: "Identifiant complet", valueBinding: "fullIdentifier" }),
  "-305": field({ id: "-305", label: "Nature", answerType: "SELECT_ONE_FROM_FIELD_CODE", fieldCode: "SIAUE.NATURE" }),
  "200": field({ id: "200", label: "Commentaires", isSystemField: false }),
};

function ru(overrides: Partial<RecordingUnitDetail> = {}): RecordingUnitDetail {
  return {
    resourceType: "recording-units",
    id: "42",
    fullIdentifier: "OA-UE-42",
    projectId: "5",
    organization: { resourceType: "organizations", id: "7" },
    answers: { "200": { answerType: "TEXT", value: "Une observation" } },
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

// Every field is a plain value until clicked — FieldEditCell then opens CellEditOverlay (unchanged
// from the one the list uses) on top of it, portalled to document.body.
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

function render(entity: RecordingUnitDetail, onSaved = vi.fn(), writeMode = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <RecordingUnitFicheTab entity={entity} onSaved={onSaved} />
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
  mockedPatch.mockResolvedValue(ru());
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

describe("RecordingUnitFicheTab", () => {
  it("resolves the effective form for the RU's own project and type", async () => {
    render(ru({ type: { resourceType: "concepts", id: "9", resolvedLabel: "US" } }));
    await flush();

    expect(mockedGetEffectiveForm).toHaveBeenCalledWith("5", "9");
  });

  it("renders both panels with their labels, and skips hidden columns", async () => {
    render(ru());
    await flush();

    expect(container.textContent).toContain("Général");
    expect(container.textContent).toContain("Chronologie");
    // Hidden system columns never render as grid fields...
    expect(container.textContent).not.toContain("Unité");
    expect(container.textContent).not.toContain("Identifiant complet");
    // ...but a real field does, with its stored value, as a plain value until clicked.
    expect(container.textContent).toContain("Commentaires");
    expect(commentsCell().textContent).toBe("Une observation");
    expect(overlayInput()).toBeNull();
  });

  it("persists an edited field through patchRecordingUnitAnswers, keyed by the field id", async () => {
    render(ru());
    await flush();

    await openComments();
    const input = overlayInput()!;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Nouvelle observation");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    // CellEditOverlay commits a text field when focus leaves it — a mousedown outside its own box,
    // not React's bubbling blur/focusout.
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(mockedPatch).toHaveBeenCalledWith("42", { "200": { value: "Nouvelle observation" } });
  });

  it("offers no click-to-edit affordance outside write mode", async () => {
    render(ru(), vi.fn(), false);
    await flush();

    expect(commentsCell().classList.contains("field-value-cell-editable")).toBe(false);
    await act(async () => {
      commentsCell().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(overlayInput()).toBeNull();
  });

  it("shows a warning instead of loading a form when the RU has no project", async () => {
    render(ru({ projectId: null }));
    await flush();

    expect(container.textContent).toContain("Projet inconnu");
    expect(mockedGetEffectiveForm).not.toHaveBeenCalled();
  });
});
