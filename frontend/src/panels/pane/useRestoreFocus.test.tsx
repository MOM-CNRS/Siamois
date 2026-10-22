import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { useRestoreFocus } from "./useRestoreFocus";

let container: HTMLDivElement;
let root: Root;

function Probe({ open }: { open: boolean }) {
  useRestoreFocus(open);
  return open ? <input id="inside" autoFocus /> : null;
}

function render(open: boolean) {
  act(() => root.render(<><button id="opener" /><button id="other" /><Probe open={open} /></>));
}

beforeEach(() => {
  vi.useFakeTimers();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
});

const el = (id: string) => document.getElementById(id)!;

describe("useRestoreFocus", () => {
  it("gives the focus back to what held it when the overlay opened", () => {
    render(false);
    el("opener").focus();
    render(true);
    expect(document.activeElement).toBe(el("inside"));
    render(false);
    act(() => vi.runAllTimers());
    expect(document.activeElement).toBe(el("opener"));
  });

  it("leaves the focus alone when something else took it meanwhile", () => {
    render(false);
    el("opener").focus();
    render(true);
    render(false);
    el("other").focus();
    act(() => vi.runAllTimers());
    expect(document.activeElement).toBe(el("other"));
  });

  it("does nothing when nothing was focused", () => {
    render(false);
    render(true);
    render(false);
    act(() => vi.runAllTimers());
    expect(document.activeElement).toBe(document.body);
  });
});
