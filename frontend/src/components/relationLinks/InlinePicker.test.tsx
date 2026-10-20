import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { InlinePicker, type PickerOption } from "./InlinePicker";

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 3; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const options: PickerOption[] = [{ id: "1", label: "US 1" }, { id: "2", label: "US 2" }];

function render(props: Partial<React.ComponentProps<typeof InlinePicker>> = {}) {
  const onChange = vi.fn();
  const load = vi.fn().mockResolvedValue(options);
  act(() => {
    root.render(<InlinePicker value={null} onChange={onChange} load={load} placeholder="Search…" {...props} />);
  });
  return { onChange, load };
}

describe("InlinePicker", () => {
  it("lists options as soon as the field is focused, even empty", async () => {
    const { load } = render();
    act(() => container.querySelector("input")!.focus());
    await flush();

    expect(load).toHaveBeenCalledWith("");
    expect([...container.querySelectorAll("li button")].map((b) => b.textContent)).toEqual(["US 1", "US 2"]);
  });

  it("hands back the option picked and folds the list", async () => {
    const { onChange } = render();
    act(() => container.querySelector("input")!.focus());
    await flush();

    await act(async () => {
      (container.querySelectorAll("li button")[1] as HTMLElement).click();
    });

    expect(onChange).toHaveBeenCalledWith(options[1]);
    expect(container.querySelector("ul")).toBeNull();
  });

  it("shows the footer as the last line of the list", async () => {
    render({ footer: <button type="button">New unit</button> });
    act(() => container.querySelector("input")!.focus());
    await flush();

    const lines = [...container.querySelectorAll("li")];
    expect(lines[lines.length - 1].textContent).toBe("New unit");
  });

  it("lets a later search overwrite an earlier, slower one, never the other way round", async () => {
    let resolveSlow: (value: PickerOption[]) => void = () => undefined;
    const load = vi
      .fn()
      .mockImplementationOnce(() => new Promise<PickerOption[]>((resolve) => { resolveSlow = resolve; }))
      .mockResolvedValue([{ id: "9", label: "Fresh" }]);
    render({ load });
    const input = container.querySelector("input")!;
    act(() => input.focus());
    // A second search while the first is pending.
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
      setter.call(input, "x");
      input.dispatchEvent(new Event("input", { bubbles: true }));
      await new Promise((resolve) => setTimeout(resolve, 300));
    });
    await act(async () => resolveSlow([{ id: "0", label: "Stale" }]));

    expect([...container.querySelectorAll("li button")].map((b) => b.textContent)).toEqual(["Fresh"]);
  });
});
