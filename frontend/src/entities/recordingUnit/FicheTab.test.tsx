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
// (ACTION_UNIT_FIELD/FULL_IDENTIFIER_FIELD, "d-none" — displayed via the panel header instead,
// not this grid), and a required field (nature).
const layoutJson = JSON.stringify([
  {
    className: null,
    name: "common.header.general",
    isSystemPanel: true,
    canUserAddFields: null,
    rows: [
      {
        columns: [
          { className: "d-none", isRequired: false, isReadOnly: true, fieldId: -301 },
          { className: "d-none", isRequired: true, isReadOnly: true, fieldId: -303 },
          { className: "col-nature", isRequired: true, isReadOnly: false, fieldId: -305 },
        ],
      },
      {
        columns: [{ className: "col-comments", isRequired: false, isReadOnly: false, fieldId: 200 }],
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

// The "nature" field (SELECT_ONE_FROM_FIELD_CODE, listed before "comments" in the layout) is an
// autocomplete that ALSO renders a plain .p-inputtext internally, so a bare
// `container.querySelector(".p-inputtext")` would grab nature's input instead of the field this
// test actually cares about — scope to the field-value-group carrying the "Commentaires" label.
function commentsInput(): HTMLInputElement | null {
  const group = Array.from(container.querySelectorAll(".field-value-group")).find((el) =>
    el.textContent?.includes("Commentaires"),
  );
  return group?.querySelector(".p-inputtext") as HTMLInputElement | null;
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

  it("renders both panels with their labels, and skips d-none columns", async () => {
    render(ru());
    await flush();

    expect(container.textContent).toContain("Général");
    expect(container.textContent).toContain("Chronologie");
    // Hidden system columns never render as grid fields...
    expect(container.textContent).not.toContain("Unité");
    expect(container.textContent).not.toContain("Identifiant complet");
    // ...but a real field does, with its stored value.
    expect(container.textContent).toContain("Commentaires");
    expect(commentsInput()?.value).toBe("Une observation");
  });

  it("persists an edited field through patchRecordingUnitAnswers, keyed by the field id", async () => {
    render(ru());
    await flush();

    const input = commentsInput()!;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Nouvelle observation");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    // React implements onBlur on the bubbling `focusout`, not on the non-bubbling `blur` — only
    // the former reaches the field-value-group wrapper's own handler.
    await act(async () => {
      input.dispatchEvent(new FocusEvent("focusout", { bubbles: true }));
    });
    await flush();

    expect(mockedPatch).toHaveBeenCalledWith("42", { "200": { value: "Nouvelle observation" } });
  });

  it("renders fields read-only outside write mode", async () => {
    render(ru(), vi.fn(), false);
    await flush();

    expect(commentsInput()?.disabled).toBe(true);
  });

  it("shows a warning instead of loading a form when the RU has no project", async () => {
    render(ru({ projectId: null }));
    await flush();

    expect(container.textContent).toContain("Projet inconnu");
    expect(mockedGetEffectiveForm).not.toHaveBeenCalled();
  });
});
