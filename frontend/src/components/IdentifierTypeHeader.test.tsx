import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { IdentifierTypeHeader } from "./IdentifierTypeHeader";

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

describe("IdentifierTypeHeader", () => {
  it("shows the identifier chip and the type chip with the entity's JSF classes", () => {
    act(() => root.render(<IdentifierTypeHeader entityType="find" chipPrefix="specimen" label="OA-M12" typeLabel="Céramique" />));

    expect(container.querySelector(".specimen-chip-alt")?.textContent).toBe("OA-M12");
    expect(container.querySelector(".specimen-type-chip")?.textContent).toBe("Céramique");
  });

  it("shows no type chip for an untyped entity", () => {
    act(() => root.render(<IdentifierTypeHeader entityType="phase" chipPrefix="phase" label="PH1" typeLabel={null} />));

    expect(container.querySelector(".phase-type-chip")).toBeNull();
  });
});
