import {afterEach, beforeEach, describe, expect, it} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {createTaskProgressStore, TaskToastContent} from "./TaskToastContent";

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

describe("TaskToastContent", () => {
  it("shows the task starting, then follows its progress", () => {
    const store = createTaskProgressStore();
    act(() => root.render(<TaskToastContent label="Suppression en cours" store={store} />));

    expect(container.textContent).toContain("Suppression en cours");
    expect(container.textContent).toContain("Démarrage…");

    act(() => store.set({ done: 120, total: 450 }));

    expect(container.textContent).toContain("120 / 450 (27 %)");
  });
});
