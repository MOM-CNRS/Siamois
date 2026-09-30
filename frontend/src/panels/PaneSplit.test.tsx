import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { PaneSplit } from "./PaneSplit";


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

// Stands in for a list or fiche: local state that a remount would lose (page, filters, tab…).
function Counter() {
  const [count, setCount] = useState(0);
  return (
    <button type="button" className="counter" onClick={() => setCount((c) => c + 1)}>
      {count}
    </button>
  );
}

function render(overviewOpen: boolean) {
  act(() => {
    root.render(
      <PaneSplit
        main={<Counter />}
        mainClassName="panel-splitter-panel-l"
        overview={overviewOpen ? <div className="overview-content" /> : null}
        overviewClassName="panel-splitter-panel-r"
      />,
    );
  });
}

describe("PaneSplit", () => {
  it("keeps the main pane mounted when the overview opens and closes", () => {
    render(false);
    const counter = () => container.querySelector(".counter") as HTMLButtonElement;
    act(() => counter().click());
    act(() => counter().click());
    expect(counter().textContent).toBe("2");

    render(true);
    expect(container.querySelector(".overview-content")).toBeTruthy();
    expect(counter().textContent).toBe("2");

    render(false);
    expect(container.querySelector(".overview-content")).toBeNull();
    expect(counter().textContent).toBe("2");
  });

  it("renders a gutter only while an overview is open", () => {
    render(false);
    expect(container.querySelector(".p-splitter-gutter")).toBeNull();
    render(true);
    expect(container.querySelector(".p-splitter-gutter")).toBeTruthy();
  });
});
