import { afterEach, describe, expect, it, vi } from "vitest";
import { paneTransitionName, withPanelTransition } from "./panelTransition";

type StartViewTransition = (update: () => void) => { finished: Promise<void> };

afterEach(() => {
  delete (document as { startViewTransition?: StartViewTransition }).startViewTransition;
  delete document.documentElement.dataset.panelTransition;
});

describe("withPanelTransition", () => {
  it("just runs the update when the browser has no View Transitions", () => {
    const update = vi.fn();
    withPanelTransition("overview-open", update);
    expect(update).toHaveBeenCalledOnce();
    expect(document.documentElement.dataset.panelTransition).toBeUndefined();
  });

  it("runs the update inside a view transition, tagging the root with its kind until it finishes", async () => {
    let finish!: () => void;
    const finished = new Promise<void>((resolve) => (finish = resolve));
    const start = vi.fn<StartViewTransition>((cb) => {
      cb();
      return { finished };
    });
    (document as { startViewTransition?: StartViewTransition }).startViewTransition = start;
    const update = vi.fn();

    withPanelTransition("focus-enter", update);

    expect(start).toHaveBeenCalledOnce();
    expect(update).toHaveBeenCalledOnce();
    expect(document.documentElement.dataset.panelTransition).toBe("focus-enter");
    finish();
    await finished;
    await Promise.resolve();
    expect(document.documentElement.dataset.panelTransition).toBeUndefined();
  });
});

describe("paneTransitionName", () => {
  it("is stable per entity and a valid CSS identifier", () => {
    expect(paneTransitionName("detail", "recordingUnit", 94)).toBe("pane-detail-recordingUnit-94");
    expect(paneTransitionName("list", "project")).toBe("pane-list-project-all");
    expect(paneTransitionName("detail", "place", "a:b/c")).toBe("pane-detail-place-a_b_c");
  });
});
