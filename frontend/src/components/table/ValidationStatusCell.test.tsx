import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { WriteModeProvider } from "../../panels/writeMode";
import { ValidationStatusCell, type ValidationStatusCellProps } from "./ValidationStatusCell";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));

let container: HTMLDivElement;
let root: Root;

function render(writeMode: boolean, row: ValidationStatusCellProps["row"]) {
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <WriteModeProvider value={writeMode}>
          <ValidationStatusCell entityType="project" collectionPath="projects" row={row} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
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

describe("ValidationStatusCell", () => {
  it("is the status picker in write mode for a row the user may edit", () => {
    render(true, { id: 1, validated: "COMPLETE", _permissions: { canEdit: true } });
    const button = container.querySelector("button.status-button");
    expect(button).not.toBeNull();
    expect(button!.classList.contains("status-button-compact")).toBe(true);
    expect(container.querySelector(".validation-status-badge")).toBeNull();
  });

  it("is the read-only badge out of write mode", () => {
    render(false, { id: 1, validated: "COMPLETE", _permissions: { canEdit: true, canValidate: true } });
    expect(container.querySelector("button")).toBeNull();
    expect(container.querySelector(".validation-status-badge.complete")).not.toBeNull();
  });

  it("shows each state's own icon, and falls back to en cours when the row has none", () => {
    render(false, { id: 1, validated: "VALIDATED" });
    expect(container.querySelector(".validation-status-badge.bi-check-circle")).not.toBeNull();
    render(false, { id: 1, validated: "CANCELLED" });
    expect(container.querySelector(".validation-status-badge.cancelled")).not.toBeNull();
    render(false, { id: 1, validated: null });
    expect(container.querySelector(".validation-status-badge.incomplete")).not.toBeNull();
  });

  it("is the read-only badge for a row the user has no right on", () => {
    render(true, { id: 1, validated: "VALIDATED", _permissions: { canEdit: false } });
    expect(container.querySelector("button")).toBeNull();
    expect(container.querySelector(".validation-status-badge.validated")).not.toBeNull();
  });
});
