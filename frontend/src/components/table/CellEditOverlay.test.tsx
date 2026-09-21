import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import { ApiError } from "../../api/client";
import { CellEditOverlay, type CellEditTarget } from "./CellEditOverlay";
import type { FieldResource } from "../../fields/types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

registerDefaultFieldRenderers();

interface Row {
  id: string;
  oaCode?: string;
}

const textField: FieldResource = {
  id: "-109",
  resourceType: "fields",
  label: "Code OA",
  answerType: "TEXT",
  isSystemField: true,
  valueBinding: "oaCode",
};

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 3; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

beforeEach(() => {
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

function renderOverlay(opts: {
  target: CellEditTarget<Row> | null;
  onSave?: ReturnType<typeof vi.fn>;
  onSaved?: ReturnType<typeof vi.fn>;
  onClose?: ReturnType<typeof vi.fn>;
}) {
  const onSave = opts.onSave ?? vi.fn().mockResolvedValue(undefined);
  const onSaved = opts.onSaved ?? vi.fn();
  const onClose = opts.onClose ?? vi.fn();
  act(() => {
    root.render(
      <CellEditOverlay target={opts.target} onSave={onSave} onSaved={onSaved} onClose={onClose} />,
    );
  });
  return { onSave, onSaved, onClose };
}

describe("CellEditOverlay", () => {
  it("renders nothing when there is no target", async () => {
    renderOverlay({ target: null });
    await flush();
    expect(container.innerHTML).toBe("");
  });

  it("seeds the draft from the target row's current value via the field's binding", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField } });
    await flush();

    const input = container.querySelector("input") as HTMLInputElement;
    expect(input.value).toBe("OA-2024");
  });

  it("shows the field label", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField } });
    await flush();
    expect(container.textContent).toContain("Code OA");
  });

  it("calls onSave with the row id and an AnswerInput-shaped map keyed by field id", async () => {
    const { onSave, onSaved, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField },
    });
    await flush();

    const input = container.querySelector("input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "OA-2025");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });

    const saveButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Enregistrer")!;
    await act(async () => {
      saveButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(onSave).toHaveBeenCalledWith("1", { "-109": { value: "OA-2025" } });
    expect(onSaved).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("saves on Enter for a scalar field", async () => {
    const { onSave } = renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField } });
    await flush();

    const input = container.querySelector("input") as HTMLInputElement;
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();

    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it("cancels on Escape without saving", async () => {
    const { onSave, onClose } = renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField } });
    await flush();

    const overlay = container.querySelector(".cell-edit-overlay") as HTMLElement;
    await act(async () => {
      overlay.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });

    expect(onSave).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("shows the ApiError message inline and does not close on a failed save (e.g. 409 duplicate identifier)", async () => {
    const onSave = vi.fn().mockRejectedValue(new ApiError(409, "Identifiant déjà utilisé"));
    const { onSaved, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField },
      onSave,
    });
    await flush();

    const saveButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Enregistrer")!;
    await act(async () => {
      saveButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.textContent).toContain("Identifiant déjà utilisé");
    expect(onSaved).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("re-seeds the draft when the target changes without unmounting (single shared overlay instance)", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "A" }, field: textField } });
    await flush();
    expect((container.querySelector("input") as HTMLInputElement).value).toBe("A");

    act(() => {
      root.render(
        <CellEditOverlay
          target={{ row: { id: "2", oaCode: "B" }, field: textField }}
          onSave={vi.fn().mockResolvedValue(undefined)}
          onSaved={vi.fn()}
          onClose={vi.fn()}
        />,
      );
    });
    await flush();
    expect((container.querySelector("input") as HTMLInputElement).value).toBe("B");
  });
});
