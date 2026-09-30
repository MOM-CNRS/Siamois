import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { setLocale } from "../i18n";
import { PaneErrorBoundary } from "./PaneErrorBoundary";

let broken = true;
function Bomb() {
  if (broken) throw new Error("kaboom");
  return <p>ok</p>;
}

let container: HTMLDivElement;
let root: Root;
beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  broken = true;
});
afterEach(() => {
  setLocale("fr");
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
});

describe("PaneErrorBoundary", () => {
  it("shows a message with a retry instead of blanking, and recovers on retry", () => {
    act(() =>
      root.render(
        <PaneErrorBoundary label="l'onglet">
          <Bomb />
        </PaneErrorBoundary>,
      ),
    );
    expect(container.querySelector("[role=alert]")?.textContent).toContain("l'onglet");
    broken = false;
    act(() => container.querySelector<HTMLButtonElement>("button")!.click());
    expect(container.textContent).toContain("ok");
  });

  it("clears itself when the reset key changes", () => {
    const tree = (key: string) => (
      <PaneErrorBoundary label="x" resetKey={key}>
        <Bomb />
      </PaneErrorBoundary>
    );
    act(() => root.render(tree("a")));
    expect(container.querySelector("[role=alert]")).not.toBeNull();
    broken = false;
    act(() => root.render(tree("b")));
    expect(container.textContent).toContain("ok");
  });

  it("speaks the page language", () => {
    setLocale("en");
    act(() =>
      root.render(
        <PaneErrorBoundary label="the preview">
          <Bomb />
        </PaneErrorBoundary>,
      ),
    );
    expect(container.querySelector("[role=alert]")?.textContent).toContain("An error occurred in the preview.");
    expect(container.querySelector("button")?.textContent).toBe("Try again");
  });
});
