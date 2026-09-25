import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "../../api/client";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import { ProjectFicheTab, buildPatch, isEmptyValue } from "./FicheTab";
import { getProjectTypes } from "./projectTypes";
import { getProjectHistory } from "./history";
import { patchProject } from "./api";
import type { ProjectDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./projectTypes", () => ({ getProjectTypes: vi.fn() }));
vi.mock("./history", () => ({ getProjectHistory: vi.fn() }));
vi.mock("./api", () => ({ patchProject: vi.fn() }));
// Opening a concept field's overlay auto-focuses it (CellEditOverlay.tsx), and a concept
// AutoComplete searches on focus (renderers.tsx) — a real optionSourceFor loader would then hit
// the network, which this test environment has no session/CSRF configured for. Not this file's
// concern (ResourceRefRenderer's own search has no error handling either way): stub the loader so
// opening a concept field never depends on the network at all.
vi.mock("../../fields/optionSources", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../fields/optionSources")>();
  return { ...actual, optionSourceFor: () => async () => [] };
});

const mockedGetProjectTypes = vi.mocked(getProjectTypes);
const mockedGetProjectHistory = vi.mocked(getProjectHistory);
const mockedPatchProject = vi.mocked(patchProject);

registerDefaultFieldRenderers();

// Mirrors the real ActionUnit.DETAILS_FORM shape (FormUiDtoLayoutJson.serialize) closely enough to
// exercise every branch FicheTab/FormField take: an editable TEXT field (name), a hidden one
// (identifier — edited via the panel header instead), a concept picker (type), a field that exists
// ONLY in the `answers` projection (oaCode), a multi-line TEXT (comments), a required one (status),
// a deactivated one (developer) and the spatial-context tree field.
const STANDARD = { span: 12, md: 6, lg: 3 };
const FULL = { span: 12, md: 12, lg: 12 };

const layout = [
  {
    className: null,
    name: "common.header.general",
    isSystemPanel: true,
    canUserAddFields: null,
    rows: [
      {
        columns: [
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: -102 },
          { width: STANDARD, hidden: true, isRequired: true, isReadOnly: true, fieldId: -103 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: -101 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: 42 },
        ],
      },
      {
        columns: [
          { width: FULL, isRequired: false, isReadOnly: false, fieldId: 43 },
          { width: STANDARD, isRequired: true, isReadOnly: false, fieldId: 44 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: 45 },
          { width: FULL, isRequired: false, isReadOnly: false, fieldId: 46 },
          { width: STANDARD, isRequired: false, isReadOnly: true, fieldId: 47 },
        ],
      },
    ],
  },
];

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
  "-102": field({ id: "-102", label: "Nom", valueBinding: "name" }),
  "-103": field({ id: "-103", label: "Identifiant", valueBinding: "identifier" }),
  "-101": field({
    id: "-101",
    label: "Type",
    answerType: "SELECT_ONE_FROM_FIELD_CODE",
    valueBinding: "type",
    fieldCode: "SIAAU.TYPE",
    // What CustomFieldConcept.getIcon() actually returns for every vocabulary-backed field: a
    // background-image class, not a Bootstrap Icons font glyph.
    icon: "sia-icon-opentheso",
    conceptUri: "https://thesaurus.example/?idt=1&idc=9",
  }),
  // CustomFieldText.getIcon(). Deliberately carries NO conceptUri, unlike the type field.
  "42": field({ id: "42", label: "Code OA", valueBinding: "oaCode", icon: "bi bi-alphabet" }),
  "43": field({ id: "43", label: "Commentaires", valueBinding: "comments", isTextArea: true }),
  "44": field({ id: "44", label: "Statut", answerType: "SELECT_ONE_FROM_FIELD_CODE", valueBinding: "status", fieldCode: "SIAAU.STATUS" }),
  "45": field({ id: "45", label: "Aménageur", valueBinding: "developer" }),
  "46": field({ id: "46", label: "Localisations", answerType: "SELECT_MULTIPLE_SPATIAL_UNIT_TREE", valueBinding: "spatialContext" }),
  "47": field({ id: "47", label: "Verrouillé", valueBinding: "zmin" }),
};

