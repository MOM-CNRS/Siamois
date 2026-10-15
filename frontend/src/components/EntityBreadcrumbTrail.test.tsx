import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { EntityBreadcrumbTrail } from "./EntityBreadcrumbTrail";
import type { EntityBreadcrumbs } from "../entities/types";
import { registerEntityType } from "../entities/registry";

let container: HTMLDivElement;
let root: Root;

const home = { icon: "bi bi-house", url: "/focus/home" };
const fallback = [{ label: "Tous les projets" }];

const single: EntityBreadcrumbs = {
  truncated: false,
  paths: [
    [
      { type: "project", id: "1", label: "P1" },
      { type: "recordingUnit", id: "5", label: "UE5" },
      { type: "find", id: "9", label: "M9" },
    ],
  ],
};

function render(breadcrumbs: EntityBreadcrumbs | undefined, onNavigate = vi.fn()) {
  act(() => {
    root.render(
      <EntityBreadcrumbTrail home={home} fallback={fallback} breadcrumbs={breadcrumbs} onNavigate={onNavigate} />,
    );
  });
  return onNavigate;
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

describe("EntityBreadcrumbTrail", () => {
  it("falls back to the home + list crumbs without breadcrumbs", () => {
    render(undefined);

    expect(container.textContent).toContain("Tous les projets");
  });

  it("shows the first path without the current fiche and navigates from every crumb", () => {
    const onNavigate = render(single);

    const labels = Array.from(container.querySelectorAll(".p-menuitem-text")).map((e) => e.textContent);
    expect(labels).toEqual(["P1", "UE5"]);

    // links[0] is the home crumb
    const links = container.querySelectorAll("a.p-menuitem-link");
    act(() => (links[2] as HTMLElement).click());
    expect(onNavigate).toHaveBeenCalledWith("recordingUnit", "5");
  });

  it("puts the entity type's icon in front of each crumb", () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    registerEntityType({ key: "project", icon: "bi bi-arrow-down-square" } as any);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    registerEntityType({ key: "recordingUnit", icon: "bi bi-pencil-square" } as any);
    render(single);

    const icons = Array.from(container.querySelectorAll("a.p-menuitem-link .p-menuitem-icon")).map((e) => e.className);
    expect(icons[1]).toContain("bi-arrow-down-square");
    expect(icons[2]).toContain("bi-pencil-square");
  });

  it("truncates a long label with an ellipsis and keeps the full text as tooltip", () => {
    const longLabel = "Fouille programmée de la vallée du Rhône";
    render({ truncated: false, paths: [[{ type: "project", id: "1", label: longLabel }, { type: "find", id: "9", label: "M9" }]] });

    const text = container.querySelectorAll(".p-menuitem-text")[0].textContent ?? "";
    expect(text.endsWith("…")).toBe(true);
    expect(text.length).toBeLessThanOrEqual(24);
    expect(container.querySelector(`a[title="${longLabel}"]`)).not.toBeNull();
  });

  it("offers the all-paths button only when there are several paths", () => {
    render(single);
    expect(container.querySelector(".panel-bc-paths-button")).toBeNull();

    render({ truncated: false, paths: [single.paths[0], [{ type: "project", id: "1", label: "P1" }, { type: "find", id: "9", label: "M9" }]] });
    expect(container.querySelector(".panel-bc-paths-button")).not.toBeNull();
  });
});
