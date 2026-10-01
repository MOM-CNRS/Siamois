import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { FormLayoutView } from "./FormLayoutView";
import { registerDefaultFieldRenderers } from "./registerDefaultRenderers";
import type { FormLayoutPanel } from "../fields/layout";
import type { FieldResource } from "./types";


registerDefaultFieldRenderers();

const W = { span: 12 };

function field(id: string, label: string, answerType = "TEXT"): FieldResource {
  return { id, resourceType: "fields", label, answerType, isSystemField: false };
}

const fields: Record<string, FieldResource> = {
  "1": field("1", "Nature"),
  "2": field("2", "Forme d'érosion"),
  "3": field("3", "Z inf", "INTEGER"),
  "4": field("4", "Z sup", "INTEGER"),
  "5": field("5", "Remarque"),
};

const panels: FormLayoutPanel[] = [
  {
    name: "general",
    rows: [
      {
        columns: [
          { width: W, isRequired: false, isReadOnly: false, fieldId: 1 },
          {
            width: W,
            isRequired: false,
            isReadOnly: false,
            fieldId: 2,
            rules: { enabledWhen: { fieldId: 1, op: "EQ", values: ["érosion"] } },
          },
          { width: W, isRequired: false, isReadOnly: false, fieldId: 3 },
          {
            width: W,
            isRequired: false,
            isReadOnly: false,
            fieldId: 4,
            rules: { constraints: [{ op: "GTE", fieldId: 3 }] },
          },
          {
            width: W,
            isRequired: false,
            isReadOnly: false,
            fieldId: 5,
            rules: { requiredWhen: { fieldId: 1, op: "NOT_EMPTY" } },
          },
        ],
      },
    ],
  },
];

function entity(answers: Record<string, unknown>) {
  return { id: "42", answers };
}

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.innerHTML = "";
});

function render(answers: Record<string, unknown>, onSave = vi.fn().mockResolvedValue(undefined), onSaved = vi.fn()) {
  act(() => {
    root.render(
      <FormLayoutView
        entity={entity(answers)}
        entityType="recordingUnit"
        fields={fields}
        panels={panels}
        canEdit
        onSave={onSave}
        onSaved={onSaved}
      />,
    );
  });
  return { onSave, onSaved };
}

function col(fieldId: string): HTMLElement {
  const el = container.querySelector(`[data-field-id="${fieldId}"]`);
  if (!el) throw new Error(`no column ${fieldId}`);
  return el as HTMLElement;
}

describe("FormLayoutView rules", () => {
  it("greys a field whose condition is false and flags the value it still holds", () => {
    render({ "1": "fosse", "2": "en V" });

    expect(col("2").classList.contains("sia-field--disabled")).toBe(true);
    expect(col("2").querySelector(".field-value-cell-warning")).not.toBeNull();
    expect(col("2").querySelector(".field-value-cell-editable")).toBeNull();
  });

  it("enables the field once the condition holds", () => {
    render({ "1": "érosion", "2": "en V" });

    expect(col("2").classList.contains("sia-field--disabled")).toBe(false);
    expect(col("2").querySelector(".field-value-cell-editable")).not.toBeNull();
    expect(col("2").querySelector(".field-value-cell-warning")).toBeNull();
  });

  it("clears an incoherent value from its overlay with « Vider »", async () => {
    const { onSave, onSaved } = render({ "1": "fosse", "2": "en V" });

    await act(async () => {
      col("2").querySelector(".field-value-cell")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    const banner = document.body.querySelector(".cell-edit-overlay-incoherent");
    expect(banner?.textContent).toContain("ne s'applique plus");
    await act(async () => {
      (document.body.querySelector(".cell-edit-overlay-clear") as HTMLButtonElement).click();
    });

    expect(onSave).toHaveBeenCalledWith("42", { "2": { value: null } }, null);
    expect(onSaved).toHaveBeenCalled();
  });

  it("marks a field required by requiredWhen", () => {
    render({ "1": "fosse" });

    expect(col("5").querySelector(".required-btn")).not.toBeNull();
  });

  it("flags both sides of a violated constraint", () => {
    render({ "3": 5, "4": 2 });

    expect(col("3").querySelector(".field-value-cell-warning")).not.toBeNull();
    expect(col("4").querySelector(".field-value-cell-warning")).not.toBeNull();
    expect(col("4").querySelector(".field-value-cell")?.getAttribute("title")).toContain("« Z inf »");
  });
});
