import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { WriteModeProvider } from "../../panels/writeMode";
import { AssociateDocument } from "./AssociateDocument";
import { linkDocument, listDocuments } from "./api";

vi.mock("./api", () => ({ linkDocument: vi.fn(), listDocuments: vi.fn() }));
// The picker itself is PrimeReact's: its search and its pick are what this component wires.
vi.mock("primereact/autocomplete", () => ({
  AutoComplete: ({ completeMethod, onSelect }: { completeMethod: (e: { query: string }) => void; onSelect: (e: { value: unknown }) => void }) => (
    <div>
      <button type="button" data-testid="search" onClick={() => completeMethod({ query: "pl" })} />
      <button type="button" data-testid="pick" onClick={() => onSelect({ value: { id: "11", identifier: "DOC0001" } })} />
    </div>
  ),
}));
vi.mock("primereact/overlaypanel", async () => {
  const React = await import("react");
  return {
    OverlayPanel: React.forwardRef(function OverlayPanel(
      { children }: { children: React.ReactNode },
      ref: React.ForwardedRef<{ toggle: () => void; hide: () => void }>,
    ) {
      React.useImperativeHandle(ref, () => ({ toggle: () => {}, hide: () => {} }));
      return <div>{children}</div>;
    }),
  };
});

const mockedList = vi.mocked(listDocuments);
const mockedLink = vi.mocked(linkDocument);

let container: HTMLDivElement;
let root: Root;
let queryClient: QueryClient;

function render(props: Partial<Parameters<typeof AssociateDocument>[0]> = {}, writeMode = true) {
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <AssociateDocument segment="phases" entityId="5" organizationId={42} projectId="7" {...props} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
}

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });
const click = (testId: string) => act(async () => { container.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!.click(); });

beforeEach(() => {
  vi.resetAllMocks();
  mockedList.mockResolvedValue({ data: [], totalCount: 0, limit: 10, offset: 0 });
  mockedLink.mockResolvedValue(undefined);
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("AssociateDocument", () => {
  it("is not offered in read mode", () => {
    render({}, false);

    expect(container.textContent).toBe("");
  });

  it("searches the project's documents", async () => {
    render();

    await click("search");

    expect(mockedList).toHaveBeenCalledWith({ offset: 0, limit: 10, search: "pl", organizationId: 42, scope: { entityType: "project", id: "7" } });
  });

  it("searches the organization's documents for a place, which has no project", async () => {
    render({ projectId: null });

    await click("search");

    expect(mockedList).toHaveBeenCalledWith(expect.objectContaining({ scope: undefined, organizationId: 42 }));
  });

  it("links the picked document to the entity and refreshes the tab", async () => {
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    render();

    await click("pick");
    await flush();

    expect(mockedLink).toHaveBeenCalledWith("phases", "5", "11");
    expect(invalidate).toHaveBeenCalled();
  });

  it("keeps the tab as it was when the link fails", async () => {
    mockedLink.mockRejectedValue(new Error("400"));
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    render();

    await click("pick");
    await flush();

    expect(invalidate).not.toHaveBeenCalled();
  });
});
