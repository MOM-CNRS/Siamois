import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import { registerFieldRenderer } from "../../fields/registry";
import { ApiError } from "../../api/client";
import { CellEditOverlay, type CellEditTarget } from "./CellEditOverlay";
import type { FieldResource } from "../../fields/types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

registerDefaultFieldRenderers();

// Stand-ins for the two "commits immediately" shapes, so a value change can be triggered directly
// instead of by driving PrimeReact's AutoComplete through a real suggestion click. They keep the
// SELECT_ONE_/SELECT_MULTIPLE_ prefixes the overlay branches on.
function picker(pickedValue: unknown) {
  return function PickerRenderer({ onChange }: { onChange: (value: unknown) => void }) {
    return (
      <button type="button" className="test-pick" onClick={() => onChange(pickedValue)}>
        pick
      </button>
    );
  };
}
registerFieldRenderer("SELECT_ONE_TEST", picker("picked"));
// A SELECT_MULTIPLE renderer's onChange carries the whole selection, not the one item picked.
registerFieldRenderer("SELECT_MULTIPLE_TEST", picker(["picked"]));

interface Row {
  id: string;
  oaCode?: string;
}

const singleSelectField: FieldResource = {
  id: "-118",
  resourceType: "fields",
  label: "Statut",
  answerType: "SELECT_ONE_TEST",
  isSystemField: false,
};

const multiSelectField: FieldResource = {
  id: "-120",
  resourceType: "fields",
  label: "Périodes",
  answerType: "SELECT_MULTIPLE_TEST",
  isSystemField: false,
};

const textField: FieldResource = {
  id: "-109",
  resourceType: "fields",
  label: "Code OA",
  answerType: "TEXT",
  isSystemField: true,
  valueBinding: "oaCode",
};

// jsdom gives every element a zero rect; the exact numbers don't matter to any assertion here,
// only that the overlay is positioned from one.
const ANCHOR = { top: 120, left: 40, width: 200, height: 24 } as DOMRect;

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

// The editor portals to document.body (it is position: fixed over the cell), so every query goes
// through the body, not through `container`.
function overlayEl(): HTMLElement | null {
  return document.body.querySelector(".cell-edit-overlay");
}

function overlayInput(): HTMLInputElement {
  return document.body.querySelector(".cell-edit-overlay input") as HTMLInputElement;
}

function type(input: HTMLInputElement, value: string) {
  const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
  nativeSetter.call(input, value);
  input.dispatchEvent(new Event("input", { bubbles: true }));
}

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
    expect(overlayEl()).toBeNull();
  });

  it("seeds the draft from the target row's current value via the field's binding", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR } });
    await flush();

    expect(overlayInput().value).toBe("OA-2024");
  });

  it("shows only the field widget — no label, no Save/Cancel buttons", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR } });
    await flush();

    expect(overlayEl()!.textContent).not.toContain("Code OA");
    expect(overlayEl()!.querySelectorAll("button")).toHaveLength(0);
  });

  it("positions itself over the anchor cell", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR } });
    await flush();

    const style = overlayEl()!.style;
    expect(style.position).toBe("fixed");
    expect(style.top).toBe("120px");
    expect(style.left).toBe("40px");
  });

  it("focuses the field and selects its text on open, so the user can type straight away", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR } });
    await flush();

    const input = overlayInput();
    expect(document.activeElement).toBe(input);
    expect(input.selectionStart).toBe(0);
    expect(input.selectionEnd).toBe("OA-2024".length);
  });

  it("saves on Enter with the row id and an AnswerInput-shaped map keyed by field id, then closes", async () => {
    const { onSave, onSaved, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR },
    });
    await flush();

    const input = overlayInput();
    await act(async () => {
      type(input, "OA-2025");
    });
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();

    expect(onSave).toHaveBeenCalledWith("1", { "-109": { value: "OA-2025" } });
    expect(onSaved).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("saves when focus leaves the editor (a click elsewhere finishes the edit)", async () => {
    const { onSave, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR },
    });
    await flush();

    await act(async () => {
      type(overlayInput(), "OA-2025");
    });
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(onSave).toHaveBeenCalledWith("1", { "-109": { value: "OA-2025" } });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("issues no PATCH at all when the value was never changed", async () => {
    const { onSave, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR },
    });
    await flush();

    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(onSave).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does not close on a mousedown inside a widget's own portalled popup", async () => {
    // The AutoComplete suggestion list lives in document.body, not inside the editor: treating it
    // as an outside click closed the editor before the click reached the item, so picking a
    // suggestion saved nothing at all.
    const { onSave, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR },
    });
    await flush();

    const popup = document.createElement("div");
    popup.className = "p-autocomplete-panel p-component p-connected-overlay-enter-done";
    const item = document.createElement("li");
    popup.appendChild(item);
    document.body.appendChild(popup);

    await act(async () => {
      item.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(onClose).not.toHaveBeenCalled();
    expect(onSave).not.toHaveBeenCalled();
    popup.remove();
  });

  it("saves and closes as soon as a single-valued select changes — no Enter needed", async () => {
    const { onSave, onClose } = renderOverlay({
      target: { row: { id: "1" }, field: singleSelectField, anchor: ANCHOR },
    });
    await flush();

    const pick = document.body.querySelector(".test-pick") as HTMLElement;
    await act(async () => {
      pick.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(onSave).toHaveBeenCalledWith("1", { "-118": { value: "picked" } });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("saves each pick of a multi-valued field but stays open for the next one", async () => {
    const { onSave, onClose } = renderOverlay({
      target: { row: { id: "1" }, field: multiSelectField, anchor: ANCHOR },
    });
    await flush();

    const pick = document.body.querySelector(".test-pick") as HTMLElement;
    await act(async () => {
      pick.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    // One pick is not the whole answer — closing here would force a reopen per value.
    expect(onSave).toHaveBeenCalledWith("1", { "-120": { values: ["picked"] } });
    expect(onClose).not.toHaveBeenCalled();
    expect(overlayEl()).not.toBeNull();
  });

  it("cancels on Escape without saving", async () => {
    const { onSave, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR },
    });
    await flush();

    await act(async () => {
      type(overlayInput(), "OA-2025");
    });
    await act(async () => {
      overlayEl()!.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });

    expect(onSave).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("shows the ApiError message inline and does not close on a failed save (e.g. 409 duplicate identifier)", async () => {
    const onSave = vi.fn().mockRejectedValue(new ApiError(409, "Identifiant déjà utilisé"));
    const { onSaved, onClose } = renderOverlay({
      target: { row: { id: "1", oaCode: "OA-2024" }, field: textField, anchor: ANCHOR },
      onSave,
    });
    await flush();

    const input = overlayInput();
    await act(async () => {
      type(input, "OA-2025");
    });
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    await flush();

    expect(overlayEl()!.textContent).toContain("Identifiant déjà utilisé");
    expect(onSaved).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("re-seeds the draft when the target changes without unmounting (single shared overlay instance)", async () => {
    renderOverlay({ target: { row: { id: "1", oaCode: "A" }, field: textField, anchor: ANCHOR } });
    await flush();
    expect(overlayInput().value).toBe("A");

    act(() => {
      root.render(
        <CellEditOverlay
          target={{ row: { id: "2", oaCode: "B" }, field: textField, anchor: ANCHOR }}
          onSave={vi.fn().mockResolvedValue(undefined)}
          onSaved={vi.fn()}
          onClose={vi.fn()}
        />,
      );
    });
    await flush();
    expect(overlayInput().value).toBe("B");
  });
});
