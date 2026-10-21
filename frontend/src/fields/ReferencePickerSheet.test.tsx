import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { ReferencePickerSheet } from "./ReferencePickerSheet";
import type { FilterOption } from "./optionSources";

let container: HTMLDivElement;
let root: Root;

const OPTIONS: FilterOption[] = [
  { id: "1", label: "Argile" },
  { id: "2", label: "Sable" },
];

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

async function openSheet(props: Partial<React.ComponentProps<typeof ReferencePickerSheet>> = {}) {
  const onCommit = vi.fn();
  await act(async () => {
    root.render(
      <ReferencePickerSheet multiple={false} selected={[]} loadOptions={async () => OPTIONS} onCommit={onCommit} {...props} />,
    );
  });
  await act(async () => {
    container.querySelector<HTMLButtonElement>(".resource-ref-sheet-trigger")!.click();
  });
  return onCommit;
}

const options = () => [...document.querySelectorAll<HTMLButtonElement>(".resource-ref-sheet-option")];

describe("ReferencePickerSheet", () => {
  it("lists what the loader returns in a drawer, and a single pick commits at once", async () => {
    const onCommit = await openSheet();
    expect(options().map((o) => o.textContent)).toEqual(["Argile", "Sable"]);
    await act(async () => options()[1].click());
    expect(onCommit).toHaveBeenCalledWith([OPTIONS[1]]);
  });

  it("a multiple field commits its whole selection on « Terminé »", async () => {
    const onCommit = await openSheet({ multiple: true });
    await act(async () => options()[0].click());
    await act(async () => options()[1].click());
    expect(onCommit).not.toHaveBeenCalled();
    const buttons = document.querySelectorAll<HTMLButtonElement>(".resource-ref-sheet-actions button");
    const done = buttons[buttons.length - 1];
    await act(async () => done.click());
    expect(onCommit).toHaveBeenCalledWith(OPTIONS);
  });

  it("shows the picked labels on the trigger, and clears a single value from the drawer", async () => {
    const onCommit = await openSheet({ selected: [OPTIONS[0]] });
    expect(container.querySelector(".resource-ref-sheet-token")?.textContent).toBe("Argile");
    const clear = document.querySelector<HTMLButtonElement>(".resource-ref-sheet-actions button")!;
    await act(async () => clear.click());
    expect(onCommit).toHaveBeenCalledWith([]);
  });
});
