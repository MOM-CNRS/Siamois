import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {EntityNavigationProvider} from "../../panels/pane/entityNavigation";
import {RelationFieldEditor, type RelationFieldEditorProps} from "./RelationFieldEditor";

const apiFetch = vi.fn();
vi.mock("../../api/client", () => ({ apiFetch: (...args: unknown[]) => apiFetch(...args) }));

let container: HTMLDivElement;
let root: Root;
let accepts: { resourceType: string }[];
let singleChoice: boolean;
let values: unknown[];

// Two rounds: the field's description is read first, then its values.
async function flush() {
  for (let round = 0; round < 2; round++) {
    await act(async () => {
      for (let i = 0; i < 6; i++) await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

beforeEach(() => {
  accepts = [{ resourceType: "finds" }];
  singleChoice = false;
  values = [{ resourceId: "5", resourceType: "finds", label: "MOB 5", qualifier: { linkId: 77 } }];
  apiFetch.mockReset().mockImplementation(async (url: string) => {
    if (url.startsWith("/api/v1/relation-fields/")) return { field: 152, side: "source", mirrorField: 153, singleChoice, accepts, qualifiers: [], noCycle: false };
    if (url.includes("/values")) return { data: values };
    if (url.includes("/projects/7/finds")) return { data: [{ id: 8, fullIdentifier: "MOB 8" }] };
    if (url.includes("/projects/7/phases")) return { data: [{ id: 3, identifier: "Phase 3" }] };
    return undefined;
  });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

function render(props: Partial<RelationFieldEditorProps> = {}, openEntity = vi.fn()) {
  const onChanged = vi.fn();
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <EntityNavigationProvider value={openEntity}>
          <RelationFieldEditor fieldId="152" entityType="recordingUnit" recordId="42" projectId="7" organizationId={1} onChanged={onChanged} onClose={vi.fn()} {...props} />
        </EntityNavigationProvider>
      </QueryClientProvider>,
    );
  });
  return { onChanged, openEntity };
}

async function pickFirst(query = "") {
  await act(async () => {
    const input = container.querySelector(".sia-inline-picker input") as HTMLInputElement;
    input.focus();
    if (query) input.value = query;
  });
  await flush();
}

describe("RelationFieldEditor", () => {
  it("reads what the field links in the project, then shows its links as chips opening their fiche", async () => {
    const { openEntity } = render();
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/relation-fields/152?projectId=7");
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42/fields/152/values?offset=0&limit=200");
    expect(container.querySelector(".ref-chip")!.textContent).toContain("MOB 5");
    await act(async () => {
      (container.querySelector("[role=link]") as HTMLElement).click();
    });
    expect(openEntity).toHaveBeenCalledWith("find", "5");
  });

  it("links a record of the accepted kind through the field", async () => {
    const { onChanged } = render();
    await flush();
    await pickFirst();

    const options = [...container.querySelectorAll(".sia-inline-picker-item")].map((el) => el.textContent);
    expect(options).toEqual(["MOB 8"]);
    await act(async () => {
      (container.querySelector(".sia-inline-picker-item") as HTMLElement).click();
    });
    await act(async () => {
      (container.querySelector("button[aria-label='Confirmer']") as HTMLElement).click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/relation-links", {
      method: "POST",
      body: {
        field: 152,
        record: { resourceType: "recording-units", resourceId: 42 },
        other: { resourceType: "finds", resourceId: 8 },
        qualifier: null,
        uncertain: false,
      },
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it("names the kind of each option when the field links several", async () => {
    accepts = [{ resourceType: "finds" }, { resourceType: "phases" }];
    render();
    await flush();
    await pickFirst();

    const options = [...container.querySelectorAll(".sia-inline-picker-item")].map((el) => el.textContent);
    expect(options).toEqual(["MOB 8 · mobilier", "Phase 3 · phase"]);
    await act(async () => {
      (container.querySelectorAll(".sia-inline-picker-item")[1] as HTMLElement).click();
    });
    await act(async () => {
      (container.querySelector("button[aria-label='Confirmer']") as HTMLElement).click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/relation-links", expect.objectContaining({
      body: expect.objectContaining({ other: { resourceType: "phases", resourceId: 3 } }),
    }));
  });

  it("offers no picker once a single-choice field has its link", async () => {
    singleChoice = true;
    render();
    await flush();

    expect(container.querySelector(".ref-chip")).not.toBeNull();
    expect(container.querySelector(".sia-inline-picker")).toBeNull();
  });

  it("links any record of the project when the field accepts no particular kind", async () => {
    accepts = [];
    values = [];
    render();
    await flush();
    await pickFirst();

    expect(apiFetch).toHaveBeenCalledWith(expect.stringContaining("/api/v1/projects/7/documents"));
    expect(apiFetch).not.toHaveBeenCalledWith(expect.stringContaining("/api/v1/projects/7/projects"));
  });

  it("shows nothing outside a project or for a record of an unknown kind", async () => {
    render({ projectId: null });
    await flush();
    expect(container.innerHTML).toBe("");

    render({ entityType: "place" });
    await flush();
    expect(container.querySelector(".ref-chip")).toBeNull();
  });
});
