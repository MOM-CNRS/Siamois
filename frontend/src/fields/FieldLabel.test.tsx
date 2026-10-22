import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { FieldLabel } from "./FieldLabel";
import { FieldConfigProvider } from "./fieldConfigContext";
import type { FieldResource } from "./types";

function field(overrides: Partial<FieldResource> = {}): FieldResource {
  return { id: "34", resourceType: "fields", label: "État sanitaire", answerType: "TEXT", isSystemField: false, ...overrides };
}

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
  document.body.querySelectorAll(".p-menu").forEach((menu) => menu.remove());
});

async function openMenu() {
  await act(async () => {
    container.querySelector("button")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

function menuLabels(): string[] {
  return [...document.body.querySelectorAll(".p-menuitem-text")].map((item) => item.textContent ?? "");
}

describe("FieldLabel menu", () => {
  it("offers nothing to open on a field with no thesaurus entry, for a viewer who may not configure", () => {
    act(() => root.render(<FieldLabel field={field()} required={false} />));

    expect(container.querySelector("button")!.getAttribute("aria-haspopup")).toBeNull();
    expect(document.body.querySelector(".p-menu")).toBeNull();
  });

  it("offers the thesaurus entry of a vocabulary field", async () => {
    act(() => root.render(<FieldLabel field={field({ conceptUri: "https://thesaurus.example.org/?idc=1" })} required={false} />));
    await openMenu();

    expect(container.querySelector("button")!.getAttribute("aria-haspopup")).toBe("menu");
    expect(menuLabels()).toHaveLength(1);
  });

  it("offers to configure the field, before the thesaurus entry, to whoever may configure the project", async () => {
    const configure = vi.fn();
    const label = field({ conceptUri: "https://thesaurus.example.org/?idc=1" });
    act(() =>
      root.render(
        <FieldConfigProvider value={{ configure }}>
          <FieldLabel field={label} required />
        </FieldConfigProvider>,
      ),
    );
    await openMenu();

    expect(menuLabels()).toHaveLength(2);
    await act(async () => {
      (document.body.querySelector(".p-menuitem-link") as HTMLElement).click();
    });
    expect(configure).toHaveBeenCalledWith(label);
  });

  it("offers only the configuration on a field with no thesaurus entry", async () => {
    act(() =>
      root.render(
        <FieldConfigProvider value={{ configure: vi.fn() }}>
          <FieldLabel field={field()} required={false} />
        </FieldConfigProvider>,
      ),
    );
    await openMenu();

    expect(menuLabels()).toHaveLength(1);
  });
});
