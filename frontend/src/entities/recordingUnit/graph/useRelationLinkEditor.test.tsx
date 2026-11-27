import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {type RelationLinkEditor, type RelationLinkEditorOptions, useRelationLinkEditor} from "./useRelationLinkEditor";

const removeStratigraphyLink = vi.fn();
const removeHierarchyLink = vi.fn();
vi.mock("../../../components/relationLinks/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../components/relationLinks/api")>()),
  removeStratigraphyLink: (...args: unknown[]) => removeStratigraphyLink(...args),
  removeHierarchyLink: (...args: unknown[]) => removeHierarchyLink(...args),
}));
// The confirmation is accepted at once: what is tested is what accepting it does.
vi.mock("primereact/confirmpopup", () => ({
  ConfirmPopup: () => null,
  confirmPopup: (options: { accept: () => Promise<void> }) => options.accept(),
}));

let container: HTMLDivElement;
let root: Root;
let latest: RelationLinkEditor;

function Probe(props: Readonly<RelationLinkEditorOptions>) {
  latest = useRelationLinkEditor(props);
  return null;
}

function mount(options: RelationLinkEditorOptions) {
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <Probe {...options} />
      </QueryClientProvider>,
    );
  });
}

const FIELDS = { anterior: "-328", synchronous: "-330", posterior: "-329" };
const ref = { resourceId: "5", resourceType: "recording-units", label: "US 5", qualifier: { linkId: 77 } };

beforeEach(() => {
  removeStratigraphyLink.mockReset().mockResolvedValue(undefined);
  removeHierarchyLink.mockReset().mockResolvedValue(undefined);
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("useRelationLinkEditor", () => {
  it("removes a stratigraphic link by the field of its zone and the link's id", async () => {
    const onChanged = vi.fn();
    mount({ unitId: 42, fields: FIELDS, onChanged });

    await act(async () => latest.remove("posterior", ref, document.body));

    expect(removeStratigraphyLink).toHaveBeenCalledWith("-329", "77");
    expect(onChanged).toHaveBeenCalled();
    expect(latest.failure).toBeNull();
  });

  it("refuses a stratigraphic removal when the graph gave no field for the zone", async () => {
    mount({ unitId: 42 });

    await act(async () => latest.remove("anterior", ref, document.body));

    expect(removeStratigraphyLink).not.toHaveBeenCalled();
    expect(latest.failure).toBeTruthy();
  });

  it("removes a hierarchy link by the unit", async () => {
    mount({ unitId: 42, fields: FIELDS });

    await act(async () => latest.remove("parents", ref, document.body));

    expect(removeHierarchyLink).toHaveBeenCalledWith(42, "parents", "5");
  });
});
