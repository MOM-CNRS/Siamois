import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { MOUNT_CONTRACT_VERSION } from "./mountOptions";

// mount.ts installs window.SiamoisMainPanel as a side effect of being imported.
beforeAll(async () => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  await import("./mount");
});

const options = {
  panelKind: "home" as const,
  entityType: "project",
  basePath: "",
  csrf: { headerName: "X", token: "t" },
  main: { resourceUri: "/welcome", title: "Accueil" },
};

function mountDiv(): HTMLDivElement {
  const div = document.createElement("div");
  document.body.appendChild(div);
  return div;
}

afterEach(() => {
  document.body.innerHTML = "";
  vi.restoreAllMocks();
});

describe("SiamoisMainPanel", () => {
  it("unmounts the roots whose container JSF removed from the page, when the next one mounts", () => {
    const first = mountDiv();
    const second = mountDiv();
    act(() => window.SiamoisMainPanel.mount(first, options));
    act(() => window.SiamoisMainPanel.mount(second, options));
    expect(first.childNodes.length).toBeGreaterThan(0);

    // JSF re-renders the flow: the mount div is replaced, nobody calls unmount.
    const stale = first.childNodes.length;
    first.remove();
    const replacement = mountDiv();
    act(() => window.SiamoisMainPanel.mount(replacement, options));

    // The detached root was unmounted: its tree is gone from the (detached) container.
    expect(stale).toBeGreaterThan(0);
    expect(first.childNodes.length).toBe(0);
    // The others are untouched.
    expect(second.childNodes.length).toBeGreaterThan(0);
    expect(replacement.childNodes.length).toBeGreaterThan(0);
  });

  it("keeps a single root when the same container is mounted twice", () => {
    const div = mountDiv();
    act(() => window.SiamoisMainPanel.mount(div, options));
    act(() => window.SiamoisMainPanel.mount(div, options));
    // A second createRoot on the same container would warn and stack a second tree.
    expect(div.querySelectorAll(".siamois-panel").length).toBe(1);
  });

  it("mounts from the container's own data-* attributes", () => {
    const div = mountDiv();
    Object.assign(div.dataset, {
      contractVersion: String(MOUNT_CONTRACT_VERSION),
      panelKind: "home",
      entityType: "project",
      basePath: "",
      csrfHeader: "X",
      csrfToken: "t",
      mainResourceUri: "/welcome",
      mainTitle: "Accueil",
    });
    act(() => window.SiamoisMainPanel.mountFromDataset(div));
    expect(div.childNodes.length).toBeGreaterThan(0);
  });

  it("says so instead of mounting when the page speaks another contract", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const div = mountDiv();
    div.dataset.panelKind = "home";
    window.SiamoisMainPanel.mountFromDataset(div);
    expect(div.textContent).toContain("mount contract");
    expect(error).toHaveBeenCalled();
  });
});