function project(overrides: Partial<ProjectDetail> = {}): ProjectDetail {
  return {
    resourceType: "projects",
    id: "1",
    name: "Fouille A",
    fullIdentifier: "INST-FA-2024",
    identifier: "FA",
    organization: { resourceType: "organizations", id: "7" },
    type: { resourceType: "concepts", id: "9", resolvedLabel: "Sondage" },
    _permissions: { canEdit: true, canDelete: false },
    // What GET /api/v1/projects/{id}?fields=all returns: raw values keyed by field id.
    answers: {
      "-102": "Fouille A",
      "-101": { resourceId: "9", resourceType: "concepts", label: "Sondage" },
      "42": "OA-2024-17",
      "43": "Un commentaire",
      "44": { resourceId: "3", resourceType: "concepts", label: "En cours" },
      "46": [{ resourceId: "55", resourceType: "spatial-units", label: "Lyon 5e" }],
      "47": "verrouillé",
    },
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

function renderFiche(entity: ProjectDetail, { writeMode = true, onSaved = vi.fn() } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <ProjectFicheTab entity={entity} onSaved={onSaved} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

// Every field is a plain value until clicked — FieldEditCell then opens CellEditOverlay
// (unchanged, reused straight from the list) on top of it, portalled to document.body. So driving
// a field means: click the cell of its column wrapper (found by the real fieldId, via the
// data-field-id attribute FicheTab.tsx sets — not a synthetic per-test className, now that column
// width is a structured object rather than a free-form class string) to open the (one,
// shared-per-field) overlay, then drive the overlay the exact same way CellEditOverlay.test.tsx
// does.
function fieldCol(fieldId: number | string): HTMLElement {
  const el = container.querySelector(`[data-field-id="${fieldId}"]`) as HTMLElement | null;
  if (!el) throw new Error(`No column for field id "${fieldId}"`);
  return el;
}

function openField(fieldId: number | string) {
  const cell = fieldCol(fieldId).querySelector(".field-value-cell-editable") as HTMLElement | null;
  if (!cell) throw new Error(`No editable cell for field id "${fieldId}"`);
  return act(async () => {
    cell.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

function overlayEl(): HTMLElement | null {
  return document.body.querySelector(".cell-edit-overlay");
}

function overlayInput(): HTMLInputElement | HTMLTextAreaElement {
  const el = document.body.querySelector(".cell-edit-overlay input, .cell-edit-overlay textarea");
  if (!el) throw new Error("No cell edit overlay is open");
  return el as HTMLInputElement | HTMLTextAreaElement;
}

function typeInto(value: string) {
  const el = overlayInput();
  const proto = el instanceof HTMLTextAreaElement ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
  const nativeSetter = Object.getOwnPropertyDescriptor(proto, "value")!.set!;
  return act(async () => {
    nativeSetter.call(el, value);
    el.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

// CellEditOverlay commits a text/number field on focus leaving the overlay, which it detects via a
// document-level mousedown outside its own box — not React's bubbling blur/focusout, which
// AutosavingField used and CellEditOverlay does not.
function clickOutsideOverlay() {
  return act(async () => {
    document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
  });
}

function pressKey(key: string) {
  return act(async () => {
    overlayInput().dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true }));
  });
}

beforeEach(() => {
  mockedGetProjectTypes.mockReset();
  mockedGetProjectHistory.mockReset();
  mockedPatchProject.mockReset();
  mockedPatchProject.mockResolvedValue(project());
  mockedGetProjectTypes.mockResolvedValue({ layoutJson: JSON.stringify(layout), fieldConfigs: [], tableColumns: [], fields });
  mockedGetProjectHistory.mockResolvedValue([]);
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

describe("ProjectFicheTab — layout", () => {
  it("renders every layout field from the answers projection, as a plain value until clicked", async () => {
    renderFiche(project());
    await flush();

    expect(fieldCol(-102).querySelector(".field-value-cell")?.textContent).toBe("Fouille A");
    // oaCode has no flat property on ProjectResource and is readable only through `answers`.
    expect(fieldCol(42).querySelector(".field-value-cell")?.textContent).toBe("OA-2024-17");
    expect(fieldCol(-101).querySelector(".field-value-cell")?.textContent).toBe("Sondage");
    expect(fieldCol(44).querySelector(".field-value-cell")?.textContent).toBe("En cours");
    expect(container.textContent).not.toContain("Non disponible");
    // No widget on screen until a value is clicked — that's the whole point of click-to-edit.
    expect(overlayEl()).toBeNull();
  });

  it("hides the identifier column, which the panel header edits instead", async () => {
    renderFiche(project());
    await flush();

    expect(container.querySelector('[data-field-id="-103"]')).toBeNull();
    expect(container.textContent).not.toContain("Identifiant");
  });

  it("opens a textarea for a field flagged isTextArea, and a plain input otherwise", async () => {
    renderFiche(project());
    await flush();

    await openField(43);
    expect(document.body.querySelector(".cell-edit-overlay textarea")).toBeTruthy();
    expect(document.body.querySelector(".cell-edit-overlay input")).toBeNull();

    await clickOutsideOverlay();
    await openField(42);
    expect(document.body.querySelector(".cell-edit-overlay textarea")).toBeNull();
    expect(document.body.querySelector(".cell-edit-overlay input")).toBeTruthy();
  });

  it("hides a deactivated field with no value, but keeps one that still holds data", async () => {
    mockedGetProjectTypes.mockResolvedValue({
      layoutJson: JSON.stringify(layout),
      fieldConfigs: [
        { field: "45", active: false, institutionLocked: false },
        { field: "42", active: false, institutionLocked: false },
      ],
      tableColumns: [],
      fields,
    });
    renderFiche(project());
    await flush();

    // developer (45) is deactivated AND empty → gone, like JSF's isColumnEnabled gate.
    expect(container.querySelector('[data-field-id="45"]')).toBeNull();
    // oaCode (42) is deactivated but still has an answer → stays, same escape hatch as JSF.
    expect(container.querySelector('[data-field-id="42"]')).toBeTruthy();
  });

  it("renders each field's own type icon in its label (CustomField.getIcon)", async () => {
    renderFiche(project());
    await flush();

    // A Bootstrap Icons font glyph...
    expect(fieldCol(42).querySelector(".ellipsis-btn .bi.bi-alphabet")).toBeTruthy();
    // ...and a background-image class, which is what every vocabulary-backed field gets.
    expect(fieldCol(-101).querySelector(".ellipsis-btn .sia-icon-opentheso")).toBeTruthy();
  });

  it("renders no icon element for a field the catalog gives no icon", async () => {
    renderFiche(project());
    await flush();

    // `developer` (45) has no icon in this catalog — an empty icon span would show as a gap.
    expect(fieldCol(45).querySelector(".ellipsis-btn .p-button-icon")).toBeNull();
  });
});

describe("ProjectFicheTab — thesaurus menu on the field label", () => {
  function labelButton(text: string): HTMLElement {
    const button = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === text);
    if (!button) throw new Error(`No field label button "${text}"`);
    return button;
  }

  it("uses PrimeReact's real flat class, not PrimeFaces' name for it", async () => {
    renderFiche(project());
    await flush();

    const button = labelButton("Type");
    // p-button-flat does not exist in PrimeReact: using it left lara's solid blue fill in place
    // under a grey label, which is what made the field header unreadable.
    expect(button.classList.contains("p-button-flat")).toBe(false);
    expect(button.classList.contains("p-button-text")).toBe(true);
  });

  it("opens the thesaurus link from the label of a field that has a conceptUri", async () => {
    renderFiche(project());
    await flush();

    // panelField.xhtml's p:menu is overlay="true" trigger="btn": it exists only once pressed.
    expect(document.querySelector(".p-menu a[href]")).toBeNull();

    await act(async () => {
      labelButton("Type").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const link = document.querySelector(".p-menu a[href]") as HTMLAnchorElement;
    expect(link.getAttribute("href")).toBe("https://thesaurus.example/?idt=1&idc=9");
    expect(link.textContent).toContain("Voir dans le thésaurus");
    expect(link.getAttribute("target")).toBe("_blank");
  });

  // Characterisation, not a regression guard: PrimeReact's Portal already falls back to
  // document.body. It is asserted because the fiche sits in an overflow:auto box that would clip
  // an in-place popup, so anything that changes where this lands (a global PrimeReact.appendTo,
  // say) must fail here rather than silently produce an invisible menu.
  it("portals the menu to the body, outside the panel's own scroll container", async () => {
    renderFiche(project());
    await flush();

    await act(async () => {
      labelButton("Type").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const menu = document.querySelector(".p-menu") as HTMLElement;
    expect(menu).toBeTruthy();
    // The regression this locks down: rendered in place, the menu sits inside the fiche's
    // overflow:auto box and is clipped away — it looks like clicking the label does nothing.
    expect(container.contains(menu)).toBe(false);
    expect(document.body.contains(menu)).toBe(true);
  });

  it("presents a field with no thesaurus entry as non-interactive", async () => {
    renderFiche(project());
    await flush();

    // `Code OA` has an icon but no conceptUri.
    const button = labelButton("Code OA");
    expect(button.getAttribute("aria-haspopup")).toBeNull();
    expect(button.classList.contains("has-thesaurus-entry")).toBe(false);

    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(document.querySelector(".p-menu")).toBeNull();
  });

  it("marks a field that does have one as a menu trigger", async () => {
    renderFiche(project());
    await flush();

    const button = labelButton("Type");
    expect(button.getAttribute("aria-haspopup")).toBe("menu");
    expect(button.classList.contains("has-thesaurus-entry")).toBe(true);
  });
});

describe("ProjectFicheTab — the app's read/write switch is the only edit gate", () => {
  it("offers no Modifier / Enregistrer / Annuler of its own", async () => {
    renderFiche(project());
    await flush();

    const labels = Array.from(container.querySelectorAll("button")).map((b) => b.textContent);
    expect(labels).not.toContain("Modifier");
    expect(labels).not.toContain("Enregistrer");
    expect(labels).not.toContain("Annuler");
  });

  it("offers no click-to-edit affordance in read mode", async () => {
    renderFiche(project(), { writeMode: false });
    await flush();

    expect(fieldCol(-102).querySelector(".field-value-cell-editable")).toBeNull();
    expect(fieldCol(42).querySelector(".field-value-cell-editable")).toBeNull();
    expect(fieldCol(44).querySelector(".field-value-cell-editable")).toBeNull();
  });

  it("offers the click-to-edit affordance in write mode", async () => {
    renderFiche(project());
    await flush();

    expect(fieldCol(-102).querySelector(".field-value-cell-editable")).not.toBeNull();
    expect(fieldCol(42).querySelector(".field-value-cell-editable")).not.toBeNull();
  });

  it("stays read-only in write mode when the user has no edit right on this project", async () => {
    renderFiche(project({ _permissions: { canEdit: false, canDelete: false } }));
    await flush();

    expect(fieldCol(-102).querySelector(".field-value-cell-editable")).toBeNull();
  });

  it("keeps a column the layout marks readOnly non-editable even in write mode", async () => {
    renderFiche(project());
    await flush();

    expect(fieldCol(47).querySelector(".field-value-cell-editable")).toBeNull();
  });
});

describe("ProjectFicheTab — per-field click-to-edit", () => {
  it("saves a text field when focus leaves the overlay, through the answers map keyed by field id", async () => {
    const onSaved = renderFiche(project());
    await flush();

    await openField(42);
    await typeInto("OA-2025-01");
    expect(mockedPatchProject).not.toHaveBeenCalled(); // not per keystroke

    await clickOutsideOverlay();
    await flush();

    expect(mockedPatchProject).toHaveBeenCalledWith("1", { answers: { "42": { value: "OA-2025-01" } } });
    expect(onSaved).toHaveBeenCalled();
    // The overlay closes once the save lands, back to the plain value.
    expect(overlayEl()).toBeNull();
  });

  it("saves a text field on Enter too", async () => {
    renderFiche(project());
    await flush();

    await openField(-102);
    await typeInto("Renamed");
    await pressKey("Enter");
    await flush();

    expect(mockedPatchProject).toHaveBeenCalledWith("1", { answers: { "-102": { value: "Renamed" } } });
  });

  it("sends only the field that changed, never the whole form", async () => {
    renderFiche(project());
    await flush();

    await openField(42);
    await typeInto("OA-2025-01");
    await clickOutsideOverlay();
    await flush();

    expect(mockedPatchProject).toHaveBeenCalledTimes(1);
    expect(Object.keys(mockedPatchProject.mock.calls[0][1].answers!)).toEqual(["42"]);
  });

  it("issues no PATCH at all when a field is closed untouched", async () => {
    renderFiche(project());
    await flush();

    await openField(42);
    await clickOutsideOverlay();
    await flush();

    expect(mockedPatchProject).not.toHaveBeenCalled();
  });

  it("discards the edit on Escape and saves nothing", async () => {
    renderFiche(project());
    await flush();

    await openField(42);
    await typeInto("typed but abandoned");
    await pressKey("Escape");
    await flush();

    expect(overlayEl()).toBeNull();
    expect(fieldCol(42).querySelector(".field-value-cell")?.textContent).toBe("OA-2024-17");
    expect(mockedPatchProject).not.toHaveBeenCalled();
  });

  it("refuses to clear a required field, naming it inline instead of letting the server reject it", async () => {
    renderFiche(project());
    await flush();

    await openField(44);
    await typeInto("");
    await flush();

    expect(overlayEl()!.textContent).toContain("obligatoire");
    expect(mockedPatchProject).not.toHaveBeenCalled();
    // Kept open with the error, exactly like a failed save — not silently discarded.
    expect(overlayEl()).not.toBeNull();
  });

  it("keeps the edit on screen with the server's own message when the save fails", async () => {
    mockedPatchProject.mockRejectedValue(new ApiError(409, "Identifiant déjà utilisé"));
    renderFiche(project());
    await flush();

    await openField(42);
    await typeInto("OA-DUP");
    await clickOutsideOverlay();
    await flush();

    expect(overlayEl()!.textContent).toContain("Identifiant déjà utilisé");
    expect(overlayInput().value).toBe("OA-DUP");
  });
});

describe("ProjectFicheTab — footer", () => {
  it("builds the footer from the whole history: creation, last change and distinct contributors", async () => {
    mockedGetProjectHistory.mockResolvedValue([
      { revisionNumber: 3, revisionDate: "2026-09-01T10:00:00Z", revisionType: "MOD", author: { id: 2, name: "Grace", lastname: "Hopper" } },
      { revisionNumber: 2, revisionDate: "2026-05-02T10:00:00Z", revisionType: "MOD", author: { id: 1, name: "Ada", lastname: "Lovelace" } },
      { revisionNumber: 1, revisionDate: "2026-01-03T10:00:00Z", revisionType: "ADD", author: { id: 1, name: "Ada", lastname: "Lovelace" } },
    ]);
    renderFiche(project());
    await flush();

    const text = container.textContent ?? "";
    expect(text).toContain("Créé le");
    expect(text).toContain("03/01/2026");
    expect(text).toContain("Modifié le");
    expect(text).toContain("01/09/2026");
    expect(text).toContain("Contributeurs : Grace Hopper, Ada Lovelace");
  });

  it("renders no footer at all when the project has no history", async () => {
    renderFiche(project());
    await flush();

    expect(container.querySelector(".panel-footer")).toBeNull();
  });
});

describe("buildPatch", () => {
  it("routes every ordinary field through answers", () => {
    expect(buildPatch({ "42": "OA-1", "44": { resourceId: "3" } }, fields)).toEqual({
      answers: { "42": { value: "OA-1" }, "44": { value: "3" } },
    });
  });

  it("routes the spatial-context tree field to its flat alias, which is the only path PATCH accepts", () => {
    expect(
      buildPatch({ "46": [{ resourceId: "55" }, { resourceId: "56" }] }, fields),
    ).toEqual({ spatialContextSpatialUnitIds: ["55", "56"] });
  });

  it("clears the spatial context with an empty list rather than omitting the key", () => {
    expect(buildPatch({ "46": [] }, fields)).toEqual({ spatialContextSpatialUnitIds: [] });
  });

  it("sends a SELECT_MULTIPLE field as values, never as value", () => {
    const multi = { "50": field({ id: "50", answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE" }) };
    expect(buildPatch({ "50": [{ resourceId: "1" }] }, multi)).toEqual({ answers: { "50": { values: ["1"] } } });
  });

  it("ignores a draft entry whose field is no longer in the catalog", () => {
    expect(buildPatch({ "999": "orphan" }, fields)).toEqual({});
  });
});

describe("isEmptyValue", () => {
  it("treats null, a blank string and an empty list as empty", () => {
    expect(isEmptyValue(null)).toBe(true);
    expect(isEmptyValue(undefined)).toBe(true);
    expect(isEmptyValue("   ")).toBe(true);
    expect(isEmptyValue([])).toBe(true);
  });

  it("treats 0 and false as present, not empty", () => {
    expect(isEmptyValue(0)).toBe(false);
    expect(isEmptyValue(false)).toBe(false);
  });
});
