import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {WriteModeProvider} from "../../panels/pane/writeMode";
import {queryKeys} from "../../api/queryKeys";
import {BulkImportDocument} from "./BulkImportDocument";

vi.mock("primereact/button", () => ({
  Button: ({ label, onClick }: { label: string; onClick: () => void }) => (
    <button type="button" data-testid="bulk-import" onClick={onClick}>
      {label}
    </button>
  ),
}));

let container: HTMLDivElement;
let root: Root;
let queryClient: QueryClient;

function render(props: Partial<Parameters<typeof BulkImportDocument>[0]> = {}, writeMode = true) {
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <BulkImportDocument {...props} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
}

beforeEach(() => {
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  delete (window as unknown as { siaOpenBulkDocumentImport?: unknown }).siaOpenBulkDocumentImport;
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("BulkImportDocument", () => {
  it("is not offered in read mode", () => {
    render({}, false);
    expect(container.textContent).toBe("");
  });

  it("opens the dialog with the fiche as target when segment and id are set", () => {
    const open = vi.fn();
    (window as unknown as { siaOpenBulkDocumentImport: typeof open }).siaOpenBulkDocumentImport = open;
    render({ segment: "projects", entityId: 12 });

    act(() => {
      container.querySelector<HTMLButtonElement>("[data-testid='bulk-import']")!.click();
    });

    expect(open).toHaveBeenCalledWith({ targetType: "projects", targetId: "12" });
  });

  it("opens the dialog empty from the organization-wide list", () => {
    const open = vi.fn();
    (window as unknown as { siaOpenBulkDocumentImport: typeof open }).siaOpenBulkDocumentImport = open;
    render();

    act(() => {
      container.querySelector<HTMLButtonElement>("[data-testid='bulk-import']")!.click();
    });

    expect(open).toHaveBeenCalledWith({});
  });

  it("warns when the remote command is missing", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    render();

    act(() => {
      container.querySelector<HTMLButtonElement>("[data-testid='bulk-import']")!.click();
    });

    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it("invalidates document lists and open fiches when the dialog reports success", async () => {
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    render();

    await act(async () => {
      window.dispatchEvent(new Event("sia-bulk-documents-done"));
      await Promise.resolve();
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.entityList("document") });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.entityDetails() });
  });
});
