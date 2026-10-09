import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { DateRenderer } from "./renderers";
import type { FieldResource } from "./types";

const field: FieldResource = {
  id: "-3",
  resourceType: "fields",
  label: "Date de début",
  answerType: "DATETIME",
  isSystemField: true,
};

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
});

function type(input: HTMLInputElement, text: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
  act(() => {
    setter.call(input, text);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

describe("DateRenderer typed entry", () => {
  it("commits a typed date once the input is left, not while it is being typed", () => {
    const onChange = vi.fn();
    act(() => root.render(<DateRenderer field={field} value={null} readOnly={false} required={false} onChange={onChange} />));
    const input = container.querySelector("input")!;

    act(() => input.focus());
    type(input, "12/03/20");
    type(input, "12/03/2024");
    expect(onChange).not.toHaveBeenCalled();

    act(() => {
      input.dispatchEvent(new FocusEvent("focusout", { bubbles: true }));
      input.blur();
    });
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith("2024-03-12");
  });

  it("commits a typed date on Enter", () => {
    const onChange = vi.fn();
    act(() => root.render(<DateRenderer field={field} value={null} readOnly={false} required={false} onChange={onChange} />));
    const input = container.querySelector("input")!;

    act(() => input.focus());
    type(input, "01/02/2023");
    act(() => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    });
    expect(onChange).toHaveBeenCalledWith("2023-02-01");
  });
});
