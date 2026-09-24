import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { EntityCountCard } from "./EntityCountCard";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

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

function render(count?: number, onOpen = vi.fn()) {
  act(() => {
    root.render(
      <EntityCountCard
        icon="bi bi-layers"
        label="Phases"
        description="Phases et sous-phases"
        count={count}
        className="sia-welcome-card"
        chipClassName="chip"
        onOpen={onOpen}
      />,
    );
  });
  return onOpen;
}

function link(): HTMLElement {
  return container.querySelector('[role="link"]') as HTMLElement;
}

describe("EntityCountCard", () => {
  it("renders label, description and count, with no button", () => {
    render(5);

    expect(container.textContent).toContain("Phases");
    expect(container.textContent).toContain("Phases et sous-phases");
    expect(container.querySelector(".p-chip")!.textContent).toBe("5");
    expect(container.querySelector("button")).toBeNull();
  });

  it("shows a placeholder while the count is loading", () => {
    render(undefined);

    expect(container.querySelector(".p-chip")!.textContent).toBe("…");
  });

  it("opens on a click anywhere on the card", () => {
    const onOpen = render(5);

    act(() => {
      container.querySelector("small")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("is keyboard-reachable and opens on Enter and Space", () => {
    const onOpen = render(5);

    expect(link().tabIndex).toBe(0);
    act(() => {
      link().dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
      link().dispatchEvent(new KeyboardEvent("keydown", { key: " ", bubbles: true }));
      link().dispatchEvent(new KeyboardEvent("keydown", { key: "a", bubbles: true }));
    });

    expect(onOpen).toHaveBeenCalledTimes(2);
  });
});
